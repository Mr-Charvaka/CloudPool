package com.cloudpool.aop;

import com.cloudpool.config.ReadReplicaContext;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ReadOnlyRoutingAspect {

    @Before("@annotation(org.springframework.transaction.annotation.Transactional(readOnly = true))")
    public void beforeReadOnly() {
        ReadReplicaContext.setReadOnly(true);
    }

    @After("@annotation(org.springframework.transaction.annotation.Transactional(readOnly = true))")
    public void afterReadOnly() {
        ReadReplicaContext.clear();
    }
}