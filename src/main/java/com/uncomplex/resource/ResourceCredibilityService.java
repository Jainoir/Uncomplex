package com.uncomplex.resource;

import com.uncomplex.config.AppProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * Decides whether a resource URL is acceptable. AI-generated URLs are never trusted
 * blindly: only https links whose host is on the configured allowlist (or a *.edu
 * domain) survive. Everything else is dropped before persistence.
 */
@Service
public class ResourceCredibilityService {

    private final List<String> allowedDomains;

    public ResourceCredibilityService(AppProperties properties) {
        this.allowedDomains = properties.credibility().allowedDomains().stream()
                .map(d -> d.toLowerCase(Locale.ROOT))
                .toList();
    }

    public boolean isAllowed(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".edu")) {
            return true;
        }
        return allowedDomains.stream()
                .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }
}
