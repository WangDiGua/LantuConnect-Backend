FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/lantu-connect-*.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"
# 单文件配置：默认生产向；本地开发构建镜像时可加 ENV SPRING_PROFILES_ACTIVE=dev

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
