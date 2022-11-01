void call() {
    String revision = sh(script: "helm status registry-configuration -n $NAMESPACE | grep REVISION | sed 's/^.*://'", returnStdout: true).trim()
    if (revision.toInteger() > 1) {
        String secretName = "trembita-registries-secrets"
        String gerritSecretName = "gerrit-ciuser-password"
        String registryRepoName = "registry-regulations"
        String gerritUser = sh(script: "oc get secret $gerritSecretName -o jsonpath={.data.user} " +
                "-n $NAMESPACE | base64 --decode", returnStdout: true)
        String gerritPass = sh(script: "oc get secret $gerritSecretName -o jsonpath={.data.password} " +
                "-n $NAMESPACE | base64 --decode", returnStdout: true)
        String gerritHost = sh(script: "oc get route gerrit -o jsonpath={.spec.host} -n $NAMESPACE", returnStdout: true)
        String repoUrl = "https://$gerritUser:$gerritPass@$gerritHost/$registryRepoName"
        String isRepoExists = sh(script: "set +x; git ls-remote $repoUrl > /dev/null", returnStatus: true)
        if (isRepoExists == '0') {
            sh(script: "set +x; git clone $repoUrl")
            def configFile = readYaml file: "$registryRepoName/bp-trembita/configuration.yml"
            def edrRegistry = configFile["trembita-exchange-gateway"]["registries"]["edr-registry"]
            if (edrRegistry["authorization-token"] != null) {
                String token = configFile["trembita-exchange-gateway"]["registries"]["edr-registry"]["authorization-token"].bytes.encodeBase64().toString()
                sh(script: "set +x; oc patch secret $secretName --type merge -p '{\"data\": {\"trembita.registries.edr-registry.auth.secret.token\": \"$token\"}}' -n $NAMESPACE")
                edrRegistry.put("secret-name", secretName)
                edrRegistry.remove("authorization-token")
                sh(script: "rm $registryRepoName/bp-trembita/configuration.yml")
                writeYaml file: "$registryRepoName/bp-trembita/configuration.yml", data: configFile
                sh(script: "set +x; cd $registryRepoName " +
                        "&& git config user.name \"$gerritUser\" " +
                        "&& git config user.email \"jenkins@example.com\" " +
                        "&& git config user.password \"$gerritPass\" " +
                        "&& git add . && git commit -m 'Move token to secret' " +
                        "&& git push origin master")
                sh(script: "oc scale deployment/bpms --replicas=0 -n $NAMESPACE || :")
                sh(script: "oc scale deployment/bpms --replicas=1 -n $NAMESPACE || :")
            }
        }
    }
}

return this;