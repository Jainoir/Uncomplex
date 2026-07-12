package com.uncomplex.ai;

import com.uncomplex.TestFixtures;
import com.uncomplex.ai.draft.PrerequisiteDraft;
import com.uncomplex.ai.draft.RoadmapDraft;
import com.uncomplex.resource.ResourceCredibilityService;
import com.uncomplex.roadmap.model.Difficulty;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadmapDraftValidatorTest {

    private final RoadmapDraftValidator validator = new RoadmapDraftValidator(
            TestFixtures.appProperties(),
            new ResourceCredibilityService(TestFixtures.appProperties()));

    @Test
    void acceptsAValidDraft() {
        var sanitized = validator.validate(TestFixtures.draftWithPrerequisites(5));

        assertThat(sanitized.title()).isEqualTo("Learn Testing");
        assertThat(sanitized.prerequisites()).hasSize(5);
    }

    @Test
    void rejectsTooFewPrerequisites() {
        assertThatThrownBy(() -> validator.validate(TestFixtures.draftWithPrerequisites(3)))
                .isInstanceOf(InvalidDraftException.class)
                .hasMessageContaining("between 4 and 8");
    }

    @Test
    void rejectsTooManyPrerequisites() {
        assertThatThrownBy(() -> validator.validate(TestFixtures.draftWithPrerequisites(9)))
                .isInstanceOf(InvalidDraftException.class);
    }

    @Test
    void dropsResourcesFromNonAllowlistedDomains() {
        var draft = new RoadmapDraft("T", "S", List.of(
                TestFixtures.prerequisite("A", 1,
                        List.of(TestFixtures.allowedResource(), TestFixtures.disallowedResource())),
                TestFixtures.prerequisite("B", 2, List.of(TestFixtures.disallowedResource())),
                TestFixtures.prerequisite("C", 3, List.of()),
                TestFixtures.prerequisite("D", 4, null)));

        var sanitized = validator.validate(draft);

        assertThat(sanitized.prerequisites().get(0).resources()).hasSize(1);
        assertThat(sanitized.prerequisites().get(1).resources()).isEmpty();
        assertThat(sanitized.prerequisites().get(3).resources()).isEmpty();
    }

    @Test
    void ordersPrerequisitesByModelGivenPosition() {
        var draft = new RoadmapDraft("T", "S", List.of(
                TestFixtures.prerequisite("Third", 3, List.of()),
                TestFixtures.prerequisite("First", 1, List.of()),
                TestFixtures.prerequisite("Fourth", 4, List.of()),
                TestFixtures.prerequisite("Second", 2, List.of())));

        var sanitized = validator.validate(draft);

        assertThat(sanitized.prerequisites())
                .extracting(RoadmapDraftValidator.SanitizedPrerequisite::name)
                .containsExactly("First", "Second", "Third", "Fourth");
    }

    @Test
    void clampsUnrealisticTimeEstimates() {
        var draft = new RoadmapDraft("T", "S", List.of(
                new PrerequisiteDraft("A", "d", "r", Difficulty.BEGINNER, 100_000, 1, List.of()),
                new PrerequisiteDraft("B", "d", "r", Difficulty.BEGINNER, 0, 2, List.of()),
                TestFixtures.prerequisite("C", 3, List.of()),
                TestFixtures.prerequisite("D", 4, List.of())));

        var sanitized = validator.validate(draft);

        assertThat(sanitized.prerequisites().get(0).estimatedMinutes()).isEqualTo(480);
        assertThat(sanitized.prerequisites().get(1).estimatedMinutes()).isEqualTo(5);
    }

    @Test
    void rejectsBlankFields() {
        var draft = new RoadmapDraft("T", "S", List.of(
                new PrerequisiteDraft("  ", "d", "r", Difficulty.BEGINNER, 30, 1, List.of()),
                TestFixtures.prerequisite("B", 2, List.of()),
                TestFixtures.prerequisite("C", 3, List.of()),
                TestFixtures.prerequisite("D", 4, List.of())));

        assertThatThrownBy(() -> validator.validate(draft))
                .isInstanceOf(InvalidDraftException.class);
    }
}
