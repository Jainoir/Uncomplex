package com.uncomplex.library.entity;

import com.uncomplex.roadmap.entity.RoadmapNode;
import com.uncomplex.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Existence of a row = this user completed this node. Un-completing deletes the row. */
@Entity
@Table(name = "node_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_progress_user_node", columnNames = {"user_id", "node_id"}))
public class NodeProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id")
    private RoadmapNode node;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    protected NodeProgress() {
    }

    public NodeProgress(AppUser user, RoadmapNode node) {
        this.user = user;
        this.node = node;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public RoadmapNode getNode() {
        return node;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
