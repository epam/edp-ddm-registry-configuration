void call() {

    String reamlProvideName = "citizen-registry-id-gov-ua"
    String secretName = "citizen-id-gov-ua-client-secret"
    String checkCitizenKeycloakRealmIdentityProviderResource = sh(script: "" +
            "oc -n $NAMESPACE get keycloakrealmIdentityprovider $reamlProvideName --ignore-not-found", returnStdout: true).trim()
    if (checkCitizenKeycloakRealmIdentityProviderResource != '') {
        String clientId = sh(script: "set +x; oc -n $NAMESPACE get secret $secretName -o jsonpath={.data.clientId} " +
                "-n $NAMESPACE | base64 --decode || :", returnStdout: true)
        String clientSecret = sh(script: "set +x; oc -n $NAMESPACE get secret $secretName -o jsonpath={.data.clientSecret} " +
                "-n $NAMESPACE | base64 --decode || :", returnStdout: true)
        sh(script: "set +x; oc -n $NAMESPACE get keycloakrealmIdentityprovider $reamlProvideName -o json | jq '.spec.config.clientId = \"$clientId\"' | jq '.spec.config.clientSecret = \"$clientSecret\"' | oc replace -n $NAMESPACE --force -f - || :")
    }
}

return this;
