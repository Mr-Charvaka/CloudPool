package com.cloudpool.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT_LOG");

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) && "
            + "execution(* *(..)) && "
            + "(@annotation(org.springframework.web.bind.annotation.DeleteMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PostMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PutMapping) || "
            + "@annotation(org.springframework.web.bind.annotation.PatchMapping))")
    public void destructiveOperations() {}

    @AfterReturning("destructiveOperations()")
    public void logDestructiveOperation(JoinPoint joinPoint) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        HttpServletRequest request = attrs.getRequest();

        auditLog.info("action={} method={} path={} user={} ip={} traceId={}",
            getActionFromMethod(request.getMethod()),
            joinPoint.getSignature().toShortString(),
            request.getRequestURI(),
            MDC.get("userId"),
            request.getRemoteAddr(),
            MDC.get("traceId")
        );
    }

    private String getActionFromMethod(String httpMethod) {
        return switch (httpMethod) {
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> httpMethod;
        };
    }
}