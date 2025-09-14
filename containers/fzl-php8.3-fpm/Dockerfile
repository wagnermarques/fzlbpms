FROM php:8.3-fpm

LABEL maintainer="wagnerdocri@gmail.com"

# 1. Instala pacotes do sistema (lib*-dev sao dependencias de compilacao de extensoes PHP)
#    Inclui dependencias para Composer, Git, e todas as extensoes PHP comuns para Moodle
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Ferramentas gerais
    curl \
    unzip \
    git \
    gnupg \
    dirmngr \
    # Dependencias para extensoes PHP
    libpng-dev \
    libjpeg-dev \
    libfreetype6-dev \
    libzip-dev \
    libicu-dev \
    libxml2-dev \
    libonig-dev \
    libcurl4-openssl-dev \
    libsodium-dev \
    libmagickwand-dev \
    libpq-dev \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# 2. Instala e configura as extensoes PHP
RUN docker-php-ext-configure gd --with-freetype --with-jpeg \
    # Compila e instala as extensoes com a otimização de múltiplos núcleos (-j$(nproc))
    && docker-php-ext-install -j$(nproc) \
    pdo \
    pdo_mysql \
    mysqli \
    # Extensoes especificas para PostgreSQL
    pdo_pgsql \
    pgsql \
    # Extensoes comuns do Moodle
    gd \
    xml \
    intl \
    mbstring \
    zip \
    curl \
    soap \
    opcache \
    sodium \
    bcmath \
    exif 

RUN pecl install imagick && docker-php-ext-enable imagick

# 3. Instala o Composer globalmente
# Baixa o instalador do Composer
COPY --from=composer:latest /usr/bin/composer /usr/local/bin/composer

# 4. Configuracao do Xdebug (seus arquivos .ini)
RUN pecl install xdebug && docker-php-ext-enable xdebug
COPY 90-xdebug.ini /usr/local/etc/php/conf.d/90-xdebug.ini
COPY custom-php.ini /usr/local/etc/php/conf.d/custom-php.ini

# 5. Define o diretório de trabalho padrão dentro do container (para php-fpm)
WORKDIR /var/www/html

# 6. Permissoes de diretorios (importante para volume mounts)
# Estas permissoes garantem que o usuario 'www-data' (padrao do php-fpm)
# tenha acesso aos arquivos e possa criar/escrever onde necessario.
# Para volumes, as permissoes do HOST afetam, mas estas sao boas como base.
RUN chown -R www-data:www-data /var/www \
    && chmod -R 755 /var/www

# 7. Opcional: Criar diretório de assets e garantir permissões
# Se este diretório for sempre gerenciado por um volume no docker-compose,
# essa parte pode ser redundante ou apenas para garantir a existência na imagem.
# Se o 'assets' for o único diretório que precisa ser gravável pela aplicação
# (ex: para upload de arquivos), você pode ajustar as permissões aqui.
# RUN mkdir -p /var/www/html/web/assets \
#    && chown -R www-data:www-data /var/www/html/web/assets \
#    && chmod -R 775 /var/www/html/web/assets

# Expondo a porta 9000 (padrao do PHP-FPM)
EXPOSE 9000


COPY docker-entrypoint.sh /usr/local/bin/
COPY utils.sh /usr/local/bin/

RUN chmod +x /usr/local/bin/docker-entrypoint.sh
ENTRYPOINT ["docker-entrypoint.sh"]

# Comando padrão para iniciar o PHP-FPM (já é o padrão da imagem php-fpm)
CMD ["php-fpm"]
