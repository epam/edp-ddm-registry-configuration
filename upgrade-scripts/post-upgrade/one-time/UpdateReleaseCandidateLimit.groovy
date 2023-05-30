void call() {

    //Regulation gerrit data
    String registryGerritSecretName = "gerrit-ciuser-password"
    String registryRegulationRepoName = "registry-regulations"
    String registryGerritUser = sh(script: "oc get secret $registryGerritSecretName -o jsonpath={.data.user} " +
            "-n $NAMESPACE | base64 --decode", returnStdout: true)
    String registryGerritPass = sh(script: "oc get secret $registryGerritSecretName -o jsonpath={.data.password} " +
            "-n $NAMESPACE | base64 --decode", returnStdout: true)
    String gerritPath = sh(script: "oc get route gerrit -o jsonpath={.spec.path} -n $NAMESPACE", returnStdout: true).replaceAll("/\\z", "")
    String gerritHost = "gerrit.${NAMESPACE}.svc.cluster.local:8080$gerritPath"

    //Registry gerrit data
    String registryValuesPath = "deploy-templates/values.yaml"
    String cpGerritSecretName = "gerrit-ciuser-password"
    String registryRepoName = "$NAMESPACE"
    String cpGerritUser = sh(script: "oc get secret $cpGerritSecretName -o jsonpath={.data.user} " +
            "| base64 --decode", returnStdout: true)
    String cpGerritPass = sh(script: "oc get secret $cpGerritSecretName -o jsonpath={.data.password} " +
            "| base64 --decode", returnStdout: true)
    String cpGerritPath = sh(script: "oc get route gerrit -o jsonpath={.spec.path}", returnStdout: true).replaceAll("/\\z", "")
    String cpGerritHost = "gerrit:8080$cpGerritPath"
    String registryRepoUrl = "http://$cpGerritUser:$cpGerritPass@$cpGerritHost/$registryRepoName"

    //Check open changes
    int openChanges = sh(script: "set +x; curl -L http://$registryGerritUser:$registryGerritPass@$gerritHost/a/changes/?q=project:$registryRegulationRepoName+status:open" +
            " | sed -e 's/[{}]/''/g' | sed s/\\\"//g | awk -v RS=',' -F: '\$1==\"_number\"{print \$2}' | wc -l",
            returnStdout: true).trim().toInteger()

    //If there are more then 10 changes, increase a limit
    if (openChanges > 10) {
        sh(script: "set +x; git clone $registryRepoUrl")
        //Update maxCandidateVersions in registry values
        LinkedHashMap registryValues = readYaml file: "$registryRepoName/$registryValuesPath"
        registryValues.global.put("regulationManagement", ["maxCandidateVersions": "${openChanges.toString()}"])
        writeYaml file: "$registryRepoName/$registryValuesPath", data: registryValues, overwrite: true
        //Push new registry values
        pushToRepo(registryRepoName, cpGerritUser, cpGerritPass, "Update maxCandidateVersions to $openChanges")
        //Update maxCandidateVersions in configmap
        sh(script: "oc -n $NAMESPACE patch configmap registry-pipeline-stage-name " +
                "-p '{\"data\":{\"maxCandidateVersions\":\"${openChanges.toString()}\"}}'", returnStdout: true)
    }
}

void pushToRepo(String repoName, String gerritUser, String gerritPass, String commitMessage) {
    try {
        sh(script: "set +x; cd $repoName " +
                "&& git config user.name \"$gerritUser\" " +
                "&& git config user.email \"jenkins@example.com\" " +
                "&& git config user.password \"$gerritPass\" " +
                "&& git add . && git commit -m '$commitMessage' " +
                "&& git push origin master")
    } catch (any) {
        println("WARN: Failed to push into $repoName repository")
    }
}

return this;
