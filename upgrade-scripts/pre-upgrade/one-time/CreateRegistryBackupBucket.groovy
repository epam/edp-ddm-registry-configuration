void call() {
    String minioSecretNamespace = "${env.dnsWildcard}".contains('cicd') ?
            'mdtu-ddm-edp-cicd' : 'control-plane'
    String minioAccessKeyId = sh(script: "oc -n $minioSecretNamespace get secret backup-credentials -o jsonpath={.data.backup-s3-like-storage-access-key-id} " +
            "| base64 --decode", returnStdout: true)
    String minioSecretAccessKey = sh(script: "oc -n $minioSecretNamespace get secret backup-credentials -o jsonpath={.data.backup-s3-like-storage-secret-access-key} " +
            " | base64 --decode", returnStdout: true)
    String minioEndpoint = sh(script: "oc -n $minioSecretNamespace get secret backup-credentials -o jsonpath={.data.backup-s3-like-storage-url} " +
            " | base64 --decode", returnStdout: true)
    String kesServerIp=sh(script: "oc get endpointslice platform-minio -o jsonpath='{.endpoints[0].addresses[0]}'", returnStdout: true)
    String kesApiKey=sh(script: "oc get secret platform-minio-key-api-key -o jsonpath={.data.apiKey} | base64 --decode", returnStdout: true)
    String backupBucketName="${NAMESPACE}-backups"
    String bucketEncryptionKey="minio-${backupBucketName}"
    String backupNamespace="velero"
    String jobName="create-minio-bucket-${NAMESPACE}"
    // Wait for the job to complete
    int timeout = 120 // timeout in seconds
    int interval = 10 // interval between checks in seconds
    int elapsed = 0
    boolean jobCompleted = false

    sh """set +x; oc apply -f - << EOF
apiVersion: batch/v1
kind: Job
metadata:
  name: $jobName
  namespace: $NAMESPACE
spec:
  template:
    spec:
      initContainers:
      - name: create-bucket-encryption-key
        image: minio/kes
        env:
        - name: KES_SERVER
          value: https://$kesServerIp:7373
        - name: KES_API_KEY
          value: $kesApiKey
        command:
        - /bin/sh
        - -c
        - |
          ./kes key create $bucketEncryptionKey -k || echo "Encryption key already exists."
      containers:
        - name: create-bucket
          image: minio/mc
          env:
            - name: MC_CONFIG_DIR
              value: /tmp/.mc
          command:
            - /bin/sh
            - -c
            - |
              mc alias set default $minioEndpoint $minioAccessKeyId $minioSecretAccessKey
              if ! mc ls default/$backupBucketName > /dev/null 2>&1; then
                mc mb default/$backupBucketName
                mc encrypt set sse-kms $bucketEncryptionKey default/$backupBucketName
              else
                echo "Bucket 'default/$backupBucketName' already exists. Skipping creation."
              fi
      restartPolicy: Never
  backoffLimit: 4
---
apiVersion: velero.io/v1
kind: BackupStorageLocation
metadata:
  annotations:
    helm.sh/hook: 'post-install,post-upgrade,post-rollback'
    helm.sh/hook-delete-policy: before-hook-creation
  name: $NAMESPACE
  namespace: $backupNamespace
spec:
  accessMode: ReadWrite
  config:
    publicUrl: $minioEndpoint
    region: eu-central-1
    s3ForcePathStyle: 'true'
    s3Url: $minioEndpoint
  credential:
    key: cloud
    name: $backupBucketName
  default: false
  objectStorage:
    bucket: $backupBucketName
    prefix: openshift-backups
  provider: aws
EOF
"""

    while (elapsed < timeout) {
        String jobStatus = sh(script: "oc get job $jobName -o jsonpath={.status.succeeded} -n $NAMESPACE", returnStdout: true).trim()

        if (jobStatus == "1") {
            jobCompleted = true
            echo "Job $jobName completed successfully."
            break
        }

        sleep(interval)
        elapsed += interval
    }

    // Handle timeout
    if (!jobCompleted) {
        error "Job $jobName did not complete within the timeout of ${timeout} seconds."
    }

    // Delete the job
    sh "oc delete job $jobName -n $NAMESPACE"
}

return this;
