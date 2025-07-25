FROM php:8.3-fpm

LABEL maintainer="wagnerdocri@gmail.com"

# 1. Instala pacotes do sistema (lib*-dev s�o depend�ncias de compilacao de extensoes PHP)
#    Inclui depend�ncias para Composer, Git, e todas as extens�es PHP comuns para Moodle
RUN apt-get update && apt-get install -y --no-install-recommends \
    # Ferramentas gerais
    curl \
    unzip \
    git \
    gnupg \
    dirmngr \
    # Depend�ncias para extens�es PHP
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

# 2. Instala e configura as extens�es PHP
RUN docker-php-ext-configure gd --with-freetype --with-jpeg \
    # Compila e instala as extens�es com a otimiza��o de m�ltiplos n�cleos (-j$(nproc))
    && docker-php-ext-install -j$(nproc) \
    pdo \
    pdo_mysql \
    mysqli \
    # Extens�es espec�ficas para PostgreSQL
    pdo_pgsql \
    pgsql \
    # Extens�es comuns do Moodle
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
    # Instala a extens�o Imagick via PECL
    && pecl install imagick \
    && docker-php-ext-enable imagick

# 3. Instala o Composer globalmente
# Baixa o instalador do Composer
COPY --from=composer:latest /usr/bin/composer /usr/local/bin/composer

# 4. Configura��o do Xdebug (seus arquivos .ini)
RUN pecl install xdebug && docker-php-ext-enable xdebug
COPY 90-xdebug.ini /usr/local/etc/php/conf.d/90-xdebug.ini
COPY custom-php.ini /usr/local/etc/php/conf.d/custom-php.ini

# 5. Define o diret�rio de trabalho padr�o dentro do container (para php-fpm)
WORKDIR /var/www/html

# 6. Permiss�es de diret�rios (importante para volume mounts)
# Estas permiss�es garantem que o usu�rio 'www-data' (padr�o do php-fpm)
# tenha acesso aos arquivos e possa criar/escrever onde necess�rio.
# Para volumes, as permiss�es do HOST afetam, mas estas s�o boas como base.
RUN chown -R www-data:www-data /var/www \
    && chmod -R 755 /var/www

# 7. Opcional: Criar diret�rio de assets e garantir permiss�es
# Se este diret�rio for sempre gerenciado por um volume no docker-compose,
# essa parte pode ser redundante ou apenas para garantir a exist�ncia na imagem.
# Se o 'assets' for o �nico diret�rio que precisa ser grav�vel pela aplica��o
# (ex: para upload de arquivos), voc� pode ajustar as permiss�es aqui.
# RUN mkdir -p /var/www/html/web/assets \
#    && chown -R www-data:www-data /var/www/html/web/assets \
#    && chmod -R 775 /var/www/html/web/assets

# Expondo a porta 9000 (padr�o do PHP-FPM)
EXPOSE 9000

# Comando padr�o para iniciar o PHP-FPM (j� � o padr�o da imagem php-fpm)
CMD ["php-fpm"]
