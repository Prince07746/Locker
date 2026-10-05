package com.guardian.app.guardian;

import com.guardian.app.common.ApiExceptions;
import com.guardian.app.crypto.HashUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GuardianAccountService {

    private final GuardianRepository repo;
    private final PasswordEncoder encoder;

    public GuardianAccountService(GuardianRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Transactional
    public Guardian register(String email, String password) {
        String normalized = HashUtil.normalizeEmail(email);
        String lookup = HashUtil.emailLookup(normalized);
        if (repo.existsByEmailLookup(lookup)) {
            throw ApiExceptions.conflict("An account with that email already exists");
        }
        Guardian g = new Guardian(UUID.randomUUID(), normalized, lookup, encoder.encode(password));
        return repo.save(g);
    }

    public Guardian authenticate(String email, String password) {
        String lookup = HashUtil.emailLookup(HashUtil.normalizeEmail(email));
        Guardian g = repo.findByEmailLookup(lookup)
                .orElseThrow(() -> ApiExceptions.unauthorized("Invalid credentials"));
        if (!encoder.matches(password, g.getPasswordHash())) {
            throw ApiExceptions.unauthorized("Invalid credentials");
        }
        return g;
    }
}
