FROM eclipse-temurin:17-jre-noble
WORKDIR /app
COPY target/cloudpool-gateway-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
