FROM postgres:16-alpine

# Define as vari�veis de ambiente para localiza��o
# LANG e LC_ALL s�o importantes para definir o locale do banco de dados para Portugu�s do Brasil
ENV LANG pt_BR.UTF-8
ENV LC_ALL pt_BR.UTF-8

# Instala o pacote postgresql-contrib e as depend�ncias do PostGIS
# 'build-base' e 'proj-dev', 'geos-dev', 'gdal-dev' s�o necess�rias para compilar o PostGIS
# 'postgis' � o pacote que cont�m a extens�o PostGIS
RUN apk add --no-cache postgresql-contrib postgis \
    build-base \
    proj-dev \
    geos-dev \
    gdal-dev \
    # Limpa o cache para reduzir o tamanho da imagem
    && rm -rf /var/cache/apk/*

# ou ativar extens�es automaticamente na primeira inicializa��o.
# Se voc� tiver um arquivo chamado 'init.sql' ou 'init.sh' na mesma pasta,
# ele ser� executado automaticamente pelo PostgreSQL.
# Exemplo: COPY init.sql /docker-entrypoint-initdb.d/

# Exp�e a porta padr�o do PostgreSQL
EXPOSE 5432

# O comando padr�o para iniciar o PostgreSQL j� est� definido na imagem base
# CMD ["postgres"]
