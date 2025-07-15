# Usa a imagem oficial nginx:stable-alpine como base
FROM nginx:stable-alpine

COPY nginx.conf /etc/nginx/nginx.conf

# remove a configuração padrão do Nginx pra evitar conflitos
RUN rm /etc/nginx/conf.d/default.conf

# COPY html/ /usr/share/nginx/html/
# por enquanto vamos montar com bind na hora de subir o container

# Expõe a porta 80, que é a porta padrão para o tráfego HTTP
EXPOSE 80

# Comando padrão para iniciar o Nginx quando o contêiner é executado
# Este comando já é o padrão na imagem base, mas é bom ter explícito.
CMD ["nginx", "-g", "daemon off;"]
