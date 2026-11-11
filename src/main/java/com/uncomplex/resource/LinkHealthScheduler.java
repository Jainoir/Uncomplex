package com.uncomplex.resource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(name = "app.link-health.enabled", havingValue = "true", matchIfMissing = true)
public class LinkHealthScheduler {

    /** Re-probe a link at most once a day. */
    static final Duration MAX_AGE = Duration.ofHours(24);

    /**
     * Each probe can take up to two 5-second timeouts, so the batch size bounds how long one
     * catch-up run can hold its transaction.
     */
    static final int CATCH_UP_BATCH = 25;

    private final LinkHealthService linkHealthService;

    public LinkHealthScheduler(LinkHealthService linkHealthService) {
        this.linkHealthService = linkHealthService;
    }

    @Scheduled(cron = "${app.link-health.cron:0 0 4 * * *}")
    public void nightlyCheck() {
        linkHealthService.checkAll();
    }

    /**
     * The nightly slot is missed whenever the host is asleep at 04:00, which on a
     * sleep-when-idle plan is nearly always. This run starts a few minutes after boot and
     * repeats while the instance is up, covering new and day-old links in small batches.
     */
    @Scheduled(
            initialDelayString = "${app.link-health.catch-up-initial-delay:PT3M}",
            fixedDelayString = "${app.link-health.catch-up-interval:PT20M}")
    public void catchUp() {
        linkHealthService.checkStale(MAX_AGE, CATCH_UP_BATCH);
    }
}
