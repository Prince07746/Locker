package com.guardian.app.guardian;

import com.guardian.app.security.TokenService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final long GUARDIAN_TOKEN_TTL = 60 * 60 * 12; // 12h

    private final GuardianAccountService accounts;
    private final TokenService tokens;

    public AuthController(GuardianAccountService accounts, TokenService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    public record RegisterRequest(@Email @NotBlank String email,
                                  @NotBlank @Size(min = 10, message = "Use at least 10 characters") String password) {}

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest req) {
        Guardian g = accounts.register(req.email(), req.password());
        return Map.of("guardianId", g.getId(),
                "token", tokens.issueGuardianToken(g.getId(), GUARDIAN_TOKEN_TTL));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) {
        Guardian g = accounts.authenticate(req.email(), req.password());
        return Map.of("token", tokens.issueGuardianToken(g.getId(), GUARDIAN_TOKEN_TTL));
    }
}
