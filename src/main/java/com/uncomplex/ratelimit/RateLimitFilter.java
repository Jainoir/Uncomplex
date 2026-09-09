package com.uncomplex.ratelimit;

import com.uncomplex.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Collections;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Separate budgets for generation and login/registration; proxy headers require explicit trust. */
@Component
@Lazy(false) // Preserve proxy validation at startup if global lazy initialization is enabled.
public class RateLimitFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RateLimitFilter.class);
    private final RateLimiter generationLimiter;
    private final RateLimiter authLimiter;
    private final List<IpAddressMatcher> trustedProxies;
    private final int trustedHops;

    public RateLimitFilter(RateLimiter rateLimiter, StringRedisTemplate redis, AppProperties properties,
            @Value("${app.rate-limit.auth-attempts-per-window:30}") long authAttempts,
            @Value("${app.rate-limit.trusted-proxy-cidrs:}") String proxyCidrs,
            @Value("${app.rate-limit.trusted-proxy-hops:0}") int trustedHops,
            @Value("${app.rate-limit.require-trusted-proxy:false}") boolean requireTrustedProxy) {
        if (trustedHops < 0 || trustedHops > 10) {
            throw new IllegalStateException("TRUSTED_PROXY_HOPS must be 0..10");
        }
        if (requireTrustedProxy && trustedHops == 0 && proxyCidrs.isBlank()) {
            throw new IllegalStateException("Set TRUSTED_PROXY_HOPS (preferred where the proxy IPs are not published, "
                    + "such as Render) or TRUSTED_PROXY_CIDRS to identify the reverse proxies on this deployment");
        }
        this.trustedHops = trustedHops;
        this.generationLimiter = rateLimiter;
        this.authLimiter = "redis".equals(properties.rateLimit().store())
                ? new RedisRateLimiter(redis, authAttempts, Duration.ofMinutes(15), "ratelimit:auth:")
                : new InMemoryRateLimiter(authAttempts, Duration.ofMinutes(15));
        this.trustedProxies = Arrays.stream(proxyCidrs.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
    }

    private boolean isAuth(HttpServletRequest request) {
        return "/api/auth/login".equals(request.getRequestURI())
                || "/api/auth/register".equals(request.getRequestURI());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !(isAuth(request) || "/api/roadmaps".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        RateLimiter.Decision decision;
        String client = clientKey(request);
        // Deploy once with this at DEBUG to read the real chain, then set trusted-proxy-hops to the
        // number of proxies between this app and the client (peer included).
        log.debug("Rate-limit address: peer={}, x-forwarded-for={}, client={}",
                request.getRemoteAddr(), String.join(" | ", Collections.list(request.getHeaders("X-Forwarded-For"))), client);
        try {
            decision = (isAuth(request) ? authLimiter : generationLimiter).tryConsume(client);
        } catch (RuntimeException failure) {
            response.setStatus(503);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader("Retry-After", "30");
            response.getWriter().write("""
                    {"type":"about:blank","title":"Service unavailable","status":503,
                    "detail":"Request limiting is temporarily unavailable. Please try again shortly."}
                    """);
            return;
        }
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        String detail = isAuth(request) ? "Too many login or registration attempts. Try again later."
                : "Daily roadmap generation limit reached. Try again later.";
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too many requests\",\"status\":429,\"detail\":\"" + detail + "\"}");
    }

    private boolean trusted(String address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }

    private String clientKey(HttpServletRequest request) {
        String peer = literalAddress(request.getRemoteAddr());
        if (peer == null) return request.getRemoteAddr();
        String address = peer;
        var headers = Collections.list(request.getHeaders("X-Forwarded-For"));
        if (headers.isEmpty()) return address;
        String[] chain = String.join(",", headers).split(",", -1);
        // Hop counting is the only workable mode where the proxy addresses are not published
        // (Render sits behind Cloudflare and does not document its ingress ranges).
        if (trustedHops > 0) return hopAddress(chain, peer);
        if (!trusted(address)) return address;
        // Walk from the closest proxy, stopping at the first untrusted address.
        for (int i = chain.length - 1; i >= 0 && trusted(address); i--) {
            String candidate = literalAddress(chain[i].trim());
            if (candidate == null) return peer;
            address = candidate;
        }
        // A chain made entirely of proxies identifies no client. Never use a
        // client-supplied proxy address as a fresh rate-limit budget.
        return trusted(address) ? peer : address;
    }

    /**
     * Trust a fixed number of proxies rather than their addresses. With N trusted hops the client is
     * the Nth entry from the right, because each proxy appends the peer it received from. Entries to
     * the left of it are client-supplied and must never be trusted. A chain shorter than the
     * configured hop count means the request did not arrive through the expected proxies, so the
     * socket peer is used instead of a spoofable value.
     */
    private String hopAddress(String[] chain, String peer) {
        int index = chain.length - trustedHops;
        if (index < 0 || index >= chain.length) return peer;
        String candidate = literalAddress(chain[index].trim());
        return candidate == null ? peer : candidate;
    }

    /** Parse literals only, never DNS names. Normalize equivalent spellings to one budget. */
    private static String literalAddress(String value) {
        if (value == null || value.length() > 45) return null;
        if (value.contains(":")) {
            if (!value.matches("[0-9a-fA-F:.]+")) return null;
            try { return InetAddress.getByName(value).getHostAddress(); }
            catch (UnknownHostException invalid) { return null; }
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) return null;
        for (int i = 0; i < octets.length; i++) {
            if (!octets[i].matches("[0-9]{1,3}")) return null;
            int octet = Integer.parseInt(octets[i]);
            if (octet > 255) return null;
            octets[i] = Integer.toString(octet);
        }
        return String.join(".", octets);
    }
}
