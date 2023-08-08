void call() {
    String configMapName = "business-process-modeler-element-templates-js"
    sh "oc delete configmap $configMapName -n $NAMESPACE || true"
}

return this;
