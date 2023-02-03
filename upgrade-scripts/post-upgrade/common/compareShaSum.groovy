void call() {
    try {
        LinkedHashMap oldChecksum = readYaml file: "/tmp/resourceChecksum.yaml"
        LinkedHashMap newCheckSum = [:]
        String newSecretChecksum
        String newConfigChecksum
        String getSecret
        String getConfigmap
        ["trembita-registries-secrets", "external-systems-secrets", "diia-secret"].each {
            getSecret = sh(script: "oc -n $NAMESPACE get secret $it --ignore-not-found", returnStdout: true).trim()
            if (getSecret != '') {
                newSecretChecksum = sh(script: "oc -n $NAMESPACE get secret $it -o jsonpath='{.data}' | sha256sum", returnStdout: true).replaceAll('\\W', '').trim()
                newCheckSum.put(it, newSecretChecksum)
            } else {
                println("Secret $it does not exist")
                newCheckSum.put(it, "NOT_EXIST")
            }
        }

        ["trembita-registries-configuration", "external-systems-configuration", "diia-configuration"].each {
            getConfigmap = sh(script: "oc -n $NAMESPACE get configmap $it --ignore-not-found", returnStdout: true).trim()
            if (getConfigmap != '') {
                newConfigChecksum = sh(script: "oc -n $NAMESPACE get configmap $it -o jsonpath='{.data}' | sha256sum", returnStdout: true).replaceAll('\\W', '').trim()
                newCheckSum.put(it, newConfigChecksum)
            } else {
                println("Configmap $it does not exist")
                newCheckSum.put(it, "NOT_EXIST")
            }
        }
        if (oldChecksum["diia-secret"] != newCheckSum["diia-secret"] || oldChecksum["diia-configuration"] != newCheckSum["diia-configuration"]) {
            sh "oc scale deployment/ddm-notification-service --replicas=0 -n $NAMESPACE || :"
            sh "oc scale deployment/ddm-notification-service --replicas=1 -n $NAMESPACE || :"
        } else {
            println("There are no changes in external systems secrets and configmaps for ddm-notification-service")
        }
        if (oldChecksum["trembita-registries-secrets"] != newCheckSum["trembita-registries-secrets"] ||
                oldChecksum["external-systems-secrets"] != newCheckSum["external-systems-secrets"] ||
                oldChecksum["trembita-registries-configuration"] != newCheckSum["trembita-registries-configuration"] ||
                oldChecksum["external-systems-configuration"] != newCheckSum["external-systems-configuration"] ||
                oldChecksum["diia-configuration"] != newCheckSum["diia-configuration"]) {
            sh "oc scale deployment/bpms --replicas=0 -n $NAMESPACE || :"
            sh "oc scale deployment/bpms --replicas=1 -n $NAMESPACE || :"
        } else {
            println("There are no changes in external systems secrets and configmaps for bpms")
        }
    } catch (any) {
        println("Failed to compare external systems resources checksum")
    }
}

return this;
