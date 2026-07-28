# Build stage
FROM node:lts-alpine AS build-stage
WORKDIR /app
RUN apk add --no-cache git
# Use a specific tag or branch if needed, here we use the latest modeler example
RUN git clone --depth 1 https://github.com/bpmn-io/bpmn-js-examples.git .
WORKDIR /app/modeler
RUN npm install
RUN npm run all

# Production stage
FROM nginx:stable-alpine AS production-stage
COPY --from=build-stage /app/modeler/public /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
