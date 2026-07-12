package com.uncomplex.roadmap.repository;

import com.uncomplex.roadmap.entity.Roadmap;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoadmapRepository extends JpaRepository<Roadmap, Long> {

    Optional<Roadmap> findByCacheKey(String cacheKey);

    Optional<Roadmap> findByShareToken(String shareToken);

    boolean existsByShareToken(String shareToken);
}
