package com.uncomplex.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Probes a URL with HEAD (falling back to GET for servers that reject HEAD).
 * 2xx/3xx = reachable; 4xx/5xx or any network failure = not reachable.
 */
@Component
public class HttpUrlProber implements UrlProber {

    private static final Logger log = LoggerFactory.getLogger(HttpUrlProber.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(TIMEOUT)
            .build();

    @Override
    public boolean isReachable(String url) {
        try {
            int status = send(url, "HEAD");
            if (status == 405 || status == 501) { // server doesn't support HEAD
                status = send(url, "GET");
            }
            return status < 400;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.debug("Probe failed for {}: {}", url, e.toString());
            return false;
        }
    }

    private int send(String url, String method) throws java.io.IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .timeout(TIMEOUT)
                .header("User-Agent", "Uncomplex-LinkHealth/1.0")
                .build();
        return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
