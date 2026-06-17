FROM eclipse-temurin:17-jre-noble
WORKDIR /app
COPY target/cloudpool-network-*.jar app.jar
COPY src/main/resources/libcloudpool_rust.so /usr/local/lib/libcloudpool_rust.so
ENV LD_LIBRARY_PATH=/usr/local/lib
EXPOSE 8085
ENTRYPOINT ["java", "-Djava.library.path=/usr/local/lib", "-jar", "app.jar"]
