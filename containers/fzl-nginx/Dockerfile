# Usa a imagem oficial nginx:stable-alpine como base
FROM nginx:stable-alpine

# Removendo o arquivo de configura��o padr�o para evitar conflitos
RUN rm -f /etc/nginx/conf.d/default.conf

# Certifique-se que o caminho de origem (containers/fzl-nginx/nginx-conf.d/my_app.conf)
COPY  nginx-conf.d/fzlbpms.conf /etc/nginx/conf.d/fzlbpms.conf

# Exp�e a porta 80, que � a porta padr�o para o tr�fego HTTP
EXPOSE 80

# Comando padr�o para iniciar o Nginx quando o cont�iner � executado
# Este comando j� � o padr�o na imagem base, mas � bom ter expl�cito.
CMD ["nginx", "-g", "daemon off;"]
