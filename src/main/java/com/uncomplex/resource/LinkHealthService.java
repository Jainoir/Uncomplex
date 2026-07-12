package com.uncomplex.resource;

import com.uncomplex.roadmap.entity.NodeResource;
import com.uncomplex.roadmap.repository.NodeResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Probes every stored resource URL and records reachability. The credibility
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

    @Transactional
    public CheckResult checkAll() {
        List<NodeResource> all = resources.findAll();
        var verdictByUrl = new java.util.HashMap<String, Boolean>();
        int dead = 0;
        for (NodeResource resource : all) {
            boolean reachable = verdictByUrl.computeIfAbsent(resource.getUrl(), prober::isReachable);
            resource.recordProbeResult(reachable);
            if (!reachable) {
                dead++;
                log.warn("Dead resource link: {} ({})", resource.getUrl(), resource.getTitle());
            }
        }
        log.info("Link health check finished: {} resources, {} unreachable", all.size(), dead);
        return new CheckResult(all.size(), dead);
    }

    public record CheckResult(int checked, int unreachable) {
    }
}
