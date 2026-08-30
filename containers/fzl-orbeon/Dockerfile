# Orbeon Forms COMMUNITY EDITION (LGPL) — open-source forms platform.
#
# Orbeon publishes Docker images only for the commercial PE, so we build our
# own image from the CE distribution ZIP on GitHub releases. Orbeon 2025.1 is
# still javax.servlet, so it runs on Tomcat 9 (NOT Tomcat 10, which is Jakarta)
# and needs Java 11+ (we use 17). Form definitions + submitted data persist in
# PostgreSQL (fzl-postgresql), bootstrapped by docker-entrypoint-fzl.sh.
FROM tomcat:9-jre17-temurin

LABEL maintainer="wagnerdocri@gmail.com"

# unzip: explode the WAR · gettext-base: envsubst for the config templates ·
# postgresql-client: create/migrate Orbeon's DB at start · curl: fetch the CE dist.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        unzip gettext-base postgresql-client curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# PostgreSQL JDBC driver for Orbeon's relational persistence datasource.
ARG PG_JDBC_VERSION=42.7.4
ADD https://repo1.maven.org/maven2/org/postgresql/postgresql/${PG_JDBC_VERSION}/postgresql-${PG_JDBC_VERSION}.jar \
    ${CATALINA_HOME}/lib/postgresql.jar
RUN chmod 644 ${CATALINA_HOME}/lib/postgresql.jar

# Deploy Orbeon CE exploded at the /orbeon context. Downloaded at build time
# (not committed) to keep the 159 MB WAR out of the repo. Override the URL with
# --build-arg ORBEON_CE_URL=... to pin a different CE release.
ARG ORBEON_CE_URL=https://github.com/orbeon/orbeon-forms/releases/download/tag-release-2025.1-ce/orbeon-2025.1.202512310433-CE.zip
RUN rm -rf ${CATALINA_HOME}/webapps/* \
    && curl -fsSL "${ORBEON_CE_URL}" -o /tmp/orbeon-ce.zip \
    && unzip -q /tmp/orbeon-ce.zip -d /tmp/ce \
    && mkdir -p ${CATALINA_HOME}/webapps/orbeon \
    && unzip -q /tmp/ce/*/orbeon.war -d ${CATALINA_HOME}/webapps/orbeon \
    && rm -rf /tmp/orbeon-ce.zip /tmp/ce

# Persistence schema (applied once) + config templates (rendered from env at
# start) + entrypoint.
COPY postgresql-2025_1.sql        /opt/fzl/postgresql-2025_1.sql
COPY context.xml.template         /opt/fzl/context.xml.template
COPY properties-local.xml.template /opt/fzl/properties-local.xml.template
COPY docker-entrypoint-fzl.sh     /opt/fzl/docker-entrypoint-fzl.sh
RUN chmod +x /opt/fzl/docker-entrypoint-fzl.sh

# Orbeon is memory-hungry and uses AWT for PDF/image rendering (headless).
ENV CATALINA_OPTS="-Xms512m -Xmx1536m -Djava.awt.headless=true"

EXPOSE 8080
ENTRYPOINT ["/opt/fzl/docker-entrypoint-fzl.sh"]
CMD ["catalina.sh", "run"]
