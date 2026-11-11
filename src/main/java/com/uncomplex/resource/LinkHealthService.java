package com.uncomplex.resource;

import com.uncomplex.roadmap.entity.NodeResource;
import com.uncomplex.roadmap.repository.NodeResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;

/**
 * Probes stored resource URLs and records reachability. The credibility
 * allowlist keeps bad domains out at generation time; this catches links that
 * die afterwards. Duplicate URLs are probed once per run.
 */
@Service
public class LinkHealthService {

    private static final Logger log = LoggerFactory.getLogger(LinkHealthService.class);

    private final NodeResourceRepository resources;
    private final UrlProber prober;

    public LinkHealthService(NodeResourceRepository resources, UrlProber prober) {
        this.resources = resources;
        this.prober = prober;
    }

    /** Probes every stored resource. */
    @Transactional
    public CheckResult checkAll() {
        return probe(resources.findAll());
    }

    /**
     * Probes only resources that were never checked or were last checked longer ago than
     * {@code maxAge}, at most {@code batchSize} per run. A host that sleeps when idle is
     * rarely awake for a fixed nightly slot, so this is what keeps results current there:
     * it runs shortly after every start and periodically while the instance stays up.
     */
    @Transactional
    public CheckResult checkStale(Duration maxAge, int batchSize) {
        List<NodeResource> stale = resources.findStale(Instant.now().minus(maxAge), PageRequest.of(0, batchSize));
        return stale.isEmpty() ? new CheckResult(0, 0) : probe(stale);
    }

    private CheckResult probe(List<NodeResource> batch) {
        var verdictByUrl = new HashMap<String, Boolean>();
        int dead = 0;
        for (NodeResource resource : batch) {
            boolean reachable = verdictByUrl.computeIfAbsent(resource.getUrl(), prober::isReachable);
            resource.recordProbeResult(reachable);
            if (!reachable) {
                dead++;
                log.warn("Dead resource link: {} ({})", resource.getUrl(), resource.getTitle());
            }
        }
        log.info("Link health check finished: {} resources, {} unreachable", batch.size(), dead);
        return new CheckResult(batch.size(), dead);
    }

    public record CheckResult(int checked, int unreachable) {
    }
}
