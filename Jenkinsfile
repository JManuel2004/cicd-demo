pipeline {
    agent any

    environment {
        APP_NAME  = "cicd-demo"
        IMAGE_TAG = "${APP_NAME}:latest"
        CONTAINER = "${APP_NAME}-container"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    env.GIT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                }
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
                }
            }
        }

        stage('Test') {
            steps {
                // Solo tests unitarios — los de Selenium requieren servidor externo
                sh 'mvn test -Dgroups="au.com.equifax.cicddemo.domain.UnitTest"'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
                }
            }
        }

        stage('Static Analysis (SonarQube)') {
            when { anyOf { branch 'master'; branch 'main' } }
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh "mvn sonar:sonar -Dsonar.projectKey=${APP_NAME}"
                }
            }
        }

        stage('Quality Gate') {
            when { anyOf { branch 'master'; branch 'main' } }
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh "docker build -t ${IMAGE_TAG} ."
            }
        }

        stage('Security Scan (Trivy)') {
            steps {
                script {
                    // --ignore-unfixed: solo falla si hay CVE CRITICAL con parche disponible
                    def result = sh(
                        script: "trivy image --severity CRITICAL --exit-code 1 --ignore-unfixed ${IMAGE_TAG}",
                        returnStatus: true
                    )
                    sh "trivy image --severity HIGH,CRITICAL --format json --output trivy-report.json ${IMAGE_TAG} || true"
                    archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
                    if (result != 0) {
                        error("Trivy detectó vulnerabilidades CRITICAL con parche disponible. Pipeline abortado.")
                    }
                }
            }
        }

        stage('Deploy') {
            steps {
                sh """
                    docker stop ${CONTAINER} || true
                    docker rm   ${CONTAINER} || true
                    docker run -d -p 80:80 --name ${CONTAINER} ${IMAGE_TAG}
                """
                echo "Aplicacion desplegada en http://localhost:80"
            }
        }

        stage('Health Check') {
            steps {
                // wget corre DENTRO del contenedor via docker exec para evitar problemas de red entre contenedores
                sh '''
                    for i in $(seq 1 10); do
                        if docker exec cicd-demo-container wget -qO /dev/null http://localhost:80 2>/dev/null; then
                            echo "Health check OK en intento $i"
                            exit 0
                        fi
                        echo "Intento $i/10 - esperando..."
                        sleep 3
                    done
                    echo "Health check fallido: la aplicacion no responde en puerto 80"
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            echo "Limpiando entorno..."
            cleanWs()
        }
        failure {
            sh "docker stop ${CONTAINER} || true"
            sh "docker rm   ${CONTAINER} || true"
            echo "============================================"
            echo "PIPELINE FALLIDO"
            echo "Commit : ${env.GIT_SHA}"
            echo "Rama   : ${env.BRANCH_NAME}"
            echo "Build  : #${currentBuild.number}"
            echo "Revisa los logs para identificar el error."
            echo "============================================"
        }
        success {
            echo "============================================"
            echo "PIPELINE EXITOSO"
            echo "Commit  : ${env.GIT_SHA}"
            echo "Rama    : ${env.BRANCH_NAME}"
            echo "URL     : http://localhost:80"
            echo "============================================"
        }
    }
}
