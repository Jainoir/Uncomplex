package com.uncomplex.roadmap.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShareTokensTest {

    @Test
    void tokenIsSlugPlusRandomSuffix() {
        String token = ShareTokens.forTopic("Rate Limiting");

        assertThat(token).matches("rate-limiting-[a-z2-9]{5}");
    }

    @Test
    void weirdCharactersAreStrippedFromSlug() {
        String token = ShareTokens.forTopic("  C++ / OAuth 2.0!  ");

        assertThat(token).matches("[a-z0-9-]+-[a-z2-9]{5}");
        assertThat(token).doesNotContain("+", "/", "!", " ");
    }

    @Test
    void emptySlugFallsBackToRoadmap() {
        String token = ShareTokens.forTopic("!!!");

        assertThat(token).matches("roadmap-[a-z2-9]{5}");
    }

    @Test
    void tokensAreUniqueAcrossCalls() {
        String a = ShareTokens.forTopic("Docker");
        String b = ShareTokens.forTopic("Docker");

        assertThat(a).isNotEqualTo(b);
    }
}
