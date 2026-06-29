package com.cloudpool.aop;

import com.cloudpool.config.ReadReplicaContext;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ReadOnlyRoutingAspect {

    @org.aspectj.lang.annotation.Around("@annotation(tx)")
    public Object aroundReadOnly(org.aspectj.lang.ProceedingJoinPoint pjp, org.springframework.transaction.annotation.Transactional tx) throws Throwable {
        boolean previousState = ReadReplicaContext.isReadOnly();
        try {
            if (tx.readOnly()) {
                ReadReplicaContext.setReadOnly(true);
            }
            return pjp.proceed();
        } finally {
            // Restore previous state (handles nested transactions)
            if (previousState) {
                ReadReplicaContext.setReadOnly(true);
            } else {
                ReadReplicaContext.clear();
            }
        }
    }
}