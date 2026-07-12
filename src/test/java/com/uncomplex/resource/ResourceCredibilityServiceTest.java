package com.uncomplex.resource;

import com.uncomplex.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceCredibilityServiceTest {

    private final ResourceCredibilityService service =
            new ResourceCredibilityService(TestFixtures.appProperties());

    @ParameterizedTest
    @ValueSource(strings = {
            "https://developer.mozilla.org/en-US/docs/Web/HTTP",
            "https://docs.spring.io/spring-boot/index.html",
            "https://spring.io/guides",
            "https://ocw.mit.edu/courses/",           // *.edu is always allowed
    })
    void allowsHttpsUrlsOnAllowlistedDomains(String url) {
        assertThat(service.isAllowed(url)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://developer.mozilla.org/en-US/docs/Web/HTTP",  // https only
            "https://some-random-blog.example.com/post",         // not on allowlist
            "https://evil-developer.mozilla.org.attacker.com/x", // suffix spoofing
            "not a url",
            "",
    })
    void rejectsEverythingElse(String url) {
        assertThat(service.isAllowed(url)).isFalse();
    }

    @Test
    void subdomainsOfAllowlistedDomainsAreAllowed() {
        assertThat(service.isAllowed("https://docs.spring.io/spring-framework/reference/")).isTrue();
    }

    @Test
    void nullIsRejected() {
        assertThat(service.isAllowed(null)).isFalse();
    }
}
