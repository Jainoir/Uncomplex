package com.uncomplex.roadmap.repository;

import com.uncomplex.roadmap.entity.NodeResource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NodeResourceRepository extends JpaRepository<NodeResource, Long> {

    /** Resources never probed, or last probed before the cutoff; never-probed first. */
    @Query("""
            select r from NodeResource r
            where r.lastCheckedAt is null or r.lastCheckedAt < :cutoff
            order by r.lastCheckedAt asc nulls first, r.id asc
            """)
    List<NodeResource> findStale(@Param("cutoff") Instant cutoff, Pageable page);
}
