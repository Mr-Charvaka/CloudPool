package com.cloudpool.aop;

import com.cloudpool.config.ReadReplicaContext;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ReadOnlyRoutingAspect {

    @Before("@annotation(tx)")
    public void beforeReadOnly(org.springframework.transaction.annotation.Transactional tx) {
        if (tx.readOnly()) {
            ReadReplicaContext.setReadOnly(true);
        }
    }

    @After("@annotation(tx)")
    public void afterReadOnly(org.springframework.transaction.annotation.Transactional tx) {
        if (tx.readOnly()) {
            ReadReplicaContext.clear();
        }
    }
}