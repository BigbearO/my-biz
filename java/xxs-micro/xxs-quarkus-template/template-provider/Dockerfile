# 基于 Quarkus 构建产物 quarkus-app 的 JVM 运行镜像（推荐生产使用）
FROM eclipse-temurin:25-jre

WORKDIR /work
# Quarkus 依赖分层，按变更频率拷贝以提升镜像缓存命中率
COPY target/quarkus-app/lib/ /work/lib/
COPY target/quarkus-app/app/ /work/app/
COPY target/quarkus-app/quarkus/ /work/quarkus/
COPY target/quarkus-app/*.jar /work/

# HTTP(8080) + gRPC(9090)
EXPOSE 8080 9090

ENTRYPOINT ["java", "-jar", "/work/quarkus-run.jar"]
