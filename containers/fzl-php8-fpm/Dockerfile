FROM php:8.1-fpm

RUN docker-php-ext-install pdo pdo_mysql mysqli

# Instala dependências para o Composer (curl e unzip são comuns)
RUN apt-get update && apt-get install -y \
    curl \
    unzip \
    git \
    libpng-dev \ 
    libjpeg-dev \ 
    libfreetype6-dev \
    # Adicionado para Xdebug
    gnupg \
    dirmngr

RUN rm -rf /var/lib/apt/lists/*

# Instala as extensões do PHP necessárias
RUN docker-php-ext-configure gd --with-freetype --with-jpeg
RUN docker-php-ext-install gd

# Instala a extensão intl
RUN apt-get update && apt-get install -y libicu-dev \
    && docker-php-ext-install intl

# Instala a extensão zip
RUN apt-get update && apt-get install -y libzip-dev \
    && docker-php-ext-install zip

# Instala a extensão bcmath
RUN apt-get update && apt-get install -y libxml2-dev \
    && docker-php-ext-install bcmath    


#-----------------------------------------------
# ADICIONAR ESTAS LINHAS PARA INSTALAR E HABILITAR O XDEBUG
# -----------------------------------------------
RUN pecl install xdebug && docker-php-ext-enable xdebug    
# Copia o arquivo de configuração do Xdebug para o diretório de conf do PHP
COPY 90-xdebug.ini /usr/local/etc/php/conf.d/90-xdebug.ini


# Instala o Composer globalmente
# Baixa o instalador do Composer
COPY --from=composer:latest /usr/bin/composer /usr/local/bin/composer

# Define o diretório de trabalho padrão dentro do container
WORKDIR /var/www/html

# Opcional: copiar o composer.json e composer.lock aqui
# e rodar composer install para otimizar o cache da camada do Docker
#COPY composer.json composer.lock ./
#RUN composer install --no-dev --no-autoloader --no-scripts --optimize-autoloader


# Create assets directory and set permissions
RUN mkdir -p /var/www/html/web/assets
RUN chown -R www-data:www-data /var/www/html
RUN chmod -R 755 /var/www/html/web/assets

# Expondo a porta 9000 (padrão do PHP-FPM)
EXPOSE 9000

# Comando padrão para iniciar o PHP-FPM (já é o padrão da imagem php-fpm)
CMD ["php-fpm"]
