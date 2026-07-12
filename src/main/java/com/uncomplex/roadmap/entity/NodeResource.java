package com.uncomplex.roadmap.entity;

import com.uncomplex.roadmap.model.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "node_resource")
public class NodeResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id")
    private RoadmapNode node;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 40)
    private SourceType sourceType;

    @Column(name = "credibility_reason", nullable = false, length = 500)
    private String credibilityReason;

    protected NodeResource() {
    }

    public NodeResource(String title, String url, SourceType sourceType, String credibilityReason) {
        this.title = title;
        this.url = url;
        this.sourceType = sourceType;
        this.credibilityReason = credibilityReason;
    }

    void setNode(RoadmapNode node) {
        this.node = node;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getCredibilityReason() {
        return credibilityReason;
    }
}
