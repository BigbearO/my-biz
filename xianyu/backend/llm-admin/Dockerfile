# 构建指定服务:docker build --build-arg SERVICE_NAME=admin -t admin:dev .
ARG SERVICE_NAME=admin

FROM golang:1.26-alpine AS builder
ARG SERVICE_NAME
WORKDIR /app
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && apk add --no-cache git
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -ldflags "-s -w" -o /app/bin/server ./app/${SERVICE_NAME}/service/cmd/server

FROM alpine:3.20
ARG SERVICE_NAME
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories && apk add --no-cache tzdata ca-certificates && cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && echo "Asia/Shanghai" > /etc/timezone
COPY --from=builder /app/bin/server /app/bin/server
COPY --from=builder /app/app/${SERVICE_NAME}/service/configs /app/configs
WORKDIR /app
EXPOSE 8000 9000
USER nobody
CMD ["/app/bin/server", "-conf", "/app/configs"]
