package com.uncomplex.auth;

import com.uncomplex.auth.dto.AuthResponse;
import com.uncomplex.exception.ConflictException;
import com.uncomplex.exception.InvalidCredentialsException;
import com.uncomplex.user.AppUser;
import com.uncomplex.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("An account with this email already exists");
        }
        AppUser user = userRepository.save(new AppUser(normalizedEmail, passwordEncoder.encode(rawPassword)));
        return toResponse(user);
    }

    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            // Same error for unknown email and wrong password: no account enumeration.
            throw new InvalidCredentialsException();
        }
        return toResponse(user);
    }

    /**
     * Exchanges a valid refresh token for a fresh access + refresh token pair (rotation).
     * noRollbackFor must match rotate(): both interceptors share one physical
     * transaction, and either one marking rollback-only would undo the
     * revoke-all-sessions response to token reuse.
     */
    @Transactional(noRollbackFor = com.uncomplex.exception.InvalidCredentialsException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        AppUser user = refreshTokenService.rotate(rawRefreshToken);
        return toResponse(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResponse toResponse(AppUser user) {
        JwtService.IssuedToken access = jwtService.issueFor(user);
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user);
        return new AuthResponse(access.token(), access.expiresAt(),
                refresh.rawToken(), refresh.expiresAt(), user.getEmail());
    }
}
