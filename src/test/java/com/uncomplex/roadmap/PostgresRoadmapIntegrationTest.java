package com.uncomplex.roadmap;

import com.uncomplex.roadmap.entity.Roadmap;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import com.uncomplex.roadmap.repository.RoadmapRepository;
import com.uncomplex.roadmap.service.RoadmapService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real Flyway migration and JPA mappings against actual PostgreSQL.
 * Skipped automatically when Docker is not available (e.g. on this dev machine);
 * runs in CI where Docker is present.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PostgresRoadmapIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private RoadmapService service;

    @Autowired
    private RoadmapRepository repository;

    @Test
    void generatePersistAndReloadAgainstRealPostgres() {
        Roadmap generated = service.getOrGenerate("Database indexing",
                ExperienceLevel.INTERMEDIATE, LearningGoal.JOB_INTERVIEW);

        // Read back through the service (initializes the lazy graph inside the
        // transaction) — touching lazy collections on a bare repository result
        // outside a transaction would throw LazyInitializationException.
        Roadmap reloaded = service.getByShareToken(generated.getShareToken());

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getNodes()).hasSize(5);
        assertThat(reloaded.getEstimatedTotalMinutes()).isGreaterThan(0);

        // Second call for the same combination must not create a new row
        Roadmap again = service.getOrGenerate("Database Indexing",
                ExperienceLevel.INTERMEDIATE, LearningGoal.JOB_INTERVIEW);
        assertThat(again.getId()).isEqualTo(generated.getId());
        assertThat(repository.count()).isEqualTo(1);
    }
}
