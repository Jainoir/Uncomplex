package com.uncomplex.roadmap.entity;

import com.uncomplex.roadmap.model.Difficulty;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "roadmap_node")
public class RoadmapNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "roadmap_id")
    private Roadmap roadmap;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 2000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @OneToMany(mappedBy = "node", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<NodeResource> resources = new ArrayList<>();

    protected RoadmapNode() {
    }

    public RoadmapNode(String name, String description, String reason, Difficulty difficulty,
                       int estimatedMinutes, int orderIndex) {
        this.name = name;
        this.description = description;
        this.reason = reason;
        this.difficulty = difficulty;
        this.estimatedMinutes = estimatedMinutes;
        this.orderIndex = orderIndex;
    }

    public void addResource(NodeResource resource) {
        resource.setNode(this);
        resources.add(resource);
    }

    void setRoadmap(Roadmap roadmap) {
        this.roadmap = roadmap;
    }

    public Long getId() {
        return id;
    }

    public Roadmap getRoadmap() {
        return roadmap;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getReason() {
        return reason;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public List<NodeResource> getResources() {
        return resources;
    }
}
