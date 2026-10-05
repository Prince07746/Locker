package com.guardian.app.guardian;

import com.guardian.app.crypto.EmailCryptoConverter;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guardians")
public class Guardian {

    @Id
    private UUID id;

    @Convert(converter = EmailCryptoConverter.class)
    @Column(name = "email_enc", nullable = false)
    private String email;            // encrypted transparently at rest

    @Column(name = "email_lookup", nullable = false, unique = true)
    private String emailLookup;      // deterministic HMAC for lookups

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Guardian() {}

    public Guardian(UUID id, String email, String emailLookup, String passwordHash) {
        this.id = id;
        this.email = email;
        this.emailLookup = emailLookup;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getEmailLookup() { return emailLookup; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
