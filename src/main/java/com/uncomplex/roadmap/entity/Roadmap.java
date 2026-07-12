package com.uncomplex.roadmap.entity;

import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "roadmap")
public class Roadmap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalized topic|level|goal key used to serve repeat requests without a new AI call. */
    @Column(name = "cache_key", nullable = false, unique = true)
    private String cacheKey;

    @Column(name = "share_token", nullable = false, unique = true, length = 64)
    private String shareToken;

    @Column(nullable = false, length = 160)
    private String topic;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 2000)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 20)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private LearningGoal goal;

    @Column(name = "estimated_total_minutes", nullable = false)
    private int estimatedTotalMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "roadmap", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    private List<RoadmapNode> nodes = new ArrayList<>();

    protected Roadmap() {
    }

    public Roadmap(String cacheKey, String shareToken, String topic, String title, String summary,
                   ExperienceLevel experienceLevel, LearningGoal goal) {
        this.cacheKey = cacheKey;
        this.shareToken = shareToken;
        this.topic = topic;
        this.title = title;
        this.summary = summary;
        this.experienceLevel = experienceLevel;
        this.goal = goal;
        this.createdAt = Instant.now();
    }

    public void addNode(RoadmapNode node) {
        node.setRoadmap(this);
        nodes.add(node);
        estimatedTotalMinutes += node.getEstimatedMinutes();
    }

    public Long getId() {
        return id;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public String getShareToken() {
        return shareToken;
    }

    public String getTopic() {
        return topic;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public LearningGoal getGoal() {
        return goal;
    }

    public int getEstimatedTotalMinutes() {
        return estimatedTotalMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<RoadmapNode> getNodes() {
        return nodes;
    }
}
