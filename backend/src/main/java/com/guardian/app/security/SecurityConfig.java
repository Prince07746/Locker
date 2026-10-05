package com.guardian.app.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final GuardianJwtFilter guardianJwtFilter;

    public SecurityConfig(GuardianJwtFilter guardianJwtFilter) {
        this.guardianJwtFilter = guardianJwtFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Guardian account creation + login
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Agent endpoints authenticate with a per-device token inside the controller
                .requestMatchers("/api/v1/devices/*/heartbeat",
                                 "/api/v1/devices/*/events",
                                 "/api/v1/devices/*/uninstall/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                // Everything under /guardian requires a valid guardian JWT
                .requestMatchers("/api/v1/guardian/**").hasRole("GUARDIAN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(guardianJwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
