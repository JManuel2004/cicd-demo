
# CICD-DEMO

This project aims to be the basic skeleton to apply continuous integration and continuous delivery.

##  Características de la Pipeline

Este proyecto incluye un pipeline CI/CD completo con:

- **Build & Test**: Compilación con Maven y ejecución de pruebas unitarias
- **Análisis Estático**: SonarQube para análisis de calidad de código
- **Seguridad de Contenedor**: Trivy para escaneo de vulnerabilidades
- **Gatekeeping**: Fallos automáticos si hay Security Hotspots o vulnerabilidades CRITICAL
- **Docker Build**: Construcción de imagen Docker
- **Deploy**: Despliegue automático a dev/qa en ramas autorizadas

## Topology

CICD Demo uses some kubernetes primitives to deploy:

* Deployment
* Services
* Ingress ( with TLS )
* HorizontalPodAutoscaler

```bash
     internet
        |
   [ Ingress ]
   --|-----|--
   [ Services ]
   --|-----|--
   [   Pods   ]
   
   (Auto-scaling via HPA)
```

This project includes:

* Spring Boot java app
* Jenkinsfile integration to run pipelines
* Dockerfile containing the base image to run java apps
* Makefile and docker-compose to make the pipeline steps much simpler
* **Helm Chart** for Kubernetes deployment (NEW!)
  - Development environment (1 replica, minimal resources)
  - Production environment (3+ replicas, autoscaling, security policies)

## 🚀 Kubernetes + Helm Deployment

### Prerrequisitos para Kubernetes

#### Opción A: Docker Desktop (Windows/Mac)
```bash
# Instalar Docker Desktop
# Settings → Kubernetes → Enable Kubernetes
```

#### Opción B: Minikube
```bash
# Instalar Minikube
choco install minikube  # Windows

# Iniciar cluster
minikube start
```

### Instalar Helm
```bash
# Windows
choco install kubernetes-helm

# O descargar desde
https://helm.sh/docs/intro/install/
```

### Estructu​ra Helm Chart

```
helm/cicd-demo/
├── Chart.yaml              # Metadatos del chart
├── values.yaml             # Valores por defecto
├── values-dev.yaml         # Overrides desarrollo
├── values-prod.yaml        # Overrides producción
└── templates/
    ├── deployment.yaml     # Deployment de Kubernetes
    ├── service.yaml        # Service para exposición
    ├── ingress.yaml        # Ingress para ruteo HTTP
    ├── hpa.yaml            # HorizontalPodAutoscaler
    ├── serviceaccount.yaml  # ServiceAccount
    └── _helpers.tpl        # Funciones reutilizables
```

### Desplegar Manualmente con Helm

#### Desarrollo
```bash
helm upgrade --install cicd-demo-dev ./helm/cicd-demo \
  --namespace development \
  --create-namespace \
  --values helm/cicd-demo/values-dev.yaml \
  --set image.tag=master-latest
```

#### Producción
```bash
helm upgrade --install cicd-demo-prod ./helm/cicd-demo \
  --namespace production \
  --create-namespace \
  --values helm/cicd-demo/values-prod.yaml \
  --set image.tag=1.0.0
```

### Verificar Deployment

```bash
# Ver releases
helm list -n development
helm list -n production

# Ver status
helm status cicd-demo-dev -n development

# Ver historia
helm history cicd-demo-dev -n development

# Rollback (si es necesario)
helm rollback cicd-demo-dev 1 -n development
```

### Monitorear Pods

```bash
# Ver pods en ejecución
kubectl get pods -n development
kubectl get pods -n production

# Logs de un pod
kubectl logs -f deployment/cicd-demo-dev -n development

# Exec en un pod
kubectl exec -it <pod-name> -n development -- /bin/bash

# Ver eventos
kubectl describe pod <pod-name> -n development
```

## Pipeline Setup

### Prerrequisitos

#### 1. Jenkins en Docker
```bash
docker run -p 8080:8080 -p 50000:50000 -v jenkins_home:/var/jenkins_home jenkins/jenkins:lts
```

#### 2. Instalar plugins en Jenkins
- Pipeline
- Git
- Docker
- SonarQube Scanner
- Slack Notification (opcional)

#### 3. Credenciales necesarias en Jenkins
Ir a **Jenkins > Manage Credentials** y crear:
- `REGISTRY_PASSWORD` - Contraseña de Docker Registry
- `REGISTRY_USERNAME` - Usuario de Docker Registry
- `POSTGRES_PASSWORD` - Contraseña PostgreSQL
- `SONAR_TOKEN` - Token de SonarQube

**Para Kubernetes con Helm:**
- `KUBECONFIG` - Archivo kubeconfig de tu cluster Kubernetes
  - O configurar `~/.kube/config` en el agente Jenkins
- Jenkins debe tener `kubectl` y `helm` instalados

### Configurar SonarQube Localmente

1. **Iniciar contenedores**:
```bash
docker-compose up -d sonarqube postgres-sonar
```

2. **Acceder a SonarQube**:
   - URL: `http://localhost:9000`
   - Usuario: `admin`
   - Contraseña: `admin`

3. **Generar token**:
   - Ir a **My Account > Security > Generate Tokens**
   - Nombre: `jenkins`
   - Copiar el token y guardarlo como credencial `SONAR_TOKEN` en Jenkins

### Instalar Trivy en Jenkins

**En el agente de Jenkins** (donde corre el Jenkinsfile):

```bash
# Descargar Trivy
wget https://github.com/aquasecurity/trivy/releases/download/v0.50.0/trivy_0.50.0_Linux-64bit.tar.gz
tar xvf trivy_0.50.0_Linux-64bit.tar.gz
sudo mv trivy /usr/local/bin/

# Verificar instalación
trivy version
```

## Flujo de Pipeline

```
Checkout 
   ↓
Build & Test (mvn clean package)
   ↓
SonarQube Analysis (con Gatekeeping)
   ↓
Trivy Security Scan (con Gatekeeping)
   ↓
Integration Tests
   ↓
Docker Build
   ↓
Docker Push
   ↓
Deploy To Kubernetes (Helm) ← NEW!
   ├─ Development (master/main)
   └─ Production (master/release-*)
   ↓
Health Check (Kubernetes liveness/readiness probes)
```

**Con Docker Local (alternativa):**
```
... (mismo hasta Docker Push)
   ↓
Deploy To Local (docker run -p 8080:8080)
   ↓
Health Check (http://localhost:8080/actuator/health)
```

## Puertas de Calidad (Gatekeeping) 

### SonarQube Gatekeeping
El pipeline **fallará automáticamente** si:
- Se detectan **Security Hotspots** en el análisis de código

**Resolución**:
1. Acceder a SonarQube: `http://localhost:9000`
2. Revisar los **Security Hotspots**
3. Aplicar las correcciones recomendadas
4. Ejecutar el pipeline nuevamente

### Trivy Gatekeeping
El pipeline **fallará automáticamente** si:
- Se detectan vulnerabilidades de nivel **CRITICAL** en la imagen Docker

**Resolución**:
1. Revisar el reporte de Trivy
2. Actualizar dependencias vulnerables en `pom.xml`
3. Reconstruir la imagen Docker
4. Ejecutar el pipeline nuevamente

## How to run the app:

```make
make
```

## Testing

Unit tests and integrations tests are separated using [JUnit Categories][].

##  Limpieza de Infraestructura

El bloque `post` del pipeline automaticamente:
- Detiene y limpia contenedores Docker: `docker-compose down -v`
- Limpia imágenes no utilizadas: `docker system prune`
- Limpia el workspace de Jenkins

Para limpiar manualmente:
```bash
docker-compose down -v
docker system prune -f
```

## Acceso a Reportes

Los reportes de seguridad se archivan en cada ejecución del pipeline:
- **Trivy Report**: `trivy-report.json`
- **Surefire Tests**: `target/surefire-reports/`
- **JAR Artifacts**: `target/*.jar`

Acceder desde Jenkins:
1. Seleccionar la ejecución del pipeline
2. Ir a **Build Artifacts**
3. Descargar los reportes

## 🧪 Prueba Final E2E (End-to-End)

Esta sección te guía para validar que el pipeline funciona correctamente en su totalidad.

### Paso 1: Hacer un cambio en la aplicación

Edita el archivo principal de la aplicación para hacer un cambio visible:

```bash
# Opción 1: Editar mensaje en ApiController
nano src/main/java/au/com/equifax/cicddemo/controller/ApiController.java
# Cambiar un mensaje o agregar un nuevo endpoint

# Opción 2: Editar properties
nano src/main/resources/application.properties
# Cambiar server.port, logging.level, etc.

# Opción 3: Crear un cambio intencional con deuda técnica (para demostración)
nano src/main/java/au/com/equifax/cicddemo/controller/ApiController.java

# Agregue un comentario TODO o código sin documentar:
// TODO: Refactorizar esta lógica
// Código inseguro para demostración:
String query = request.getParameter("search"); // Falta validar entrada
```

### Paso 2: Commit y Push

```bash
# Agregar cambios
git add .

# Commit con mensaje descriptivo
git commit -m "feat: Actualizar mensaje de bienvenida

- Se mejora el mensaje en ApiController
- Se agrega nuevo endpoint de health check
- Cambios preparados para validación de pipeline"

# Push a rama master (o main)
git push origin master
```

### Paso 3: Observar Jenkins

1. **Ir a Jenkins Dashboard**:
   - URL: `http://localhost:8080`
   - Ir a tu pipeline

2. **Ver el build automático**:
   - Jenkins detecta automáticamente el push
   - Build inicia en segundos

3. **Monitorear las etapas**:

```
✓ Checkout          (recibe código del repositorio)
✓ Build & Test      (compila y ejecuta pruebas)
✓ SonarQube         (analiza calidad de código)
✓ Trivy             (escanea vulnerabilidades)
✓ Integration Tests (ejecuta pruebas de integración)
✓ Docker Build      (construye la imagen)
✓ Docker Push       (pushea a registry)
✓ Deploy To Local   (despliega en puerto 8080)
✓ Health Check      (verifica que la app responde)
```

### Paso 4: Validar resultados

#### Validar Build Exitoso
```bash
# Revisar logs del build
# Jenkins > Pipeline > Stage View > Ver logs

# Buscar líneas:
✓ "BUILD SUCCESS" en Maven
✓ "Aplicación respondiendo correctamente" en Health Check
✓ "PIPELINE COMPLETADO EXITOSAMENTE" al final
```

#### Validar Deploy Local
```bash
# Verificar que el contenedor está corriendo
docker ps | grep cicd-demo-container

# Probar la aplicación
curl http://localhost:8080/actuator/health
# Debe responder: {"status":"UP"}

# Ver logs del contenedor
docker logs cicd-demo-container
```

#### Validar SonarQube
```bash
# Acceder a SonarQube
# URL: http://localhost:9000

# Buscar el proyecto "cicd-demo"
# Revisar:
- Líneas de código
- Coverage
- Security Hotspots
- Bugs detectados
```

#### Validar Trivy
```bash
# Revisar reporte de Trivy
# Jenkins > Pipeline > Build Artifacts > trivy-report.json

# Ver vulnerabilidades detectadas
cat trivy-report.json | grep "Severity"
```

### Paso 5: Introducir un error deliberado (Prueba de Gatekeeping)

Para probar que el **gatekeeping funciona**, introduce un error:

#### Opción A: Error de compilación
```bash
# Editar un archivo Java
nano src/main/java/au/com/equifax/cicddemo/controller/ApiController.java

# Agregar error de sintaxis
public void invalidMethod(
    // Falta cerrar parentesis

# Commit y push
git add .
git commit -m "test: Error de compilación para validar gatekeeping"
git push origin master

# Resultado: Build falla en "Build & Test"
```

#### Opción B: Error en SonarQube
```bash
# Agregar código inseguro
String password = request.getParameter("pwd"); // Security Hotspot
String sql = "SELECT * FROM users WHERE id=" + userId; // SQL Injection

# Commit y push
git add .
git commit -m "test: Agregar Security Hotspots para validar SonarQube"
git push origin master

# Resultado: Pipeline falla en "Static Analysis (SonarQube)"
```

#### Opción C: Error en Trivy
```bash
# Editar Dockerfile para usar imagen vulnerable
# Esto es más complejo - usar solo si necesitas demostrar Trivy

# En Dockerfile:
# FROM tomcat:8.0  # Versión vulnerable

# Commit y push
git add .
git commit -m "test: Usar imagen vulnerable para validar Trivy"
git push origin master

# Resultado: Pipeline falla en "Container Security Scan (Trivy)"
```

### Paso 6: Revisar notificaciones de error

Cuando el pipeline falla, verás:

```
════════════════════════════════════════════
❌ PIPELINE FALLIDO
════════════════════════════════════════════
Etapa fallida: Build & Test
Rama: master
Build: #5
════════════════════════════════════════════
Acciones a tomar:
1. Revisar logs completos en Jenkins
2. Si es SonarQube: revisar Security Hotspots
3. Si es Trivy: actualizar dependencias vulnerables
4. Hacer commit con las correcciones
5. Push al repositorio para reintentar
════════════════════════════════════════════
```

### Paso 7: Corregir y reintentar

```bash
# Corregir el error
# Editar el archivo problemático y arreglarlo

# Commit y push nuevamente
git add .
git commit -m "fix: Resolver Security Hotspots detectados por SonarQube"
git push origin master

# El pipeline reinicia automáticamente
# Esta vez debe completarse exitosamente
```

### ✅ Validación completa

Una ejecución exitosa mostrará:

```
════════════════════════════════════════════
✅ PIPELINE COMPLETADO EXITOSAMENTE
════════════════════════════════════════════
🚀 La aplicación está disponible en:
   • Local: http://localhost:8080
   • Kubernetes DEV: (URL desde Kubernetes)

📊 Reportes disponibles:
   • Build: target/*.jar
   • Tests: target/surefire-reports/
   • Seguridad: trivy-report.json
════════════════════════════════════════════
```

## Troubleshooting

### SonarQube no responde
```bash
docker-compose logs -f sonarqube
```

### Trivy no encontrado
```bash
which trivy
trivy version
```

### Permisos de Docker en Jenkins
```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```

### Limpiar completamente (desarrollo)
```bash
docker-compose down -v --remove-orphans
rm -rf ~/.m2/repository  # Opcional: limpia caché Maven
```

[JUnit Categories]: https://maven.apache.org/surefire/maven-surefire-plugin/examples/junit.html

### Unit Tests

```java
mvn test -Dgroups=UnitTest
```

Or using Docker:

```bash
make build
```

### Integration Tests

```java
mvn integration-test -Dgroups=IntegrationTests
```

Or using Docker:

```bash
make integrationTest
```

### System Tests

System tests run with Selenium using docker-compose to run a [Selenium standalone container][] with Chrome.

[Selenium standalone container]: https://github.com/SeleniumHQ/docker-selenium

Using Docker:

* If you are running locally, make sure the `$APP_URL` is populated and points to a valid instance of your application. This variable is populated automatically in Jenkins.

```bash
APP_URL=http://dev-cicd-demo-master.anzcd.internal/ make systemTest
```