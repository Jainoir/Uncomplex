package com.uncomplex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Ai ai,
        Generation generation,
        RateLimit rateLimit,
        Credibility credibility,
        Security security
) {

    public record Security(String jwtSecret, long tokenTtlMinutes, long refreshTokenTtlDays) {
    }

    public record Ai(String provider, Anthropic anthropic) {
        public record Anthropic(String apiKey, String model, long maxOutputTokens) {
        }
    }

    public record Generation(
            int minPrerequisites,
            int maxPrerequisites,
            int maxResourcesPerPrerequisite,
            int maxAttempts
    ) {
    }

    public record RateLimit(long generationsPerDay, String store) {
    }

    public record Credibility(List<String> allowedDomains) {
    }
}
