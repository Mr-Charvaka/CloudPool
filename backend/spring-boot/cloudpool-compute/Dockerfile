FROM eclipse-temurin:17-jre-noble
WORKDIR /app
COPY target/cloudpool-compute-*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
