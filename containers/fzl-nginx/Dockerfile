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
COPY containers/fzl-nginx/nginx-shared /etc/nginx/shared
COPY containers/fzl-nginx/docker-entrypoint.d/ /docker-entrypoint.d/
RUN chmod +x /docker-entrypoint.d/*.sh

# Static fzlbpmsadmin-web build, served at /fzlbpmsadmin/ (see 00-catchall.conf)
COPY --from=fzlbpmsadmin-web-build /app/dist/fzlbpmsadmin-web/browser /var/www/fzlbpmsadmin

RUN rm /var/log/nginx/access.log && \
    rm /var/log/nginx/error.log

# Self-signed fallback for https://fzlbpms.local (01-tls.conf) so nginx
# always has a valid cert to load, even before ansible/setup-project.yml
# (--tags certs) has issued a real mkcert-trusted one — the entrypoint script
# overwrites this at container start if a real one is bind-mounted in.
# Browsers will show a trust warning until then.
RUN apk add --no-cache openssl \
    && mkdir -p /etc/nginx/certs \
    && openssl req -x509 -newkey rsa:2048 -nodes -days 3650 \
        -keyout /etc/nginx/certs/fzlbpms.local-key.pem \
        -out /etc/nginx/certs/fzlbpms.local.pem \
        -subj "/CN=fzlbpms.local"

EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
