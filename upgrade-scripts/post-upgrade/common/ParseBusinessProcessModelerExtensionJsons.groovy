void call() {

  String finalJson = ""
  String jsonTemplate = "jsonTemplate"
  String yamlTemplate = "template.yaml"

  // Collect json files
  ArrayList jsonFiles = sh(script: "find ../../../element-templates/* -name \"*.json\"", returnStdout: true).trim().split('\n')

  // Concatenate json files
  jsonFiles.eachWithIndex { file, index ->
    if (index != 0)
      finalJson += "," + "\n"
    finalJson += sh(script: "cat ${file}", returnStdout: true)
  }

  // Create template file for creation config map
  writeFile(file: jsonTemplate, text: "[\n${finalJson}\n]")

  // Create and apply manifest template
  writeFile(file: yamlTemplate, text: sh(script: "oc create configmap business-process-modeler-element-templates --from-file=business-process-modeler-element-templates.json=${jsonTemplate} -o yaml --dry-run", returnStdout: true))
  sh "oc create -f ${yamlTemplate} -n $NAMESPACE || oc replace -f ${yamlTemplate} -n $NAMESPACE"
}

return this;
