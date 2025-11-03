FROM node:24-alpine as development
WORKDIR /app
RUN apk add --no-cache git
RUN npm install -g @angular/cli
EXPOSE 4200
# CMD is removed. The server start is handled by postCreateCommand in devcontainer.json.
