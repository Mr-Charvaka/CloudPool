package com.cloudpool.controller;

import com.cloudpool.handler.CloudTunnelHandler;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudTunnelControllerTest {

    @Mock private CloudTunnelHandler cloudTunnelHandler;
    @Mock private HttpServletRequest request;

    @InjectMocks
    private CloudTunnelController controller;

    private CloudTunnelHandler.TunnelResponse successResponse;

    @BeforeEach
    void setUp() {
        successResponse = new CloudTunnelHandler.TunnelResponse();
        successResponse.setStatusCode(200);
        successResponse.setBody("response body");
    }

    @Test
    void proxy_successfulRequest_shouldReturnTunnelResponse() throws Exception {
        when(request.getRequestURI()).thenReturn("/tunnels/tun-1/api/data");
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(null);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        when(cloudTunnelHandler.forwardHttpRequest(eq("tun-1"), eq("GET"), eq("/api/data"), anyString(), eq("")))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        var response = controller.proxy("tun-1", request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("response body", response.getBody());
        assertTrue(response.getHeaders().containsKey("X-CloudPool-Tunnel"));
    }

    @Test
    void proxy_withQueryString_shouldIncludeInUri() throws Exception {
        when(request.getRequestURI()).thenReturn("/tunnels/tun-1/search");
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn("q=test&page=1");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        when(cloudTunnelHandler.forwardHttpRequest(eq("tun-1"), eq("GET"),
                eq("/search?q=test&page=1"), anyString(), eq("")))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        var response = controller.proxy("tun-1", request);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void proxy_tunnelRoot_shouldResolveToSlash() throws Exception {
        when(request.getRequestURI()).thenReturn("/tunnels/tun-1");
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(null);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        when(cloudTunnelHandler.forwardHttpRequest(eq("tun-1"), eq("GET"),
                eq("/"), anyString(), eq("")))
                .thenReturn(CompletableFuture.completedFuture(successResponse));

        var response = controller.proxy("tun-1", request);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void proxy_timeout_shouldReturn502() throws Exception {
        when(request.getRequestURI()).thenReturn("/tunnels/tun-1/timeout");
        when(request.getMethod()).thenReturn("GET");
        when(request.getQueryString()).thenReturn(null);
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader("")));
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        CompletableFuture<CloudTunnelHandler.TunnelResponse> timeoutFuture = new CompletableFuture<>();
        when(cloudTunnelHandler.forwardHttpRequest(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(timeoutFuture);

        var response = controller.proxy("tun-1", request);
        assertTrue(response.getStatusCode().is5xxServerError());
    }
}
