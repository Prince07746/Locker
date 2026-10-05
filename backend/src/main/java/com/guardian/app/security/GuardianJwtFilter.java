package com.guardian.app.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class GuardianJwtFilter extends OncePerRequestFilter {

    private final TokenService tokens;

    public GuardianJwtFilter(TokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = tokens.parse(header.substring(7));
                if ("guardian".equals(claims.get("typ"))) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            claims.getSubject(), null,
                            AuthorityUtils.createAuthorityList("ROLE_GUARDIAN"));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // invalid token -> unauthenticated; secured endpoints will reject
            }
        }
        chain.doFilter(req, res);
    }
}
