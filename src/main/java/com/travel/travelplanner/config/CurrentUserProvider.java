package com.travel.travelplanner.config;

import org.springframework.stereotype.Component;

/**
 * Exposes the current userId from the request (X-User-Id header).
 * Use in controllers/services for user-specific operations.
 */
@Component
public class CurrentUserProvider {

    public String getCurrentUserId() {
        return UserIdContext.getCurrentUserId();
    }
}
