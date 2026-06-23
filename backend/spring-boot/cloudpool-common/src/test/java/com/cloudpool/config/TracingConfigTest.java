package com.cloudpool.config;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TracingConfigTest {

    @Mock private Tracer tracer;
    @Mock private Span span;
    @Mock private TraceContext traceContext;
    @Mock private FilterChain filterChain;

    private Filter traceIdFilter;

    @BeforeEach
    void setUp() {
        TracingConfig config = new TracingConfig();
        traceIdFilter = config.traceIdFilter(tracer);
        MDC.clear();
    }

    @Test
    @DisplayName("Should set X-Trace-Id header when span is active")
    void testTraceIdFilterAddsHeaders() throws Exception {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("abc123trace");
        when(traceContext.spanId()).thenReturn("def456span");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = spy(new MockHttpServletResponse());

        traceIdFilter.doFilter(request, response, filterChain);

        verify(response).setHeader("X-Trace-Id", "abc123trace");
    }

    @Test
    @DisplayName("Should clean MDC after filter execution")
    void testTraceIdFilterCleansMDC() throws Exception {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-123");
        when(traceContext.spanId()).thenReturn("span-456");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, filterChain);

        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
    }

    @Test
    @DisplayName("Should clean MDC even when filter chain throws")
    void testTraceIdFilterCleansMDCOOnException() throws Exception {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-xyz");
        when(traceContext.spanId()).thenReturn("span-xyz");
        doThrow(new RuntimeException("chain failed")).when(filterChain).doFilter(any(), any());

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(RuntimeException.class,
                () -> traceIdFilter.doFilter(request, response, filterChain));

        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
    }

    @Test
    @DisplayName("Should not set header or MDC when no span is active")
    void testTraceIdFilterNoSpan() throws Exception {
        when(tracer.currentSpan()).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = spy(new MockHttpServletResponse());

        traceIdFilter.doFilter(request, response, filterChain);

        verify(response, never()).setHeader(anyString(), anyString());
        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Should set MDC during filter chain execution")
    void testTraceIdFilterSetsMDCDuringChain() throws Exception {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-123");
        when(traceContext.spanId()).thenReturn("span-456");

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        traceIdFilter.doFilter(request, response, filterChain);

        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
    }

    @Test
    @DisplayName("Should not set header on non-HttpServletResponse")
    void testTraceIdFilterNonHttpResponse() throws Exception {
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace-123");
        when(traceContext.spanId()).thenReturn("span-456");

        MockHttpServletRequest request = new MockHttpServletRequest();
        jakarta.servlet.ServletResponse mockResponse = mock(jakarta.servlet.ServletResponse.class);

        traceIdFilter.doFilter(request, mockResponse, filterChain);

        assertNull(MDC.get("traceId"));
        assertNull(MDC.get("spanId"));
        verify(filterChain).doFilter(request, mockResponse);
    }
}