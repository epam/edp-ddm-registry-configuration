void call() {

    String newToken = sh(script: "set +x; oc -n $NAMESPACE get secret trembita-registries-secrets -o jsonpath={.data.\"trembita\\.registries\\.edr\\-registry\\.auth\\.secret\\.token\"} " +
            "-n $NAMESPACE | base64 --decode || :", returnStdout: true)
    sh(script: "set +x; oc -n $NAMESPACE get keycloakauthflow citizen-portal-dso-citizen-auth-flow -o json | jq '.spec.authenticationExecutions[1].authenticatorConfig.config.registryToken = \"${newToken}\"' | oc replace -n $NAMESPACE --force -f - || :")
    sh "oc rollout restart deployment/keycloak-operator -n $NAMESPACE || :"
}

return this;
