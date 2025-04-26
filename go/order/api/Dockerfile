FROM alpine:3.21

# Define the project name | 定义项目名称
ARG PROJECT=order
# Define the config file name | 定义配置文件名
ARG CONFIG_FILE=order.yaml
# Define the author | 定义作者
ARG AUTHOR="example@example.com"

LABEL org.opencontainers.image.authors=${AUTHOR}

WORKDIR /app
ENV PROJECT=${PROJECT}
ENV CONFIG_FILE=${CONFIG_FILE}

COPY ./${PROJECT}_api ./
COPY ./etc/${CONFIG_FILE} ./etc/

EXPOSE 8081

ENTRYPOINT ["./order_api", "-f", "etc/order.yaml"]