package com.uncomplex.library.entity;

import com.uncomplex.roadmap.entity.Roadmap;
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

/**
 * A user's library entry. Roadmaps are shared and immutable; what a user owns is
 * their membership (this row) and their per-node progress.
 */
@Entity
@Table(name = "saved_roadmap",
        uniqueConstraints = @UniqueConstraint(name = "uq_saved_user_roadmap", columnNames = {"user_id", "roadmap_id"}))
public class SavedRoadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id")
    private Roadmap roadmap;

    @Column(name = "saved_at", nullable = false)
    private Instant savedAt;

    protected SavedRoadmap() {
    }

    public SavedRoadmap(AppUser user, Roadmap roadmap) {
        this.user = user;
        this.roadmap = roadmap;
        this.savedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public Roadmap getRoadmap() {
        return roadmap;
    }

    public Instant getSavedAt() {
        return savedAt;
    }
}
