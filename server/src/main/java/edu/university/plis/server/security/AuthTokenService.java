package edu.university.plis.server.security;

import edu.university.plis.server.config.JwtProperties;
import edu.university.plis.server.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;

@Service
public class AuthTokenService {
    private static final Logger logger = LoggerFactory.getLogger(AuthTokenService.class);

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public AuthTokenService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = createSigningKey(properties.jwtSecret());
    }

    public IssuedToken issueToken(AppUser user) {
        return issueToken(user, null);
    }

    public IssuedToken issueToken(AppUser user, String deviceId) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.tokenLifetime());
        var builder = Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("uid", user.getId())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
        if (deviceId != null && !deviceId.isBlank()) {
            builder.claim("device", deviceId);
        }
        String token = builder.signWith(signingKey).compact();
        return new IssuedToken(token, expiresAt);
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isValid(String token, String expectedUsername) {
        Claims claims = parseClaims(token);
        return expectedUsername.equalsIgnoreCase(claims.getSubject())
                && claims.getExpiration().toInstant().isAfter(Instant.now());
    }

    public String extractDeviceId(String token) {
        return parseClaims(token).get("device", String.class);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey createSigningKey(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            logger.warn("PLIS_JWT_SECRET is not set; tokens will be signed with an ephemeral key and expire on restart");
            return Jwts.SIG.HS256.key().build();
        }
        byte[] secretBytes;
        try {
            secretBytes = Decoders.BASE64.decode(configuredSecret);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("PLIS_JWT_SECRET must be a Base64-encoded value of at least 32 bytes", exception);
        }
        if (secretBytes.length < 32) {
            throw new IllegalStateException("PLIS_JWT_SECRET must decode to at least 32 bytes");
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }
}
