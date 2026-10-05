package com.guardian.app.security;

import com.guardian.app.common.ApiExceptions;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentGuardian {

    private CurrentGuardian() {}

    public static UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw ApiExceptions.unauthorized("Not authenticated");
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }
}
