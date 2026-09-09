package com.uncomplex.config;

import com.uncomplex.UncomplexApplication;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.SpringApplication;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupGuardsIntegrationTest {
    @ParameterizedTest
    @CsvSource({
            "app.rate-limit.trusted-proxy-hops, 0, Set TRUSTED_PROXY_HOPS",
            "app.security.jwt-secret, short, JWT_SECRET must be at least 32 bytes",
            "app.ai.provider, anthropic, requires the ANTHROPIC_API_KEY"
    })
    void invalidConfigurationFailsBeforeStartupReturnsEvenWithLazyInitialization(
            String property, String value, String expectedError) {
        Map<String, String> settings = new LinkedHashMap<>(Map.of(
                "spring.profiles.active", "test",
                "server.address", "127.0.0.1",
                "server.port", "0",
                "app.link-health.enabled", "false",
                "app.rate-limit.require-trusted-proxy", "true",
                "app.rate-limit.trusted-proxy-hops", "1",
                "app.rate-limit.trusted-proxy-cidrs", "",
                "app.security.jwt-secret", "startup-test-secret-0123456789abcdef",
                "app.ai.provider", "mock",
                "app.ai.anthropic.api-key", ""));
        settings.put(property, value);
        String[] args = settings.entrySet().stream()
                .map(entry -> "--" + entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
        SpringApplication application = new SpringApplication(UncomplexApplication.class);
        application.setLazyInitialization(true);

        assertThatThrownBy(() -> {
            try (var ignored = application.run(args)) {
                // Closing a successfully started context avoids leaking its server if this regresses.
            }
        }).hasRootCauseInstanceOf(IllegalStateException.class).hasStackTraceContaining(expectedError);
    }
}
