# 贝尔实验室 Spring 官方推荐镜像 JDK下载地址 https://bell-sw.com/pages/downloads/
FROM bellsoft/liberica-openjdk-rocky:21.0.12-cds

LABEL maintainer="goodfish"

RUN mkdir -p /ruoyi/goodfish-item-service/logs \
    /ruoyi/skywalking/agent

WORKDIR /ruoyi/goodfish-item-service

ENV SERVER_PORT=9302 LANG=C.UTF-8 LC_ALL=C.UTF-8 JAVA_OPTS=""

EXPOSE ${SERVER_PORT}

ADD ./target/goodfish-item-service.jar ./app.jar

SHELL ["/bin/bash", "-c"]

ENTRYPOINT java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${SERVER_PORT} \
           #-Dskywalking.agent.service_name=goodfish-item-service \
           #-javaagent:/ruoyi/skywalking/agent/skywalking-agent.jar \
           -XX:+HeapDumpOnOutOfMemoryError -XX:+UseZGC ${JAVA_OPTS} \
           -jar app.jar
