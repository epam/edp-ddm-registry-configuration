void call() {

    String reamlProvideName = "idgovua-officer"
    String secretName = "officer-id-gov-ua-client-secret"
    String checkOfficerKeycloakRealmIdentityProviderResource = sh(script: "" +
            "oc -n $NAMESPACE get keycloakrealmIdentityprovider $reamlProvideName --ignore-not-found", returnStdout: true).trim()
    if (checkOfficerKeycloakRealmIdentityProviderResource != '') {
        String clientId = sh(script: "set +x; oc -n $NAMESPACE get secret $secretName -o jsonpath={.data.clientId} " +
                "-n $NAMESPACE | base64 --decode || :", returnStdout: true)
        String clientSecret = sh(script: "set +x; oc -n $NAMESPACE get secret $secretName -o jsonpath={.data.clientSecret} " +
                "-n $NAMESPACE | base64 --decode || :", returnStdout: true)
        sh(script: "set +x; oc -n $NAMESPACE get keycloakrealmIdentityprovider $reamlProvideName -o json | jq '.spec.config.clientId = \"$clientId\"' | jq '.spec.config.clientSecret = \"$clientSecret\"' | oc replace -n $NAMESPACE --force -f - || :")
    }
}

return this;
