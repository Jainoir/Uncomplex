package com.uncomplex.resource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.link-health.enabled", havingValue = "true", matchIfMissing = true)
public class LinkHealthScheduler {

    private final LinkHealthService linkHealthService;

    public LinkHealthScheduler(LinkHealthService linkHealthService) {
        this.linkHealthService = linkHealthService;
    }

    @Scheduled(cron = "${app.link-health.cron:0 0 4 * * *}")
    public void nightlyCheck() {
        linkHealthService.checkAll();
    }
}
