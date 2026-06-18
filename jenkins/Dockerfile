# Jenkins image with the Docker CLI added, so the DAST stage can run ZAP via the
# mounted /var/run/docker.sock. Plugins/jobs/credentials live in the jenkins_home
# volume (installed at runtime), so rebuilding this image keeps all of that.
FROM jenkins/jenkins:lts-jdk17
USER root
ARG DOCKER_VER=27.3.1
RUN ARCH=$(uname -m) && \
    curl -fsSL "https://download.docker.com/linux/static/stable/${ARCH}/docker-${DOCKER_VER}.tgz" -o /tmp/d.tgz && \
    tar -xzf /tmp/d.tgz -C /tmp && \
    cp /tmp/docker/docker /usr/local/bin/docker && \
    chmod +x /usr/local/bin/docker && \
    rm -rf /tmp/d.tgz /tmp/docker
# compose overrides to user root at runtime; default image user is fine here.
USER jenkins
