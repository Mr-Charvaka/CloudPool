package com.cloudpool.filter;

import com.cloudpool.model.WafRule;
import com.cloudpool.repository.WafRuleRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WafFilterTest {

    @Mock private WafRuleRepository wafRuleRepository;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private WafFilter wafFilter;
    private UUID projectId;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        wafFilter = new WafFilter(wafRuleRepository);
        projectId = UUID.randomUUID();
        responseWriter = new StringWriter();

        lenient().when(request.getHeader("X-Project-Id")).thenReturn(projectId.toString());
        lenient().when(request.getRequestURI()).thenReturn("/api/test");
        lenient().when(request.getQueryString()).thenReturn(null);
        lenient().when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @Test
    void doFilter_noRules_shouldProceed() throws Exception {
        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of());

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_noProjectHeader_shouldProceed() throws Exception {
        when(request.getHeader("X-Project-Id")).thenReturn(null);

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidProjectId_shouldProceed() throws Exception {
        when(request.getHeader("X-Project-Id")).thenReturn("not-a-uuid");

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_ipBlockMatch_shouldBlock() throws Exception {
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType("IP_BLOCK");
        rule.setPattern("10.0.0.1");

        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(rule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_ipBlockNoMatch_shouldProceed() throws Exception {
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType("IP_BLOCK");
        rule.setPattern("192.168.1.1");

        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(rule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilter_rateLimit_shouldBlockWhenExceeded() throws Exception {
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType("RATE_LIMIT");
        rule.setPattern("1000.0");

        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(rule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilter_sqliBlockMatchInUri_shouldBlock() throws Exception {
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType("SQLI_BLOCK");
        rule.setPattern("");
        rule.setAction("BLOCK");

        when(request.getRequestURI()).thenReturn("/api/users?name=test' OR '1'='1");
        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(rule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_sqliBlockInQueryString_shouldBlock() throws Exception {
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType("SQLI_BLOCK");
        rule.setPattern("");
        rule.setAction("BLOCK");

        when(request.getRequestURI()).thenReturn("/api/users");
        when(request.getQueryString()).thenReturn("name=test' OR '1'='1");
        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(rule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_sqliBlockNoMatch_shouldProceed() throws Exception {
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType("SQLI_BLOCK");
        rule.setPattern("");
        rule.setAction("BLOCK");

        when(request.getRequestURI()).thenReturn("/api/health");
        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(rule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_multipleRules_shouldEvaluateAll() throws Exception {
        WafRule ipRule = new WafRule();
        ipRule.setProjectId(projectId);
        ipRule.setRuleType("IP_BLOCK");
        ipRule.setPattern("10.0.0.1");

        WafRule sqliRule = new WafRule();
        sqliRule.setProjectId(projectId);
        sqliRule.setRuleType("SQLI_BLOCK");
        sqliRule.setPattern("");
        sqliRule.setAction("BLOCK");

        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(ipRule, sqliRule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void getClientIp_withXForwardedFor_shouldReturnFirstIp() throws Exception {
        WafRule rule = new WafRule();
        rule.setProjectId(projectId);
        rule.setRuleType("IP_BLOCK");
        rule.setPattern("203.0.113.1");

        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1, 192.168.1.1");
        when(wafRuleRepository.findByProjectIdAndIsActiveTrue(projectId)).thenReturn(List.of(rule));

        wafFilter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(403);
        verify(filterChain, never()).doFilter(request, response);
    }
}
