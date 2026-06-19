package com.cloudpool.compute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.cloudpool.compute", "com.cloudpool.service", "com.cloudpool.controller"})
@EnableScheduling
public class ComputeApplication {
    public static void main(String[] args) {
        System.setProperty("server.port", "8081");
        SpringApplication.run(ComputeApplication.class, args);
    }
}
