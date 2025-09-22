FROM postgres:16-alpine

# Define as variáveis de ambiente para localização
# LANG e LC_ALL são importantes para definir o locale do banco de dados para Português do Brasil
ENV LANG pt_BR.UTF-8
ENV LC_ALL pt_BR.UTF-8

# Instala o pacote postgresql-contrib e as dependências do PostGIS
# 'build-base' e 'proj-dev', 'geos-dev', 'gdal-dev' são necessárias para compilar o PostGIS
# 'postgis' é o pacote que contém a extensão PostGIS
RUN apk add --no-cache postgresql-contrib postgis \
    build-base \
    proj-dev \
    geos-dev \
    gdal-dev \
    # Limpa o cache para reduzir o tamanho da imagem
    && rm -rf /var/cache/apk/*

# ou ativar extensões automaticamente na primeira inicialização.
# Se você tiver um arquivo chamado 'init.sql' ou 'init.sh' na mesma pasta,
# ele será executado automaticamente pelo PostgreSQL.
# Exemplo: COPY init.sql /docker-entrypoint-initdb.d/

# Expõe a porta padrão do PostgreSQL
EXPOSE 5432

# O comando padrão para iniciar o PostgreSQL já está definido na imagem base
# CMD ["postgres"]
