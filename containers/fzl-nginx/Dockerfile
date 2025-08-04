# Usa a imagem oficial nginx:stable-alpine como base
FROM nginx:stable-alpine

# Removendo o arquivo de configuração padrão para evitar conflitos
RUN rm -f /etc/nginx/conf.d/default.conf

# Certifique-se que o caminho de origem (containers/fzl-nginx/nginx-conf.d/my_app.conf)
COPY  nginx-conf.d/fzlbpms.conf /etc/nginx/conf.d/fzlbpms.conf

RUN rm /var/log/nginx/access.log && \
    rm /var/log/nginx/error.log  

# Expõe a porta 80, que é a porta padrão para o tráfego HTTP
EXPOSE 80

# Comando padrão para iniciar o Nginx quando o contêiner é executado
# Este comando já é o padrão na imagem base, mas é bom ter explícito.
CMD ["nginx", "-g", "daemon off;"]
