package com.uncomplex.library.repository;

import com.uncomplex.library.entity.NodeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NodeProgressRepository extends JpaRepository<NodeProgress, Long> {

    Optional<NodeProgress> findByUserIdAndNodeId(Long userId, Long nodeId);

    @Query("select p.node.id from NodeProgress p where p.user.id = :userId and p.node.roadmap.id = :roadmapId")
    List<Long> findCompletedNodeIds(@Param("userId") Long userId, @Param("roadmapId") Long roadmapId);

    long countByUserIdAndNodeRoadmapId(Long userId, Long roadmapId);

    @Modifying
    @Query("delete from NodeProgress p where p.user.id = :userId and p.node.roadmap.id = :roadmapId")
    void deleteAllForUserAndRoadmap(@Param("userId") Long userId, @Param("roadmapId") Long roadmapId);
}
