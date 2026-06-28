package com.cloudpool.handler;

import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class GraphQLErrorHandler implements DataFetcherExceptionHandler {

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {
        
        Throwable exception = handlerParameters.getException();
        
        // Log the error
        log.error("GraphQL error in field: {}", 
            handlerParameters.getPath(), exception);
        
        // Don't expose internal errors to clients
        GraphQLError error = GraphQLError.newError()
            .message("An error occurred while processing your request")
            .build();
        
        return CompletableFuture.completedFuture(
            DataFetcherExceptionHandlerResult.newResult().error(error).build()
        );
    }
}
