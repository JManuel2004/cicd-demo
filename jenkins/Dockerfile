FROM jenkins/jenkins:lts

USER root

# Docker CLI
RUN apt-get update && \
    apt-get install -y ca-certificates curl gnupg2 lsb-release && \
    install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg -o /etc/apt/keyrings/docker.asc && \
    chmod a+r /etc/apt/keyrings/docker.asc && \
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
        > /etc/apt/sources.list.d/docker.list && \
    apt-get update && \
    apt-get install -y docker-ce-cli

# Maven
RUN apt-get install -y maven

# Trivy
RUN curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | \
    sh -s -- -b /usr/local/bin

# Permisos para usar el socket de Docker
# El GID real del socket se inyecta al correr el contenedor con --group-add
RUN groupadd -f docker && usermod -aG docker jenkins && chmod 777 /var/run || true

USER jenkins
