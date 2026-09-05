package com.uncomplex.ratelimit;

import com.uncomplex.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {
    private final RateLimiter generation = mock(RateLimiter.class);

    private RateLimitFilter filter(String proxies) {
        return new RateLimitFilter(generation, mock(StringRedisTemplate.class), TestFixtures.appProperties(), 2, proxies, 0, false);
    }

    private RateLimitFilter hopFilter(int hops) {
        return new RateLimitFilter(generation, mock(StringRedisTemplate.class), TestFixtures.appProperties(), 2, "", hops, false);
    }

    /** Sends `forwarded` from an unlisted peer and returns nothing; assertions verify the budget key. */
    private void dispatch(RateLimitFilter filter, String forwarded) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/roadmaps");
        request.setRemoteAddr("10.0.0.2");
        if (forwarded != null) request.addHeader("X-Forwarded-For", forwarded);
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
    }

    @Test
    void oneTrustedHopUsesTheLastEntryAndIgnoresASpoofedPrefix() throws Exception {
        when(generation.tryConsume("203.0.113.9")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        dispatch(hopFilter(1), "1.2.3.4, 203.0.113.9");
        verify(generation).tryConsume("203.0.113.9");
    }

    @Test
    void twoTrustedHopsSkipTheInnerProxy() throws Exception {
        when(generation.tryConsume("203.0.113.9")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        dispatch(hopFilter(2), "1.2.3.4, 203.0.113.9, 198.51.100.1");
        verify(generation).tryConsume("203.0.113.9");
    }

    @Test
    void chainShorterThanConfiguredHopsFallsBackToThePeer() throws Exception {
        when(generation.tryConsume("10.0.0.2")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        dispatch(hopFilter(3), "203.0.113.9");
        verify(generation).tryConsume("10.0.0.2");
    }

    @Test
    void missingForwardedHeaderUnderHopCountingFallsBackToThePeer() throws Exception {
        when(generation.tryConsume("10.0.0.2")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        dispatch(hopFilter(1), null);
        verify(generation).tryConsume("10.0.0.2");
    }

    @Test
    void malformedEntryAtTheTrustedHopFallsBackToThePeer() throws Exception {
        when(generation.tryConsume("10.0.0.2")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        dispatch(hopFilter(1), "203.0.113.9, 1.2.3.4.5");
        verify(generation).tryConsume("10.0.0.2");
    }

    @Test
    void hopCountingSatisfiesTheRequiredProxyConfigurationWithoutCidrs() {
        assertThat(new RateLimitFilter(generation, mock(StringRedisTemplate.class),
                TestFixtures.appProperties(), 2, "", 1, true)).isNotNull();
    }

    @Test
    void outOfRangeHopCountIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> hopFilter(11))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TRUSTED_PROXY_HOPS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.0.0.9", "10.0.0.9, 10.0.0.3", "10.0.0.99, 10.0.0.8, 10.0.0.3"})
    void entirelyTrustedChainUsesSocketPeerBudget(String chain) throws Exception {
        when(generation.tryConsume("10.0.0.2")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        var request = new MockHttpServletRequest("POST", "/api/roadmaps");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", chain);
        var response = new MockHttpServletResponse();
        filter("10.0.0.0/24").doFilter(request, response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(200);
        verify(generation).tryConsume("10.0.0.2");
        verifyNoMoreInteractions(generation);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.2.3.4.5", "256.0.0.1", "1.2.3", "dead", "::::", "2001:db8::x", "", "example.com"})
    void malformedForwardedAddressFallsBackToPeerWithout503(String invalid) throws Exception {
        when(generation.tryConsume("10.0.0.2")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        var request = new MockHttpServletRequest("POST", "/api/roadmaps");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "192.0.2.10, " + invalid);
        var response = new MockHttpServletResponse();
        filter("10.0.0.0/24").doFilter(request, response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(200);
        verify(generation).tryConsume("10.0.0.2");
    }

    @Test
    void requiredProxyConfigurationCannotSilentlyDefaultToSharedBudget() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new RateLimitFilter(generation,
                mock(StringRedisTemplate.class), TestFixtures.appProperties(), 2, "", 0, true))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("TRUSTED_PROXY_CIDRS");
    }

    @Test
    void repeatedHeadersAreCombinedBeforeFindingTheClient() throws Exception {
        when(generation.tryConsume("192.0.2.10")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        var request = new MockHttpServletRequest("POST", "/api/roadmaps");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        request.addHeader("X-Forwarded-For", "192.0.2.10, 10.0.0.3");
        filter("10.0.0.0/24").doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        verify(generation).tryConsume("192.0.2.10");
    }

    @Test
    void spoofedForwardingHeaderCannotChangeClientBudget() throws Exception {
        when(generation.tryConsume("192.0.2.10")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        var request = new MockHttpServletRequest("POST", "/api/roadmaps");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-For", "198.51.100.7");
        filter("").doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        verify(generation).tryConsume("192.0.2.10");
    }

    @Test
    void trustedProxyUsesClosestUntrustedAddressNotSpoofedFirstEntry() throws Exception {
        when(generation.tryConsume("192.0.2.10")).thenReturn(new RateLimiter.Decision(true, 1, 0));
        var request = new MockHttpServletRequest("POST", "/api/roadmaps");
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "198.51.100.7, 192.0.2.10, 10.0.0.3");
        filter("10.0.0.0/24").doFilter(request, new MockHttpServletResponse(), (req, res) -> {});
        verify(generation).tryConsume("192.0.2.10");
    }

    @Test
    void loginAndRegistrationShareBudgetButRefreshIsUnaffected() throws Exception {
        var filter = filter("");
        for (String path : new String[]{"/api/auth/login", "/api/auth/register"}) {
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", path), response, (req, res) -> {});
            assertThat(response.getStatus()).isEqualTo(200);
        }
        var blocked = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), blocked, (req, res) -> {});
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        var refresh = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/refresh"), refresh, (req, res) -> {});
        assertThat(refresh.getStatus()).isEqualTo(200);
        verifyNoInteractions(generation);
    }

    @Test
    void unavailableCounterReturnsRetryableServiceError() throws Exception {
        when(generation.tryConsume(anyString())).thenThrow(new IllegalStateException("offline"));
        var response = new MockHttpServletResponse();
        filter("").doFilter(new MockHttpServletRequest("POST", "/api/roadmaps"), response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
    }
}
