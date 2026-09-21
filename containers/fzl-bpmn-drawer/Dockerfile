# Build stage: download bpmn-js standalone assets
FROM alpine:latest AS assets-stage
WORKDIR /assets
RUN apk add --no-cache curl

# Download bpmn-js production bundle and assets (pinned version 17.11.1 matching integra-bpmn)
RUN mkdir -p css js css/bpmn-font/css css/bpmn-font/font && \
    curl -fsSL https://cdn.jsdelivr.net/npm/bpmn-js@17.11.1/dist/bpmn-modeler.production.min.js -o js/bpmn-modeler.production.min.js && \
    curl -fsSL https://cdn.jsdelivr.net/npm/bpmn-js@17.11.1/dist/assets/diagram-js.css -o css/diagram-js.css && \
    curl -fsSL https://cdn.jsdelivr.net/npm/bpmn-js@17.11.1/dist/assets/bpmn-js.css -o css/bpmn-js.css && \
    curl -fsSL https://cdn.jsdelivr.net/npm/bpmn-js@17.11.1/dist/assets/bpmn-font/css/bpmn-embedded.css -o css/bpmn-font/css/bpmn-embedded.css

# Production stage: serve static files via nginx
FROM nginx:stable-alpine AS production-stage

# Copy downloaded bpmn-js assets
COPY --from=assets-stage /assets /usr/share/nginx/html/

# Copy custom fzl-bpmn-drawer application files
COPY src/ /usr/share/nginx/html/

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
