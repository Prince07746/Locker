package com.guardian.app.security;

import com.guardian.app.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class TokenService {

    private final SecretKey key;

    public TokenService(AppProperties props) {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(props.jwtSigningKey()));
    }

    public String issueGuardianToken(UUID guardianId, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(guardianId.toString())
                .claim("typ", "guardian")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key)
                .compact();
    }

    public String issueUninstallToken(UUID deviceId, long ttlSeconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(deviceId.toString())
                .claim("typ", "uninstall")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlSeconds * 1000))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
