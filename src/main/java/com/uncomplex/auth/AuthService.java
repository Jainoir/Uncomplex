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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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

    @Transactional(readOnly = true)
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

    private AuthResponse toResponse(AppUser user) {
        JwtService.IssuedToken issued = jwtService.issueFor(user);
        return new AuthResponse(issued.token(), issued.expiresAt(), user.getEmail());
    }
}
