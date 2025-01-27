package com.crypto.arbitrage.aspect;

import com.crypto.arbitrage.exception.NotAuthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuthorizationAspect {
    @Autowired
    private HttpServletRequest request;
    @Value("${security.management.token}")
    private String securityManagementToken;

    @Around("@annotation(com.crypto.arbitrage.aspect.annotation.Authorized)")
    public Object checkAuthorization(ProceedingJoinPoint joinPoint) throws Throwable {
        String token = request.getHeader("X-Arbitrage-Token");
        if (token == null || !token.equals(securityManagementToken)) {
            throw new NotAuthorizedException("Not Authorized");
        }
        return joinPoint.proceed();
    }
}
