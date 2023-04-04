void call() {
    if (env.workDir) {
        // Retrieve data to define required variables
        String centralVaultToken = sh(script: "set +x; oc get secret central-vault-token -o jsonpath={.data.token} " +
                "-n $NAMESPACE | base64 --decode", returnStdout: true)
        String registryValuesPath = "deploy-templates/values.yaml"
        String trembitaRegistriesSecretName = "trembita-registries"
        String externalSystemsSecretName = "external-systems"
        String vaultSecretsPath = "http://hashicorp-vault.user-management.svc.cluster.local:8200/v1/registry-kv/data/registry/$NAMESPACE"
        LinkedHashMap registryValues = readYaml file: "$workDir/$registryValuesPath"
        String cpGerritSecretName = "gerrit-ciuser-password"
        String gerritUser = sh(script: "set +x; oc get secret $cpGerritSecretName -o jsonpath={.data.user} " +
                "| base64 --decode", returnStdout: true)
        String gerritPass = sh(script: "set +x; oc get secret $cpGerritSecretName -o jsonpath={.data.password} " +
                "| base64 --decode", returnStdout: true)
        String cpGerritHost = "gerrit:8080"
        String registryRepoUrl = "http://$gerritUser:$gerritPass@$cpGerritHost/$NAMESPACE"
        // Recreate secrets in vault and put new secret into registry values map for trembita registries
        registryValues.trembita.registries.each { trembitaRegistry, tv ->
            if (tv.auth?.type && tv.auth?.type != "NO_AUTH") {
                recreateVaultSecret(trembitaRegistriesSecretName, trembitaRegistry, tv.auth.type, vaultSecretsPath,
                        centralVaultToken, "trembita.registries")
                tv.auth.put("secret", "${tv.auth.secret}/$trembitaRegistry")
            }
        }
        // Recreate secrets in vault and put new secret into registry values map for external-systems
        registryValues["external-systems"].each { externalSystem, ev ->
            if (ev.auth?.type && ev.auth?.type != "NO_AUTH") {
                recreateVaultSecret(externalSystemsSecretName, externalSystem, ev.auth.type, vaultSecretsPath,
                        centralVaultToken, "external-systems")
                ev.auth.put("secret", "${ev.auth.secret}/$externalSystem")
            }
        }
        // Save new registry values in values.yaml
        writeYaml file: "$workDir/$registryValuesPath", data: registryValues, overwrite: true
        // Push new registry values to gerrit repo
        sh(script: "set +x; cd $workDir " +
                "&& git config user.name \"$gerritUser\" " +
                "&& git config user.email \"jenkins@example.com\" " +
                "&& git config user.password \"$gerritPass\" " +
                "&& git checkout master " +
                "&& git remote add update $registryRepoUrl" +
                "&& git add $registryValuesPath && git commit -m 'Update external systems secrets' " +
                "&& git push -u update master")
        // Generate externalsecrets CRs using new registry values and replace existing
        sh(script: "cd ../../../ && rm deploy-templates/templates/SmtpInternalServerUser.yaml " +
                "&& helm template deploy-templates -s templates/trembita-registries-secrets-external-secret.yaml " +
                "--values $workDir/$registryValuesPath --set registryRegulations.registryRegulationsRepoVersion=mock " +
                "--set namespace=$NAMESPACE > template-trembita-registries-secrets-external-secret.yaml " +
                "&& helm template deploy-templates/ -s templates/external-systems-external-secrets.yaml " +
                "--values $workDir/$registryValuesPath --set registryRegulations.registryRegulationsRepoVersion=mock " +
                "--set namespace=$NAMESPACE > template-external-systems-external-secrets.yaml " +
                "&& helm template deploy-templates/ -s templates/diia-external-secret.yaml " +
                "--values $workDir/$registryValuesPath --set registryRegulations.registryRegulationsRepoVersion=mock " +
                "--set namespace=$NAMESPACE > template-diia-external-secret.yaml " +
                "&& oc replace -f template-trembita-registries-secrets-external-secret.yaml --force -n $NAMESPACE " +
                "&& oc annotate --overwrite externalsecret trembita-registries-external-secrets meta.helm.sh/release-name=registry-configuration -n $NAMESPACE " +
                "&& oc replace -f template-external-systems-external-secrets.yaml --force -n $NAMESPACE " +
                "&& oc annotate --overwrite externalsecret external-systems-external-secrets meta.helm.sh/release-name=registry-configuration -n $NAMESPACE " +
                "&& oc replace -f template-diia-external-secret.yaml --force -n $NAMESPACE " +
                "&& oc annotate --overwrite externalsecret diia-external-secret meta.helm.sh/release-name=registry-configuration -n $NAMESPACE")
    }
}

void recreateVaultSecret(String oldSecretName, String newSecretName, String authType, String vaultSecretsPath, String centralVaultToken, String tokenPrefix) {
    if (authType == "BASIC") {
        String basicAuthUser = sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                "--request GET $vaultSecretsPath/$oldSecretName | jq '.data.data.\"${tokenPrefix}.${newSecretName}.auth.secret.username\"'", returnStdout: true)
        String basicAuthPass = sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                "--request GET $vaultSecretsPath/$oldSecretName | jq '.data.data.\"${tokenPrefix}.${newSecretName}.auth.secret.password\"'", returnStdout: true)
        sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                "--request POST $vaultSecretsPath/$oldSecretName/$newSecretName " +
                "--data '{\"data\": {\"${tokenPrefix}.${newSecretName}.auth.secret.username\": $basicAuthUser, " +
                "\"${tokenPrefix}.${newSecretName}.auth.secret.password\": $basicAuthPass}}'")
    } else {
        String existingToken = sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                "--request GET $vaultSecretsPath/$oldSecretName | jq '.data.data.\"${tokenPrefix}.${newSecretName}.auth.secret.token\"'", returnStdout: true)
        sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                "--request POST $vaultSecretsPath/$oldSecretName/$newSecretName " +
                "--data '{\"data\": {\"${tokenPrefix}.${newSecretName}.auth.secret.token\": $existingToken}}'")
    }
}

return this;
