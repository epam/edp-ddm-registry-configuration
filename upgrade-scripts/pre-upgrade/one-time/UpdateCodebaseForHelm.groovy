void call() {
    String cli = "oc -n ${NAMESPACE}"
    String commonHelmLabel = "app.kubernetes.io/managed-by=Helm"
    String commonNamespaceAnnotation = "meta.helm.sh/release-namespace=${NAMESPACE}"
    String releaseName = "${edpProject}" == "control-plane" ? "codebases" : "codebase-operator"
    ArrayList<LinkedHashMap<String, String>> codebasesWithReleases = [
            [codebaseName: "history-excerptor", releaseName: releaseName],
            [codebaseName: "registry-regulations", releaseName: releaseName]
    ]
    codebasesWithReleases.each {
        def checkCodebaseExists = sh(script: "${cli} get codebase ${it.codebaseName} --ignore-not-found", returnStdout: true)
        if (checkCodebaseExists) {
            LinkedHashMap codebaseContext = readYaml text: sh(script: "${cli} get codebase ${it.codebaseName} " +
                    "-o yaml", returnStdout: true).trim()
            codebaseContext.spec.framework = "registry-codebase"
            codebaseContext.spec.gitUrlPath = "/${it.codebaseName}"
            writeYaml file: "${it.codebaseName}.yaml", data: codebaseContext
            sh "${cli} apply -f ${it.codebaseName}.yaml"
            sh "rm ${it.codebaseName}.yaml"
            String codebaseBranchName = sh(script: "${cli} get codebasebranch -l app.edp.epam.com/codebaseName=${it.codebaseName}" +
                    " -o custom-columns=\"NAME:.metadata.name\" --no-headers", returnStdout: true).trim()
            if (!codebaseBranchName.contains("master")) {
                sh "${cli} scale deployment codebase-operator --replicas=0"
                LinkedHashMap codebaseBranchContext = readYaml text: sh(script: "${cli} get codebasebranch ${codebaseBranchName}" +
                        " -o yaml", returnStdout: true).trim()
                codebaseBranchContext.metadata.name = it.codebaseName + "-master"
                writeYaml file: "${codebaseBranchName}.yaml", data: codebaseBranchContext
                sh "${cli} patch codebasebranch ${codebaseBranchName} -p '{\"metadata\":{\"finalizers\":null}}' --type=merge"
                sh "${cli} delete codebasebranch ${codebaseBranchName}"
                sh "${cli} create -f ${codebaseBranchName}.yaml"
                sh "rm ${codebaseBranchName}.yaml"
                codebaseBranchName = it.codebaseName + "-master"
                sh "${cli} scale deployment codebase-operator --replicas=1"
                sleep 30
            }
            ["codebase", "codebasebranch"].each { resource ->
                String resourceName = (resource == "codebasebranch") ? codebaseBranchName : it.codebaseName
                sh "${cli} annotate ${resource} ${resourceName} ${commonNamespaceAnnotation} --overwrite=true"
                sh "${cli} annotate ${resource} ${resourceName} meta.helm.sh/release-name=${it.releaseName} --overwrite=true"
                sh "${cli} label ${resource} ${resourceName} ${commonHelmLabel} --overwrite=true"
                sh "${cli} annotate ${resource} ${resourceName} kubectl.kubernetes.io/last-applied-configuration-"
            }
        }
    }
}

return this;