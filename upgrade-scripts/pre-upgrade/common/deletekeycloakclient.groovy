void call() {

  ["admin-portal", "officer-portal", "citizen-portal"].each { it ->
      sh "oc delete keycloakclient $it -n $NAMESPACE || true"
  }

}

return this;
