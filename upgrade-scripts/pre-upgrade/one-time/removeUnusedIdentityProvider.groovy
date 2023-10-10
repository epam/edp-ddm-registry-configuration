void call() {
    String identityProviderName = "citizen-id-gov-ua"
    try {
        sh "oc delete KeycloakRealmIdentityProvider $identityProviderName -n ${NAMESPACE} --ignore-not-found=true"
    } catch (any) {
        println("WARN: failed to delete unused KeycloakRealmIdentityProvider ${identityProviderName}")
    }
}

return this;
