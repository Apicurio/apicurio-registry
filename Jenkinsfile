pipeline {
  agent {
    label 'phoenix-002'
  }
  stages {

    stage('schema-registry') {
      when {
        changeset "**"
      }
      steps {
        build job: "schema-registry/develop", wait: false
      }
    }
  }
}