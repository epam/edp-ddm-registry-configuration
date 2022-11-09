void call() {
  String cmName = "registry-environment-js"
  try {
    sh "oc annotate cm ${cmName} helm.sh/hook- meta.helm.sh/release-name=registry-configuration " +
      "meta.helm.sh/release-namespace=${NAMESPACE} --overwrite=true -n ${NAMESPACE}; " +
      "oc label cm ${cmName} app.kubernetes.io/managed-by=Helm --overwrite=true -n ${NAMESPACE}"
  } catch (any) {
    println("WARN: failed to annotate configmap ${cmName}")
  }
}

return this;
