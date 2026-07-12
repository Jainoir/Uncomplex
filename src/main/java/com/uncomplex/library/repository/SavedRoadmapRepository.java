package com.uncomplex.library.repository;

import com.uncomplex.library.entity.SavedRoadmap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedRoadmapRepository extends JpaRepository<SavedRoadmap, Long> {

    @Query("select s from SavedRoadmap s join fetch s.roadmap where s.user.id = :userId order by s.savedAt desc")
    List<SavedRoadmap> findAllByUserIdWithRoadmap(@Param("userId") Long userId);

    Optional<SavedRoadmap> findByUserIdAndRoadmapId(Long userId, Long roadmapId);

    boolean existsByUserIdAndRoadmapId(Long userId, Long roadmapId);
}
