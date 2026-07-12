package com.uncomplex.roadmap.service;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generates share tokens like "rate-limiting-k7p4x": a readable topic slug plus a
 * random suffix so tokens are not guessable from the topic alone.
 */
public final class ShareTokens {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // no 0/O, 1/l/i
    private static final int SUFFIX_LENGTH = 5;
    private static final int MAX_SLUG_LENGTH = 40;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ShareTokens() {
    }

    public static String forTopic(String topic) {
        String slug = CacheKeys.normalizeTopic(topic)
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH);
        }
        if (slug.isEmpty()) {
            slug = "roadmap";
        }
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return slug.toLowerCase(Locale.ROOT) + "-" + suffix;
    }
}
