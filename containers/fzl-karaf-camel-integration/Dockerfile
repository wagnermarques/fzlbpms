FROM openjdk:21-jdk-slim

# Set environment variables
ENV KARAF_VERSION=4.4.7
ENV KARAF_HOME=/opt/karaf
ENV PATH=$KARAF_HOME/bin:$PATH
ENV DEBIAN_FRONTEND=noninteractive

# 1. Instala TODAS as depend�ncias e cria o usu�rio em uma �nica camada
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Ferramenta para troca de usu�rio (essencial para a solu��o)
    gosu \
    # Depend�ncias do Docker CLI
    apt-transport-https gnupg lsb-release \
    # Utilit�rios de sistema e desenvolvimento
    wget curl git sudo ca-certificates \
    python3 python3-pip python3-venv \
    ffmpeg build-essential make cmake clang \
    && rm -rf /var/lib/apt/lists/* \
    # Cria o usu�rio e o grupo de forma limpa
    && groupadd -r appuser && useradd -r -g appuser -d /home/appuser -s /bin/bash appuser \
    && mkdir -p /home/appuser

# 2. Instala o Docker CLI
RUN curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg \
    && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] https://download.docker.com/linux/debian $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null \
    && apt-get update && apt-get install -y --no-install-recommends docker-ce-cli \
    && rm -rf /var/lib/apt/lists/*

# 3. Adiciona o appuser ao grupo do docker
RUN if ! getent group docker >/dev/null; then groupadd -r docker; fi \
    && usermod -aG docker appuser

# 4. Baixa e extrai o Apache Karaf
RUN wget -q "https://archive.apache.org/dist/karaf/${KARAF_VERSION}/apache-karaf-${KARAF_VERSION}.tar.gz" -O /tmp/karaf.tar.gz \
    && tar xzf /tmp/karaf.tar.gz -C /opt \
    && mv "/opt/apache-karaf-${KARAF_VERSION}" "${KARAF_HOME}" \
    && rm /tmp/karaf.tar.gz

# 5. Copia os arquivos necess�rios e define as permiss�es
COPY --chown=appuser:appuser ./custom_karaf_etc/etc-from-4.4.7/ ${KARAF_HOME}/etc/
COPY entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/entrypoint.sh \
    && chown -R appuser:appuser /home/appuser

# Exp�e as portas do Karaf
EXPOSE 8101 8181 1099 44444

# Define o diret�rio de trabalho
WORKDIR /home/appuser

#COPY ./custom_karaf_etc/etc-from-4.4.7/ /opt/karaf/etc/

# 6. Define o entrypoint. O container iniciar� como root para executar este script.
ENTRYPOINT ["entrypoint.sh"]

# 7. Define o comando padr�o. Ser� passado para o entrypoint.sh
CMD ["/opt/karaf/bin/karaf", "run"]
