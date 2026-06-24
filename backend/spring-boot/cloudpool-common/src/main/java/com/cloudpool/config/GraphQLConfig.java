package com.cloudpool.config;

import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Configuration
public class GraphQLConfig {

    @Bean
    public MaxQueryDepthInstrumentation maxQueryDepthInstrumentation() {
        return new MaxQueryDepthInstrumentation(15);
    }

    @Bean
    public MaxQueryComplexityInstrumentation maxQueryComplexityInstrumentation() {
        return new MaxQueryComplexityInstrumentation(200);
    }

    @Bean
    public PreparsedDocumentProvider preparsedDocumentProvider() {
        return new PreparsedDocumentProvider() {
            private final ConcurrentHashMap<String, PreparsedDocumentEntry> cache = new ConcurrentHashMap<>();

            @Override
            public PreparsedDocumentEntry getDocument(Function<String, PreparsedDocumentEntry> computeFunction, String query) {
                return cache.computeIfAbsent(query, computeFunction);
            }
        };
    }

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiring -> wiring
                .scalar(ExtendedScalars.DateTime)
                .scalar(ExtendedScalars.Json)
                .scalar(ExtendedScalars.PositiveInt);
    }
}