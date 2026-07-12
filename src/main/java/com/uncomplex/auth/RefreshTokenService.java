package com.uncomplex.auth;

import com.uncomplex.config.AppProperties;
import com.uncomplex.exception.InvalidCredentialsException;
import com.uncomplex.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Opaque, rotating refresh tokens. Rules:
 * - only the SHA-256 hash is stored; the raw token exists client-side only
 * - every refresh rotates: the old token is revoked, a new one is issued
 * - presenting an already-revoked token is treated as theft (someone replayed a
 *   rotated token), so every session for that user is revoked
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration ttl;

    public RefreshTokenService(RefreshTokenRepository repository, AppProperties properties) {
        this.repository = repository;
        this.ttl = Duration.ofDays(properties.security().refreshTokenTtlDays());
    }

    @Transactional
    public Issued issue(AppUser user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(ttl);
        repository.save(new RefreshToken(user, sha256(raw), expiresAt));
        return new Issued(raw, expiresAt);
    }

    /**
     * Validates and revokes the presented token, returning its user for re-issue.
     * noRollbackFor is load-bearing: on reuse detection we revoke every session and
     * then throw — without it, the thrown exception would roll back the revocation
     * and neuter the theft response.
     */
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AppUser rotate(String rawToken) {
        RefreshToken token = repository.findByTokenHash(sha256(rawToken))
                .orElseThrow(InvalidCredentialsException::new);

        if (token.isRevoked()) {
            // Reuse of a rotated token: assume compromise, kill every session.
            log.warn("Refresh token reuse detected for user {} — revoking all sessions", token.getUser().getId());
            repository.revokeAllForUser(token.getUser().getId(), Instant.now());
            throw new InvalidCredentialsException();
        }
        if (token.isExpired()) {
            throw new InvalidCredentialsException();
        }

        token.revoke();
        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        // Logout is idempotent: unknown tokens are ignored rather than leaking their validity.
        repository.findByTokenHash(sha256(rawToken)).ifPresent(RefreshToken::revoke);
    }

    static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record Issued(String rawToken, Instant expiresAt) {
    }
}
