void call() {

    LinkedHashMap checkSum = [:]
    String secretChecksum
    String configChecksum
    String getSecret
    String getConfigmap
    ["trembita-registries-secrets", "external-systems-secrets", "diia-secret"].each {
        getSecret = sh(script: "oc -n $NAMESPACE get secret $it --ignore-not-found", returnStdout: true).trim()
        if (getSecret != '') {
            secretChecksum = sh(script: "oc -n $NAMESPACE get secret $it -o jsonpath='{.data}' | sha256sum", returnStdout: true).replaceAll('\\W', '').trim()
            checkSum.put(it, secretChecksum)
        } else {
            checkSum.put(it, "NOT_EXIST")
        }
    }

    ["trembita-registries-configuration", "external-systems-configuration", "diia-configuration"].each {
        getConfigmap = sh(script: "oc -n $NAMESPACE get configmap $it --ignore-not-found", returnStdout: true).trim()
        if (getConfigmap != '') {
            configChecksum = sh(script: "oc -n $NAMESPACE get configmap $it -o jsonpath='{.data}' | sha256sum", returnStdout: true).replaceAll('\\W', '').trim()
            checkSum.put(it, configChecksum)
        } else {
            checkSum.put(it, "NOT_EXIST")
        }
        writeYaml file: "/tmp/resourceChecksum.yaml", data: checkSum, overwrite: true
    }
}

return this;
