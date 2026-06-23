package com.cloudpool.gateway;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@Testcontainers
public class EndToEndFlowTest {

    // Set to true to enable this test. Disabled by default because it spins up the entire docker-compose
    // which takes time and requires the host to have docker available.
    private static final boolean ENABLE_E2E_TEST = false;

    @Container
    public static DockerComposeContainer<?> environment = new DockerComposeContainer<>(
            new File("../../../docker/docker-compose.yml")
    )
            .withExposedService("gateway", 8080, Wait.forHttp("/actuator/health").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(5)))
            .withLocalCompose(true);

    @BeforeAll
    public static void setUp() {
        if (!ENABLE_E2E_TEST) return;
        
        String gatewayHost = environment.getServiceHost("gateway", 8080);
        Integer gatewayPort = environment.getServicePort("gateway", 8080);
        RestAssured.baseURI = "http://" + gatewayHost;
        RestAssured.port = gatewayPort;
    }

    @Test
    public void testFullUserJourney() {
        if (!ENABLE_E2E_TEST) return;

        String username = "testuser_" + System.currentTimeMillis();
        String email = username + "@example.com";
        String password = "StrongPassword123!";

        // 1. Register User
        given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "email", email, "password", password))
        .when()
            .post("/auth/register")
        .then()
            .statusCode(200)
            .body("message", equalTo("User registered successfully"));

        // 2. Login to get JWT
        Response loginResponse = given()
            .contentType(ContentType.JSON)
            .body(Map.of("username", username, "password", password))
        .when()
            .post("/auth/login")
        .then()
            .statusCode(200)
            .extract().response();

        String token = loginResponse.jsonPath().getString("token");
        org.junit.jupiter.api.Assertions.assertNotNull(token);

        // 3. Create Project
        String projectName = "E2E_Test_Project";
        Response projectResponse = given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(Map.of("name", projectName, "description", "Test project created via E2E"))
        .when()
            .post("/compute/projects")
        .then()
            .statusCode(200)
            .extract().response();
            
        String projectId = projectResponse.jsonPath().getString("id");

        // 4. Create Table
        Response tableResponse = given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "projectId", projectId,
                "name", "employees",
                "fields", List.of(
                    Map.of("fieldName", "name", "fieldType", "VARCHAR", "isRequired", true),
                    Map.of("fieldName", "age", "fieldType", "INTEGER", "isRequired", false)
                )
            ))
        .when()
            .post("/data/tables")
        .then()
            .statusCode(200)
            .extract().response();

        String tableId = tableResponse.jsonPath().getString("id");

        // 5. Insert Record
        Response insertResponse = given()
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "name", "John Doe",
                "age", 30
            ))
        .when()
            .post("/data/tables/" + tableId + "/records")
        .then()
            .statusCode(200)
            .extract().response();

        String recordId = insertResponse.jsonPath().getString("id");

        // 6. Query Record
        given()
            .header("Authorization", "Bearer " + token)
        .when()
            .get("/data/tables/" + tableId + "/records")
        .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].name", equalTo("John Doe"))
            .body("[0].age", equalTo(30));
    }
}
