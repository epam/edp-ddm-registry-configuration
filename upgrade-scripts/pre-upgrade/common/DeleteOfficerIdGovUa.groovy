void call() {
    String authFlowName = "id-gov-ua-officer"
    sh "oc patch KeycloakAuthFlows $authFlowName -p '{\"metadata\":{\"finalizers\":null}}' --type=merge -n $NAMESPACE || true"
    sh "oc delete keycloakauthflow $authFlowName -n $NAMESPACE || true"

    String identityProviderName = "idgovua-officer"
    sh "oc patch KeycloakRealmIdentityProviders $identityProviderName -p '{\"metadata\":{\"finalizers\":null}}' --type=merge -n $NAMESPACE || true"
    sh "oc delete keycloakRealmIdentityProvider $identityProviderName -n $NAMESPACE || true"
}

return this;