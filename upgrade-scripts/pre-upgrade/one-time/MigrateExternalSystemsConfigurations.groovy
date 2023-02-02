void call() {
    String centralVaultToken = sh(script: "oc get secret central-vault-token -o jsonpath={.data.token} " +
            "-n $NAMESPACE | base64 --decode", returnStdout: true)
    String registryGerritSecretName = "gerrit-ciuser-password"
    String registryRegulationRepoName = "registry-regulations"
    String regulationConfigurationFilePath = "$registryRegulationRepoName/bp-trembita/configuration.yml"
    String registryGerritUser = sh(script: "oc get secret $registryGerritSecretName -o jsonpath={.data.user} " +
            "-n $NAMESPACE | base64 --decode", returnStdout: true)
    String registryGerritPass = sh(script: "oc get secret $registryGerritSecretName -o jsonpath={.data.password} " +
            "-n $NAMESPACE | base64 --decode", returnStdout: true)
    String trembitaRegistriesSecretName = "trembita-registries"
    String externalSystemsSecretName = "external-systems"
    String vaultPathPrefix = "vault:registry-kv/registry"
    String registryValuesPath = "deploy-templates/values.yaml"
    String vaultSecretsPath = "http://hashicorp-vault.user-management.svc.cluster.local:8200/v1/registry-kv/data/registry/$NAMESPACE"
    String gerritPath = sh(script: "oc get route gerrit -o jsonpath={.spec.path} -n $NAMESPACE", returnStdout: true).replaceAll("/\\z", "")
    String gerritHost = "gerrit.${NAMESPACE}.svc.cluster.local:8080$gerritPath"
    String repoUrl = "http://$registryGerritUser:$registryGerritPass@$gerritHost/$registryRegulationRepoName"
    String isRepoExists = sh(script: "set +x; git ls-remote $repoUrl | grep master > /dev/null", returnStatus: true)
    if (isRepoExists == '0') {
        sh(script: "set +x; git clone $repoUrl")
        LinkedHashMap configFile = readYaml file: regulationConfigurationFilePath
        LinkedHashMap trembitaConfigData = configFile["trembita-exchange-gateway"]
        LinkedHashMap trembitaRegistries = [:]
        if (trembitaConfigData != null) {
            trembitaRegistries = configFile["trembita-exchange-gateway"]["registries"]
            if (trembitaRegistries != null) {
                LinkedHashMap isedrRegistryConfigured = ["edr-registry": "false", "dracs-registry": "false", "idp-exchange-service-registry": "false"]
                String trembitaUrl
                ArrayList defaultRegistries = ["edr-registry", "dracs-registry", "idp-exchange-service-registry"]
                trembitaRegistries.each { k, v ->
                    v.each { kin, vin ->
                        if (kin == "trembita-url") {
                            trembitaUrl = vin
                            v.remove(kin)
                        }
                        if (kin == "secret-name") {
                            v.remove(kin)
                        }
                        v.put("url", trembitaUrl)
                    }
                    if (k == "edr-registry") {
                        v.put("auth", ["type": "AUTH_TOKEN", "secret": "$vaultPathPrefix/$NAMESPACE/$trembitaRegistriesSecretName"])
                        isedrRegistryConfigured["edr-registry"] = "true"
                    }
                    if (k == "dracs-registry") {
                        isedrRegistryConfigured["dracs-registry"] = "true"
                    }
                    if (k == "idp-exchange-service-registry") {
                        isedrRegistryConfigured["idp-exchange-service-registry"] = "true"
                    }
                    if (defaultRegistries.contains(k)) {
                        trembitaRegistries[k].put("protocol", "SOAP")
                        trembitaRegistries[k].put("type", "platform")
                        if (k == "dracs-registry" || k == "idp-exchange-service-registry") {
                            v.put("auth", ["type": "NO_AUTH"])
                        }
                    } else {
                        trembitaRegistries[k].put("protocol", "SOAP")
                        trembitaRegistries[k].put("type", "registry")
                    }
                }
                isedrRegistryConfigured.each { kreg, vstatus ->
                    if (vstatus == "false") {
                        trembitaRegistries.put(kreg, ["protocol": "SOAP","type": "platform"])
                    }
                }
                LinkedHashMap configFileForSecretMigration = readYaml file: regulationConfigurationFilePath
                String isEdrRegistrySecret = configFileForSecretMigration["trembita-exchange-gateway"]["registries"]["edr-registry"]["secret-name"]
                if (isEdrRegistrySecret != null) {
                    String edrRegistrySecret = isEdrRegistrySecret
                    String edrRegistryToken = sh(script: "oc get secret $edrRegistrySecret " +
                            "-o jsonpath='{.data.trembita\\.registries\\.edr-registry\\.auth\\.secret\\.token}' " +
                            "-n $NAMESPACE | base64 --decode", returnStdout: true)

                    sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                            "--request POST $vaultSecretsPath/$trembitaRegistriesSecretName " +
                            "--data '{\"data\": {\"trembita.registries.edr-registry.auth.secret.token\": \"$edrRegistryToken\"}}'")

                    String checkedrRegistryVaultSecret = sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                            "--request GET $vaultSecretsPath/$trembitaRegistriesSecretName", returnStdout: true)
                    if (checkedrRegistryVaultSecret.contains("edr-registry")) {
                        try {
                            sh(script: "oc -n $NAMESPACE delete secret $edrRegistrySecret")
                        } catch (any) {
                        }
                    }
                }
            }
        }
        LinkedHashMap externalSystemsConfigData = configFile["external-systems"]
        if (externalSystemsConfigData != null) {
            LinkedHashMap externalSystemsSecretsKeyValue = [:]
            String authUrl
            String accessTokenJsonPath
            boolean addDiiaConf = true
            externalSystemsConfigData.each { k, v ->
                v.each { kin, vin ->
                    if (kin == "auth") {
                        vin.each { kauth, vauth ->
                            if (kauth == "token-json-path") {
                                accessTokenJsonPath = vauth
                                vin.remove(kauth)
                                vin.put("access-token-json-path", accessTokenJsonPath)
                            }
                            if (kauth == "partner-token-auth-url") {
                                authUrl = vauth
                                vin.remove("partner-token-auth-url")
                                vin.put("auth-url", authUrl)
                            }
                            if (kauth == "type" && vauth == "PARTNER_TOKEN") {
                                String getExternalSystemSecretToken = sh(script: "oc -n $NAMESPACE get secret ${vin["secret-name"]} " +
                                        "-o jsonpath='{.data.token}' --ignore-not-found | base64 --decode", returnStdout: true).trim()
                                if (getExternalSystemSecretToken != '') {
                                    externalSystemsSecretsKeyValue.put("\"external-systems.${k}.auth.secret.token\"", "\"$getExternalSystemSecretToken\"")
                                }
                                vin[kauth] = "AUTH_TOKEN+BEARER"
                            }
                            if (kauth == "type" && vauth == "BASIC") {
                                String getExternalSystemSecretUsername = sh(script: "oc -n $NAMESPACE get secret ${vin["secret-name"]} " +
                                        "-o jsonpath='{.data.username}' --ignore-not-found | base64 --decode", returnStdout: true).trim()
                                String getExternalSystemSecretPass = sh(script: "oc -n $NAMESPACE get secret ${vin["secret-name"]} " +
                                        "-o jsonpath='{.data.password}' --ignore-not-found | base64 --decode", returnStdout: true).trim()
                                if (getExternalSystemSecretUsername != '' && getExternalSystemSecretPass != '') {
                                    externalSystemsSecretsKeyValue.put("\"external-systems.${k}.auth.secret.username\"", "\"$getExternalSystemSecretUsername\"")
                                    externalSystemsSecretsKeyValue.put("\"external-systems.${k}.auth.secret.password\"", "\"$getExternalSystemSecretPass\"")
                                }
                            }
                            if (kauth == "secret-name" || kauth == "partner-token-auth-url" || kauth == "token-json-path") {
                                vin.remove(kauth)
                            }
                            vin.put("secret", "$vaultPathPrefix/$NAMESPACE/$externalSystemsSecretName")
                        }
                    }
                    if (kin == "methods") {
                        v.remove(kin)
                    }
                    v.put("protocol", "REST")
                }
                if (k == "diia") {
                    addDiiaConf = false
                    v.put("type", "platform")
                } else {
                    v.put("type", "registry")
                }
            }
            if (externalSystemsSecretsKeyValue) {
                String externalSystemsSecretsKeyValueCleaned = externalSystemsSecretsKeyValue.toString()
                externalSystemsSecretsKeyValueCleaned = externalSystemsSecretsKeyValueCleaned.substring(1, externalSystemsSecretsKeyValue.toString().length() - 1)
                sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                        "--request POST $vaultSecretsPath/$externalSystemsSecretName " +
                        "--data '{\"data\": {$externalSystemsSecretsKeyValueCleaned}}'", returnStatus: true)
            }
            if (addDiiaConf) {
                externalSystemsConfigData.put("diia", ["protocol": "REST", "type": "platform"])
            }
            LinkedHashMap newExternalSystemsConfigData = ["external-systems": ""]
            newExternalSystemsConfigData["external-systems"] = externalSystemsConfigData
            LinkedHashMap newTrembitaConf = ["trembita": ["registries": ""]]
            newTrembitaConf["trembita"]["registries"] = trembitaRegistries
            String cpGerritSecretName = "gerrit-ciuser-password"
            String registryRepoName = "$NAMESPACE"
            String cpGerritUser = sh(script: "oc get secret $cpGerritSecretName -o jsonpath={.data.user} " +
                    "| base64 --decode", returnStdout: true)
            String cpGerritPass = sh(script: "oc get secret $cpGerritSecretName -o jsonpath={.data.password} " +
                    "| base64 --decode", returnStdout: true)
            String cpGerritPath = sh(script: "oc get route gerrit -o jsonpath={.spec.path}", returnStdout: true).replaceAll("/\\z", "")
            String cpGerritHost = "gerrit:8080$cpGerritPath"
            String registryRepoUrl = "http://$cpGerritUser:$cpGerritPass@$cpGerritHost/$registryRepoName"
            sh(script: "set +x; git clone $registryRepoUrl")
            LinkedHashMap registryValues = readYaml file: "$registryRepoName/$registryValuesPath"
            registryValues["trembita"]["registries"] = trembitaRegistries
            registryValues["external-systems"] = externalSystemsConfigData
            writeYaml file: "$registryRepoName/$registryValuesPath", data: registryValues, overwrite: true
            sh(script: "set +x; cd $registryRepoName " +
                    "&& git config user.name \"$cpGerritUser\" " +
                    "&& git config user.email \"jenkins@example.com\" " +
                    "&& git config user.password \"$cpGerritPass\" " +
                    "&& git add . && git commit -m 'Move config from regulation to registry level' " +
                    "&& git push origin master")
            LinkedHashMap registryConfigurationValues = readYaml file: "../../../deploy-templates/values.yaml"
            LinkedHashMap registryConfigurationValuesNew = registryConfigurationValues + newTrembitaConf + newExternalSystemsConfigData
            writeYaml file: "../../../deploy-templates/values.yaml", data: registryConfigurationValuesNew, overwrite: true
            LinkedHashMap bpmsValues = readYaml file: "../../../../bpms/deploy-templates/values.yaml"
            LinkedHashMap bpmsValuesNew = bpmsValues + newTrembitaConf + newExternalSystemsConfigData
            writeYaml file: "../../../../bpms/deploy-templates/values.yaml", data: bpmsValuesNew, overwrite: true
            LinkedHashMap notificationServiceValues = readYaml file: "../../../../ddm-notification-service/deploy-templates/values.yaml"
            LinkedHashMap notificationServiceValuesNew = notificationServiceValues + newTrembitaConf + newExternalSystemsConfigData
            writeYaml file: "../../../../ddm-notification-service/deploy-templates/values.yaml", data: notificationServiceValuesNew, overwrite: true
            LinkedHashMap configForEndpoints = readYaml file: regulationConfigurationFilePath
            LinkedHashMap externalSystemsEndpoints = configForEndpoints["external-systems"]
            LinkedHashMap renameMethods = [:]
            String renamePath
            String checkOpenshiftSecret
            String checkVaultSecret
            checkVaultSecret = sh(script: "set +x; curl --header \"X-Vault-Token: $centralVaultToken\" " +
                    "--request GET $vaultSecretsPath/$externalSystemsSecretName", returnStdout: true)
            externalSystemsEndpoints.each { k, v ->
                checkOpenshiftSecret = sh(script: "oc -n $NAMESPACE get secret ${v["auth"]["secret-name"]} " +
                        "--ignore-not-found", returnStdout: true).trim()
                if (checkOpenshiftSecret != '' && checkVaultSecret.contains(k)) {
                    try {
                        sh(script: "oc -n $NAMESPACE delete secret ${v["auth"]["secret-name"]}")
                    } catch (any) {
                    }
                }
                v.each { kin, vin ->
                    if (kin == "methods") {
                        vin.each { kvin, vvin ->
                            vvin.each { kkvin, vvvin ->
                                if (kkvin == "path") {
                                    renamePath = vvvin
                                    vvin.remove(kkvin)
                                    vvin.put("resource-path", renamePath)
                                }
                            }
                        }
                        renameMethods = vin
                        v.remove(kin)
                        v.put("operations", renameMethods)
                    }
                    if (kin == "auth" || kin == "url") {
                        v.remove(kin)
                    }
                }
            }
            LinkedHashMap newExternalSystemsEndpoints = ["external-systems": ""]
            newExternalSystemsEndpoints["external-systems"] = externalSystemsEndpoints
            writeYaml file: regulationConfigurationFilePath, data: newExternalSystemsEndpoints, overwrite: true
            sh(script: "set +x; cd $registryRegulationRepoName " +
                    "&& git config user.name \"$registryGerritUser\" " +
                    "&& git config user.email \"jenkins@example.com\" " +
                    "&& git config user.password \"$registryGerritPass\" " +
                    "&& git add . && git commit -m 'Configure external-systems endpoints' " +
                    "&& git push origin master")
        }
    }
}

return this;
