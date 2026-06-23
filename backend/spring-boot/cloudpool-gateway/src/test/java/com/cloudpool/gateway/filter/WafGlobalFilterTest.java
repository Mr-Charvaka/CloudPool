package com.cloudpool.gateway.filter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class WafGlobalFilterTest {

    @Mock private GatewayFilterChain chain;

    private final WafGlobalFilter filter = new WafGlobalFilter();

    @Test
    void filter_normalRequest_shouldProceed() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/health").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_xssInUri_shouldBlock() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/search?q=<script>alert(1)</script>").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_xssEncodedInUri_shouldBlock() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api?q=%3Cscript%3Ealert(1)%3C/script%3E").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void filter_javascriptInUri_shouldBlock() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api?redirect=javascript:void(0)").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_sqliUnionSelect_shouldBlock() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users?q=union select * from passwords").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_sqliSelectFrom_shouldBlock() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/users?q=select * from users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_sqliDropTable_shouldBlock() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api?q=drop table users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_xssInHeader_shouldBlock() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header("X-Custom", "<script>alert('xss')</script>")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain, never()).filter(any());
    }

    @Test
    void filter_normalHeader_shouldProceed() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .header("Authorization", "Bearer token123")
                        .header("Content-Type", "application/json")
                        .build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_innocentPathWithSelect_shouldProceed() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/select-from-users").build());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    @Test
    void filter_getOrder_shouldBeNegative() {
        assertTrue(filter.getOrder() < 0);
    }
}

