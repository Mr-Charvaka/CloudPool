package com.cloudpool.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${cloudpool.gateway.url:http://localhost:8080}")
    private String gatewayUrl;

    @Bean
    public OpenAPI cloudpoolOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("CloudPool API")
                .description("AI-Native Backend-as-a-Service Platform\n\n" +
                    "Authentication: Use `X-API-KEY` header or `Authorization: Bearer <JWT>`.\n" +
                    "All endpoints under `/api/`, GraphQL at `/graphql`.")
                .version("0.1.0")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
            .servers(List.of(
                new Server().url(gatewayUrl).description("CloudPool Gateway")
            ));
    }
}