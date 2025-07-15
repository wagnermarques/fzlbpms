FROM openjdk:21-jdk-slim

# Set environment variables
ENV KARAF_VERSION=4.4.7
ENV KARAF_HOME=/opt/karaf
ENV PATH=$KARAF_HOME/bin:$PATH
ENV DEBIAN_FRONTEND=noninteractive

# Create appuser and install base packages
RUN groupadd -r appuser && useradd -r -g appuser appuser \
    && mkdir -p /home/appuser && chown appuser:appuser /home/appuser

# Install necessary packages in a single layer
RUN apt-get update && apt-get install -y \
    # Python and development tools
    python3 \
    python3-pip \
    python3-venv \
    # Media processing
    ffmpeg \
    # Development tools
    gcc \
    g++ \
    make \
    cmake \
    clang \
    build-essential \
    # System utilities
    wget \
    curl \
    git \
    sudo \
    ca-certificates \
    # Docker CLI dependencies
    apt-transport-https \
    gnupg \
    lsb-release \
    && rm -rf /var/lib/apt/lists/*

# Install Docker CLI
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && apt-get update \
    && apt-get install -y docker-ce-cli \
    && rm -rf /var/lib/apt/lists/*

# Download and extract Apache Karaf
RUN wget https://archive.apache.org/dist/karaf/${KARAF_VERSION}/apache-karaf-${KARAF_VERSION}.tar.gz \
    && tar xzf apache-karaf-${KARAF_VERSION}.tar.gz -C /opt \
    && mv /opt/apache-karaf-${KARAF_VERSION} $KARAF_HOME \
    && rm apache-karaf-${KARAF_VERSION}.tar.gz \
    && chown -R appuser:appuser $KARAF_HOME


# Configure docker group safely
RUN if ! getent group docker >/dev/null; then \
        groupadd -r docker; \
    fi \
    && usermod -aG docker appuser

# Expose Karaf ports
EXPOSE 8101 8181 1099 44444

# Create a startup script
RUN echo '#!/bin/bash' > /home/appuser/start.sh \
    && echo 'if [ "$1" = "karaf" ]; then' >> /home/appuser/start.sh \
    && echo '    exec $KARAF_HOME/bin/karaf run' >> /home/appuser/start.sh \
    && echo 'else' >> /home/appuser/start.sh \
    && echo '    exec "$@"' >> /home/appuser/start.sh \
    && echo 'fi' >> /home/appuser/start.sh \
    && chmod +x /home/appuser/start.sh \
    && chown appuser:appuser /home/appuser/start.sh

COPY --chown=appuser:appuser ./custom_karaf_etc/etc-from-4.4.7 /opt/karaf/etc

# Set entrypoint to our startup script
USER appuser
WORKDIR /home/appuser
ENTRYPOINT ["/home/appuser/start.sh"]

# Default to bash for interactive use
CMD ["/bin/bash"]
