package com.uncomplex.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.uncomplex.TestFixtures;
import com.uncomplex.roadmap.model.ExperienceLevel;
import com.uncomplex.roadmap.model.LearningGoal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AnthropicRoadmapGeneratorTest {
    @Test
    void providerTimeoutBecomesAGenerationFailure() {
        AnthropicClient client = mock(AnthropicClient.class, RETURNS_DEEP_STUBS);
        when(client.messages().create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new AnthropicIoException("timeout"));
        var generator = new AnthropicRoadmapGenerator(client, TestFixtures.appProperties());
        assertThatThrownBy(() -> generator.generate("Docker", ExperienceLevel.BEGINNER, LearningGoal.BUILD_A_PROJECT))
                .isInstanceOf(AiGenerationException.class).hasCauseInstanceOf(AnthropicIoException.class);
    }
}
