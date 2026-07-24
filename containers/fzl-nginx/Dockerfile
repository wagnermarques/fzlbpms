# --- Build stage: compile fzlbpmsadmin-web (Angular) into a static bundle ---
FROM node:22-alpine AS fzlbpmsadmin-web-build

WORKDIR /app

COPY src-projects/fzlbpmsadmin-web/package.json src-projects/fzlbpmsadmin-web/package-lock.json ./
RUN npm ci

COPY src-projects/fzlbpmsadmin-web/ ./
RUN npm run build

# --- Runtime stage ---
# Usa a imagem oficial nginx:stable-alpine como base
FROM nginx:stable-alpine

# Removendo o arquivo de configuraÃ§Ã£o padrÃ£o para evitar conflitos
RUN rm -f /etc/nginx/conf.d/default.conf


# Certifique-se que o caminho de origem (containers/fzl-nginx/nginx-conf.d/my_app.conf)
COPY containers/fzl-nginx/nginx-conf.d /etc/nginx/conf.d

# Static fzlbpmsadmin-web build, served at /fzlbpmsadmin/ (see 00-catchall.conf)
COPY --from=fzlbpmsadmin-web-build /app/dist/fzlbpmsadmin-web/browser /var/www/fzlbpmsadmin

RUN rm /var/log/nginx/access.log && \
    rm /var/log/nginx/error.log

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
