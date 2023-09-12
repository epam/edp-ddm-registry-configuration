void call() {
    String resourceName = "id-gov-ua-officer"
    sh "oc patch KeycloakAuthFlows $resourceName -p '{\"metadata\":{\"finalizers\":null}}' --type=merge -n $NAMESPACE || true"
    sh "oc delete keycloakauthflow $resourceName -n $NAMESPACE || true"
}

return this;