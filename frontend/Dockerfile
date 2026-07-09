ARG DOCKER_REGISTRY

FROM ${DOCKER_REGISTRY}/node:18-alpine

WORKDIR /app

COPY package.json package-lock.json* ./

RUN npm config set registry https://mirrors.huaweicloud.com/repository/npm/ && \
    npm ci

COPY . .

EXPOSE 5173

CMD ["npm", "run", "dev", "--", "--host"]