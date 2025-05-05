void call() {
    GString backupBucketName = "${NAMESPACE}-backups"
    GString s3PolicyName = backupBucketName
    GString s3Username = backupBucketName
    String s3Password = sh(script: "set +x; head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c16", returnStdout: true)
    String pgS3SecretName = "s3-conf"
    GString pgS3SecretData = """[global]
repo1-s3-key=${s3Username}
repo1-s3-key-secret=${s3Password}"""
    GString veleroS3SecretName = backupBucketName
    GString veleroS3SecretData = """[default]
aws_access_key_id=${s3Username}
aws_secret_access_key=${s3Password}"""
    String secretS3Name = "s3-user-credentials"
    String minioSecretNamespace = "control-plane"
    String minioAccessKeyId = sh(script: "kubectl -n ${minioSecretNamespace} get secret backup-credentials -o jsonpath={.data.backup-s3-like-storage-access-key-id} " +
            "| base64 --decode", returnStdout: true)
    String minioSecretAccessKey = sh(script: "kubectl -n ${minioSecretNamespace} get secret backup-credentials -o jsonpath={.data.backup-s3-like-storage-secret-access-key} " +
            " | base64 --decode", returnStdout: true)
    String minioEndpoint = sh(script: "kubectl -n ${minioSecretNamespace} get secret backup-credentials -o jsonpath={.data.backup-s3-like-storage-url} " +
            " | base64 --decode", returnStdout: true)

    sh(script: """
        set +x
        cat <<EOF > ${s3PolicyName}.json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetBucketLocation",
        "s3:GetBucketVersioning",
        "s3:GetEncryptionConfiguration",
        "s3:ListBucket",
        "s3:ListBucketMultipartUploads",
        "s3:PutBucketVersioning",
        "s3:PutEncryptionConfiguration"
      ],
      "Resource": [
        "arn:aws:s3:::${backupBucketName}"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:AbortMultipartUpload",
        "s3:DeleteObject",
        "s3:GetObject",
        "s3:GetObjectTagging",
        "s3:GetObjectVersion",
        "s3:ListMultipartUploadParts",
        "s3:PutObject",
        "s3:PutObjectTagging"
      ],
      "Resource": [
        "arn:aws:s3:::${backupBucketName}/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:DeleteObject"
      ],
      "Resource": [
        "arn:aws:s3:::${backupBucketName}/postgres-backup/*"
      ]
    }
  ]
}
EOF
        mc alias set default ${minioEndpoint} ${minioAccessKeyId} ${minioSecretAccessKey}

        if kubectl -n ${NAMESPACE} get secret ${secretS3Name} >/dev/null 2>&1; then
            echo "Secret ${secretS3Name} already exists. Skipping create."
        else
            echo "Creating ${secretS3Name} secret..."
            kubectl -n ${NAMESPACE} create secret generic ${secretS3Name} --from-literal=username="${s3Username}" --from-literal=password="${s3Password}"
            echo "Secret ${secretS3Name} has been successfully created."
        fi

        if mc admin policy info default ${s3PolicyName} >/dev/null 2>&1; then
            echo "Policy ${s3PolicyName} already exists. Skipping add."
        else
            echo "Adding policy ${s3PolicyName}..."
            mc admin policy create default ${s3PolicyName} ${s3PolicyName}.json
        fi

        if mc admin user info default ${s3Username} >/dev/null 2>&1; then
            echo "User ${s3Username} already exists. Skipping add."
        else
            mc admin user add default ${s3Username} ${s3Password}
            mc admin policy attach default ${s3PolicyName} --user ${s3Username}
        fi

        if kubectl -n ${NAMESPACE} get secret ${pgS3SecretName} >/dev/null 2>&1; then
            echo "Secret ${pgS3SecretName} already exists. Deleting and create again..."
            kubectl -n ${NAMESPACE} delete secret ${pgS3SecretName}
            kubectl -n ${NAMESPACE} create secret generic ${pgS3SecretName} --from-literal=s3.conf="${pgS3SecretData}"
            echo "Secret ${pgS3SecretName} has been successfully created."
        else
            echo "Creating ${pgS3SecretName} secret..."
            kubectl -n ${NAMESPACE} create secret generic ${pgS3SecretName} --from-literal=s3.conf="${pgS3SecretData}"
            echo "Secret ${pgS3SecretName} has been successfully created."
        fi

        if kubectl -n velero get secret ${veleroS3SecretName} >/dev/null 2>&1; then
            echo "Secret ${veleroS3SecretName} already exists. Skipping create."
        else
            echo "Creating ${veleroS3SecretName} secret..."
            kubectl -n velero create secret generic ${veleroS3SecretName} --from-literal=cloud="${veleroS3SecretData}"
            echo "Secret ${veleroS3SecretName} has been successfully created."
        fi

        if kubectl -n velero get backupstoragelocation ${NAMESPACE} >/dev/null 2>&1; then
            kubectl -n velero patch backupstoragelocation ${NAMESPACE} --type='merge' -p '{"spec":{"credential":{"name":"${veleroS3SecretName}","key":"cloud"}}}'
        fi
    """)
}

return this;
