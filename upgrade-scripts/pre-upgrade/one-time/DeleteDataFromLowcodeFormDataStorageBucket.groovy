void call() {
        String obcName = "lowcode-form-data-storage"
        String bucketName = sh(script: "oc get configmap ${obcName}  -o jsonpath='{.data.BUCKET_NAME}' " +
                "-n ${NAMESPACE} --ignore-not-found=true", returnStdout: true)
        if(bucketName) {
                String bucketAccessKey = sh(script: "oc get secret ${obcName} -o jsonpath='{.data.AWS_ACCESS_KEY_ID}' " +
                        "-n ${NAMESPACE} | base64 --decode", returnStdout: true)
                String bucketSecretAccessKey = sh(script: "oc get secret ${obcName} -o jsonpath='{.data.AWS_SECRET_ACCESS_KEY}' " +
                        "-n ${NAMESPACE} | base64 --decode", returnStdout: true)
                String bucketEndpoint = "http://" + sh(script: "oc get configmap ${obcName} -o jsonpath='{.data.BUCKET_HOST}' " +
                        "-n ${NAMESPACE}", returnStdout: true)
                //        Configuring rclone for delete data from bucket
                sh """set +x;
                mkdir -p ~/.config/rclone;
                echo "
                [form-bucket]
                type = s3
                provider = Ceph
                env_auth = false
                access_key_id = ${bucketAccessKey}
                secret_access_key = ${bucketSecretAccessKey}
                endpoint = ${bucketEndpoint}
                acl = bucket-owner-full-control
                bucket_acl = authenticated-read" > ~/.config/rclone/rclone.conf; set -x"""
                sh "rclone delete form-bucket:${bucketName} "
        }
}

return this;
