# Usa a imagem oficial nginx:stable-alpine como base
FROM nginx:stable-alpine

# Removendo o arquivo de configuraÃ§Ã£o padrÃ£o para evitar conflitos
RUN rm -f /etc/nginx/conf.d/default.conf

# Certifique-se que o caminho de origem (containers/fzl-nginx/nginx-conf.d/my_app.conf)
COPY  nginx-conf.d /etc/nginx/conf.d

RUN rm /var/log/nginx/access.log && \
    rm /var/log/nginx/error.log  

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
