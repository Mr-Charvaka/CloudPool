FROM eclipse-temurin:17-jre-noble
WORKDIR /app
COPY target/cloudpool-auth-*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
