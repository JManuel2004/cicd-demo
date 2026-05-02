# cicd-demo

Proyecto de referencia para aplicar integración y entrega continua sobre una aplicación Spring Boot. Incluye un pipeline de Jenkins completo, análisis de calidad con SonarQube, escaneo de seguridad con Trivy y despliegue con Docker o Helm sobre Kubernetes.

## Contenido del proyecto

- Aplicación Spring Boot (Java 11)
- `Jenkinsfile` con el pipeline CI/CD
- `Dockerfile` basado en `eclipse-temurin:11-jre-alpine`
- `Makefile` y `docker-compose.yml` para ejecutar las etapas del pipeline localmente
- Helm Chart para despliegue en Kubernetes (`helm/cicd-demo/`)

## Pipeline

El pipeline se activa con cada push al repositorio. Algunas etapas solo se ejecutan en ramas protegidas (`master`, `main`, `release`).

```
Checkout
  -> Build              (mvn clean package -DskipTests)
  -> Test               (mvn test)
  -> Static Analysis    (SonarQube)        [master / main / release]
  -> Quality Gate       (SonarQube)        [master / main / release]
  -> Docker Build                          [master / main]
  -> Security Scan      (Trivy)            [master / main]
  -> Integration Tests
  -> Docker Push                           [master / main]
  -> Push Latest Tag                       [master]
  -> Deploy             (Helm o Docker)    [master / main]
  -> Health Check                          [master / main]
```

El deploy tiene dos modos seleccionables mediante el parametro `DEPLOY_TO_KUBERNETES`:

- `true`: despliega con Helm en el namespace `production`
- `false` (por defecto): levanta un contenedor local en el puerto 8080

## Configurar Jenkins

### 1. Crear el pipeline

1. **Jenkins > New Item** — nombre `cicd-demo`, tipo **Pipeline**
2. En la seccion **Pipeline**:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: URL del repositorio
   - Branch Specifier: `*/master`
   - Script Path: `Jenkinsfile`
3. En **Build Triggers**, activar una de las siguientes opciones:
   - **GitHub hook trigger for GITScm polling** (requiere webhook configurado)
   - **Poll SCM**: `H/5 * * * *`

### 2. Credenciales requeridas

Agregar en **Jenkins > Manage Credentials**:

| ID                  | Descripcion                        |
|---------------------|------------------------------------|
| `REGISTRY_USERNAME` | Usuario del registro Docker        |
| `REGISTRY_PASSWORD` | Contrasena del registro Docker     |
| `POSTGRES_PASSWORD` | Contrasena de PostgreSQL           |
| `SONAR_TOKEN`       | Token de autenticacion SonarQube   |

### 3. Configurar SonarQube

1. Iniciar los contenedores:
   ```bash
   docker-compose up -d sonarqube postgres-sonar
   ```
2. Acceder a `http://localhost:9000` (usuario: `admin`, contrasena: `admin`)
3. Generar un token en **My Account > Security > Generate Tokens**
4. Guardar el token como credencial `SONAR_TOKEN` en Jenkins
5. Registrar el servidor en **Jenkins > Configure System > SonarQube servers** con el nombre `SonarQube`

### 4. Instalar Trivy en el agente Jenkins

```bash
wget https://github.com/aquasecurity/trivy/releases/download/v0.50.0/trivy_0.50.0_Linux-64bit.tar.gz
tar xvf trivy_0.50.0_Linux-64bit.tar.gz
sudo mv trivy /usr/local/bin/
```

## Puertas de calidad

El pipeline falla automaticamente en los siguientes casos:

- **SonarQube Quality Gate**: el analisis no supera el umbral de calidad configurado en el servidor.
- **Trivy**: se detectan vulnerabilidades de severidad `CRITICAL` en la imagen Docker.

El reporte de Trivy se archiva como artefacto del build en `trivy-report.json`.

## Despliegue con Helm

### Estructura del chart

```
helm/cicd-demo/
    Chart.yaml
    values.yaml
    templates/
        deployment.yaml
        service.yaml
        _helpers.tpl
```

### Desplegar manualmente

```bash
helm upgrade --install cicd-demo ./helm/cicd-demo \
  --namespace production \
  --create-namespace \
  --set image.repository=ghcr.io/helderklemp/cicd-demo \
  --set image.tag=<version>
```

### Verificar el estado

```bash
helm list -n production
helm status cicd-demo -n production
kubectl get all -n production
```

### Rollback

```bash
helm rollback cicd-demo 1 -n production
```

## Ejecucion local

Requiere tener Docker instalado.

```bash
# Construir la imagen y ejecutar pruebas unitarias
make build

# Ejecutar pruebas de integracion
make integrationTest

# Construir la imagen Docker
make dockerBuild

# Ejecutar pruebas de sistema (Selenium)
APP_URL=http://localhost:8080 make systemTest
```

## Pruebas

Las pruebas estan separadas por tipo usando categorias de JUnit:

```bash
# Unitarias
mvn test -Dgroups=UnitTest

# Integracion
mvn integration-test -Dgroups=IntegrationTests

# Sistema (requiere Selenium standalone en ejecucion)
mvn test -Dgroups=SystemTest
```

## Limpieza

El bloque `post` del pipeline limpia automaticamente los contenedores y el workspace al finalizar cada ejecucion.

Para limpiar manualmente:

```bash
docker-compose down -v
docker system prune -f
```

## Prueba Final

El repositorio incluye `DebtController.java`, un archivo con vulnerabilidades y deuda técnica intencionales para demostrar el comportamiento del pipeline ante código inseguro.

### Escenario A — Pipeline bloqueado por SonarQube

Con `DebtController.java` presente, SonarQube detecta:

| Regla | Tipo | Descripción |
|---|---|---|
| S2068 | Security Hotspot | Contraseña hardcodeada (`DB_PASSWORD = "admin123"`) |
| S4790 | Security Hotspot | Uso de MD5 (algoritmo débil) |
| S2245 | Security Hotspot | `new Random()` en contexto de seguridad |
| S2077 | Vulnerability | Inyección SQL por concatenación de cadenas |

**Resultado:** Quality Gate falla → pipeline abortado → no se despliega.

```bash
# 1. Asegúrate de que DebtController.java esté en el repositorio
git add src/main/java/au/com/equifax/cicddemo/controller/DebtController.java
git commit -m "test: agregar código inseguro para demostrar Quality Gate"
git push
# Jenkins ejecuta el pipeline → etapa Quality Gate falla → pipeline abortado
```

### Escenario B — Pipeline exitoso con código limpio

```bash
# 1. Elimina el archivo con deuda técnica
git rm src/main/java/au/com/equifax/cicddemo/controller/DebtController.java
git commit -m "fix: eliminar código inseguro - Quality Gate debe pasar"
git push
# Jenkins ejecuta el pipeline → todas las etapas pasan → nueva versión desplegada en http://localhost:80
```

### Escenario C — Cambio visual sin tocar código Java

```bash
# Edita el texto en src/main/resources/static/index.html
# Por ejemplo, cambia "v1.0" a "v2.0" o modifica el mensaje principal
git add src/main/resources/static/index.html
git commit -m "feat: actualizar texto de la aplicacion"
git push
# Jenkins detecta el push, construye la imagen y despliega la nueva versión automáticamente
```

## Troubleshooting

**SonarQube no responde:**
```bash
docker-compose logs -f sonarqube
```

**Trivy no encontrado en el agente:**
```bash
which trivy
trivy version
```

**Permisos de Docker en Jenkins:**
```bash
sudo usermod -aG docker jenkins
sudo systemctl restart jenkins
```
