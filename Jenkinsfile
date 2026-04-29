#!/usr/bin/env groovy

pipeline {
    environment{
       FEATURE_NAME = BRANCH_NAME.replaceAll('[\\(\\)_/]','-').toLowerCase()
       REGISTRY_PASSWORD = credentials('REGISTRY_PASSWORD')
       REGISTRY_USERNAME = credentials('REGISTRY_USERNAME')
       POSTGRES_PASSWORD = credentials('POSTGRES_PASSWORD')
       APP_NAME = "cicd-demo"
       GIT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
       BUILD_VERSION = "${FEATURE_NAME}-${GIT_SHA}"
       REGISTRY_HOST = "github.com"
       IMAGE_NAME = "${REGISTRY_HOST}/helderklemp/${APP_NAME}"
    }
    agent any 
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build & Test') {
            steps {
                sh "mvn clean package"
            }
        }

        stage('Docker Build') {
            when {
                expression { BRANCH_NAME != 'main' && BRANCH_NAME != 'master' }
            }
            steps {
                sh "make dockerLogin dockerBuild"
            }
        }

        stage('Docker Scan') {
            steps {
                sh "make dockerScan"
            }
            post {
                cleanup {
                    sh "docker-compose down -v"
                }
            }
        }
        
        stage('Static Analysis (SonarQube)') {
            when {
                anyOf { branch 'master'; branch 'main'; branch 'release'}
            }    
            steps {
                script {
                    sh "mvn sonar:sonar -Dsonar.projectKey=${APP_NAME} -Dsonar.host.url=http://sonarqube:9000 -Dsonar.login='${SONAR_TOKEN}'"
                    
                    
                    def response = sh(
                        script: """curl -s http://sonarqube:9000/api/hotspots/search?projectKey=${APP_NAME} | grep -o '"total":[0-9]*' || echo '"total":0'""",
                        returnStdout: true
                    ).trim()
                    def hotspots = response.replaceAll('[^0-9]', '').toInteger()
                    
                    if (hotspots > 0) {
                        error("Pipeline Fallido: SonarQube detectó ${hotspots} Security Hotspots. Resuelve antes de continuar.")
                    }
                }
            }
        }

        stage('Container Security Scan (Trivy)') {
            steps {
                script {
                    sh "docker build -t ${IMAGE_NAME}:${BUILD_VERSION} ."
                    
                    // Gatekeeping: Fallar si hay vulnerabilidades CRITICAL
                    def trivyResult = sh(
                        script: "trivy image ${IMAGE_NAME}:${BUILD_VERSION} --severity CRITICAL --exit-code 1",
                        returnStatus: true
                    )
                    
                    if (trivyResult != 0) {
                        error(" Pipeline Fallido: Trivy detectó vulnerabilidades CRITICAL. Resuelve antes de continuar.")
                    }
                    
                    // Reporte informativo de todas las severidades
                    sh "trivy image ${IMAGE_NAME}:${BUILD_VERSION} --severity HIGH,CRITICAL --format json > trivy-report.json || true"
                }
            }
        }

        stage('Integration Tests') {
            steps {
                sh "make integrationTest"
            }
        }

        stage('Push Docker Image') {
            steps {
                sh "make dockerPush"
            }
        }

        stage('Push Latest Tag') {
            when { branch 'master' }
            steps {
                sh "make dockerPushLatest"
            }
        }

        stage('Deploy To dev') {
            when { expression { BRANCH_NAME ==~ /(master|main)/ }}
            environment { 
                ENV = "dev"
                APP_DNS = util.selectAppUrl(ENV, FEATURE_NAME, APP_NAME)
                KUBE_SERVER = credentials("KUBE_API_SERVER")
                KUBE_TOKEN = credentials("KUBE_DEV_TOKEN")
            }
            steps {
                sh "make kubeLogin deploy"
            }
        }
        
        stage('Deploy To qa') {
            when { expression { BRANCH_NAME ==~ /(master|release-[0-9]+$)/ }} // Only Master and Release branches 
            environment { 
                ENV = "qa"
                APP_DNS = util.selectAppUrl(ENV, FEATURE_NAME, APP_NAME)
                KUBE_SERVER = credentials("KUBE_API_SERVER")
                KUBE_TOKEN = credentials("KUBE_QA_TOKEN")
            }
            steps {
                sh "make kubeLogin deploy"
            }
        }

        stage('Deploy To Local (master/main)') {
            when { expression { BRANCH_NAME ==~ /(master|main)/ }}
            steps {
                script {
                    echo "🚀 Desplegando contenedor localmente en puerto 8080..."
                    
                    // Detener contenedor anterior si existe
                    sh '''
                        docker stop cicd-demo-container || true
                        docker rm cicd-demo-container || true
                    '''
                    
                    // Desplegar nuevo contenedor
                    sh '''
                        docker run -d \
                            --name cicd-demo-container \
                            -p 8080:8080 \
                            ${IMAGE_NAME}:${BUILD_VERSION}
                    '''
                    
                    echo "✅ Contenedor desplegado en http://localhost:8080"
                }
            }
        }

        stage('Health Check') {
            when { expression { BRANCH_NAME ==~ /(master|main)/ }}
            steps {
                script {
                    echo "🔍 Verificando salud de la aplicación..."
                    
                    sh '''
                        sleep 5 # Esperar a que la app inicie
                        
                        max_attempts=10
                        attempts=0
                        
                        while [ $attempts -lt $max_attempts ]; do
                            response=$(curl -s -w "%{http_code}" -o /dev/null http://localhost:8080/actuator/health || echo "000")
                            
                            if [ "$response" = "200" ]; then
                                echo "✅ Aplicación respondiendo correctamente (HTTP 200)"
                                exit 0
                            fi
                            
                            attempts=$((attempts + 1))
                            echo "Intento $attempts/$max_attempts - HTTP $response"
                            sleep 2
                        done
                        
                        echo "❌ Aplicación no responde después de $max_attempts intentos"
                        exit 1
                    '''
                }
            }
        }
        
    }
    post {
        always {
            script {
                def buildStatus = currentBuild.result
                def buildDuration = currentBuild.durationString
                def buildNumber = currentBuild.number
                
                echo "════════════════════════════════════════════"
                echo "📊 RESUMEN DE EJECUCIÓN"
                echo "════════════════════════════════════════════"
                echo "✓ Rama: ${BRANCH_NAME}"
                echo "✓ Build: #${buildNumber}"
                echo "✓ Duración: ${buildDuration}"
                echo "✓ Estado: ${buildStatus}"
                echo "════════════════════════════════════════════"
                
                // Notificaciones por rama
                if(BRANCH_NAME ==~ /(master|main|release-[0-9]+$)/ ){
                    util.notifySlack(buildStatus)
                }
            }
            
            // Archivar reportes
            archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true, fingerprint: true
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
            
            // Archivar reportes de seguridad si existen
            archiveArtifacts artifacts: 'trivy-report.json', allowEmptyArchive: true
            
            // Limpieza de infraestructura
            sh '''
                echo "🧹 Limpiando entorno..."
                docker-compose down -v || true
                docker system prune -f --filter "until=24h" || true
            '''
            
            // Limpiar workspace
            cleanWs()
        }
        
        failure {
            script {
                def failureStage = env.STAGE_NAME ?: "Desconocida"
                echo "════════════════════════════════════════════"
                echo "❌ PIPELINE FALLIDO"
                echo "════════════════════════════════════════════"
                echo "Etapa fallida: ${failureStage}"
                echo "Rama: ${BRANCH_NAME}"
                echo "Build: #${currentBuild.number}"
                echo "════════════════════════════════════════════"
                echo "Acciones a tomar:"
                echo "1. Revisar logs completos en Jenkins"
                echo "2. Si es SonarQube: revisar Security Hotspots"
                echo "3. Si es Trivy: actualizar dependencias vulnerables"
                echo "4. Hacer commit con las correcciones"
                echo "5. Push al repositorio para reintentar"
                echo "════════════════════════════════════════════"
                
                // Notificación de falla
                if(BRANCH_NAME ==~ /(master|main)/ ){
                    util.notifySlack("FAILED")
                }
            }
        }
        
        success {
            script {
                echo "════════════════════════════════════════════"
                echo "✅ PIPELINE COMPLETADO EXITOSAMENTE"
                echo "════════════════════════════════════════════"
                
                if(BRANCH_NAME ==~ /(master|main)/ ){
                    echo "🚀 La aplicación está disponible en:"
                    echo "   • Local: http://localhost:8080"
                    echo "   • Kubernetes DEV: ${APP_DNS}"
                }
                
                echo "📊 Reportes disponibles:"
                echo "   • Build: target/*.jar"
                echo "   • Tests: target/surefire-reports/"
                echo "   • Seguridad: trivy-report.json"
                echo "════════════════════════════════════════════"
                
                // Notificación de éxito
                if(BRANCH_NAME ==~ /(master|main)/ ){
                    util.notifySlack("SUCCESS")
                }
            }
        }
        
        unstable {
            script {
                echo "⚠️ Pipeline UNSTABLE - Revisar advertencias"
                if(BRANCH_NAME ==~ /(master|main)/ ){
                    util.notifySlack("UNSTABLE")
                }
            }
        }
    }
}
