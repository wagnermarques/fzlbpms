FROM node:24-alpine as development

# Define o diret�rio de trabalho dentro do cont�iner
WORKDIR /app

#COPY package*.json ./
RUN apk add --no-cache git

RUN npm install -g @angular/cli

#COPY . .

#RUN npm install

EXPOSE 4200

CMD ["/bin/sh", "-c", "npm install && ng serve --host 0.0.0.0"]

