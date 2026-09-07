package com.uncomplex;

import com.uncomplex.ai.AiRoadmapGenerator;
import com.uncomplex.auth.AuthService;
import com.uncomplex.exception.InvalidCredentialsException;
import com.uncomplex.library.service.LibraryService;
import com.uncomplex.roadmap.service.RoadmapService;
import com.uncomplex.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static com.uncomplex.roadmap.model.ExperienceLevel.BEGINNER;
import static com.uncomplex.roadmap.model.LearningGoal.BUILD_A_PROJECT;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "app.link-health.enabled=false")
@ActiveProfiles("test")
class ConcurrencyIntegrationTest {
    @Autowired RoadmapService roadmaps;
    @Autowired LibraryService library;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired com.uncomplex.config.DatabaseMutex mutex;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @MockitoBean AiRoadmapGenerator generator;

    @BeforeEach
    void setUp() {
        when(generator.generate(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(100); // Widen the cache-miss race while the first request generates.
            return TestFixtures.draftWithPrerequisites(4);
        });
    }

    @Test
    void simultaneousCacheMissesGenerateOnlyOnce() throws Exception {
        String topic = "Concurrent " + UUID.randomUUID();
        var results = concurrently(() -> roadmaps.getOrGenerate(topic, null, BEGINNER, BUILD_A_PROJECT).getId());
        assertThat(results.get(0)).isEqualTo(results.get(1));
        verify(generator, times(1)).generate(topic, null, BEGINNER, BUILD_A_PROJECT);
    }

    @Test
    void slowGenerationDoesNotBlockAnotherKeyOnTheOldStripe() throws Exception {
        String slowTopic = "slow-" + UUID.randomUUID();
        String slowKey = "generation:" + com.uncomplex.roadmap.service.CacheKeys.of(slowTopic, null, BEGINNER, BUILD_A_PROJECT);
        String otherTopic = java.util.stream.IntStream.range(0, 10000).mapToObj(i -> "other-" + i)
                .filter(topic -> Math.floorMod(("generation:" + com.uncomplex.roadmap.service.CacheKeys.of(topic, null,
                        BEGINNER, BUILD_A_PROJECT)).hashCode(), 256) == Math.floorMod(slowKey.hashCode(), 256))
                .findFirst().orElseThrow();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(generator.generate(eq(slowTopic), any(), any(), any())).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Release timeout");
            return TestFixtures.draftWithPrerequisites(4);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> roadmaps.getOrGenerate(slowTopic, null, BEGINNER, BUILD_A_PROJECT));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                var other = executor.submit(() -> roadmaps.getOrGenerate(otherTopic, null, BEGINNER, BUILD_A_PROJECT));
                assertThat(other.get(3, TimeUnit.SECONDS).getId()).isNotNull();
                assertThat(first.isDone()).isFalse();
            } finally { release.countDown(); }
            first.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void matchingSlowGenerationHasBoundedWaitAndReleasesAfterCommit() throws Exception {
        String topic = "bounded-" + UUID.randomUUID();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(generator.generate(eq(topic), any(), any(), any())).thenAnswer(invocation -> {
            started.countDown();
            if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Release timeout");
            return TestFixtures.draftWithPrerequisites(4);
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var first = executor.submit(() -> roadmaps.getOrGenerate(topic, null, BEGINNER, BUILD_A_PROJECT));
            try {
                assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                long start = System.nanoTime();
                assertThatThrownBy(() -> roadmaps.getOrGenerate(topic, null, BEGINNER, BUILD_A_PROJECT))
                        .isInstanceOf(com.uncomplex.exception.OperationBusyException.class);
                assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(3000);
            } finally { release.countDown(); }
            var saved = first.get(5, TimeUnit.SECONDS);
            assertThat(roadmaps.getOrGenerate(topic, null, BEGINNER, BUILD_A_PROJECT).getId()).isEqualTo(saved.getId());
        }
        verify(generator, times(1)).generate(topic, null, BEGINNER, BUILD_A_PROJECT);
    }

    @Test
    void cachedResultDoesNotAcquireAGenerationLock() throws Exception {
        String topic = "cached-" + UUID.randomUUID();
        var cached = roadmaps.getOrGenerate(topic, null, BEGINNER, BUILD_A_PROJECT);
        var held = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var owner = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        mutex.acquire("generation:" + com.uncomplex.roadmap.service.CacheKeys.of(topic, null, BEGINNER, BUILD_A_PROJECT));
                        held.countDown();
                        try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Release timeout"); }
                        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                    }));
            try {
                assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
                var read = executor.submit(() -> roadmaps.getOrGenerate(topic, null, BEGINNER, BUILD_A_PROJECT));
                assertThat(read.get(3, TimeUnit.SECONDS).getId()).isEqualTo(cached.getId());
            } finally { release.countDown(); }
            owner.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void simultaneousSavesAndCompletionsAreIdempotent() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        auth.register(email, "a-strong-password");
        Long userId = users.findByEmail(email).orElseThrow().getId();
        var roadmap = roadmaps.getOrGenerate("Library " + UUID.randomUUID(), null, BEGINNER, BUILD_A_PROJECT);
        concurrently(() -> library.saveByShareToken(userId, roadmap.getShareToken()));
        assertThat(library.listFor(userId)).hasSize(1);
        Long nodeId = roadmap.getNodes().getFirst().getId();
        concurrently(() -> library.setNodeProgress(userId, roadmap.getId(), nodeId, true));
        assertThat(library.getWithProgress(userId, roadmap.getId()).progress().completedCount()).isEqualTo(1);
    }

    @Test
    void simultaneousRefreshCannotConsumeTheSameTokenTwice() throws Exception {
        var session = auth.register(UUID.randomUUID() + "@example.com", "a-strong-password");
        var results = concurrently(() -> {
            try { return auth.refresh(session.refreshToken()).refreshToken(); }
            catch (InvalidCredentialsException expected) { return "rejected"; }
        });
        assertThat(results.stream().filter("rejected"::equals).count()).isEqualTo(1);
        String issued = results.stream().filter(s -> !s.equals("rejected")).findFirst().orElseThrow();
        assertThatThrownBy(() -> auth.refresh(issued)).isInstanceOf(InvalidCredentialsException.class);
    }

    private <T> List<T> concurrently(Callable<T> work) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<T> task = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return work.call();
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }
}
