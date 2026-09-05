# 多阶段构建：Maven 编译 + 精简 JRE 运行
# 运行用户固定为 10001（与 k8s securityContext.runAsUser 对齐）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 国内网络直连 Maven Central 慢且不稳，用阿里云镜像（settings.xml 仅镜像 central）
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .
# 先离线拉取依赖，最大化缓存命中
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN useradd -r -u 10001 -g root app && chown -R app:root /app
COPY --from=build /app/target/admin-backend-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8080
# MaxRAMPercentage 让堆随容器内存 limits 自适应（k8s 默认 1Gi）
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
