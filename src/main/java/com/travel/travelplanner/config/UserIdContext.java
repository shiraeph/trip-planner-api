package com.travel.travelplanner.config;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

public final class UserIdContext {

    private static final String ATTR_USER_ID = "userId";

    private UserIdContext() {
    }

    public static String getCurrentUserId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            Object v = req.getAttribute(ATTR_USER_ID);
            return v != null ? v.toString() : null;
        }
        return null;
    }
}
