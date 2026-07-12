package com.uncomplex.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.uncomplex.ai.AiRoadmapGenerator;
import com.uncomplex.ai.AnthropicRoadmapGenerator;
import com.uncomplex.ai.MockRoadmapGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic")
    public AnthropicClient anthropicClient(AppProperties properties) {
        String apiKey = properties.ai().anthropic().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "app.ai.provider=anthropic requires the ANTHROPIC_API_KEY environment variable");
        }
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.ai.provider", havingValue = "anthropic")
    public AiRoadmapGenerator anthropicRoadmapGenerator(AnthropicClient client, AppProperties properties) {
        log.info("AI provider: anthropic (model {})", properties.ai().anthropic().model());
        return new AnthropicRoadmapGenerator(client, properties);
    }

    @Bean
    @ConditionalOnMissingBean(AiRoadmapGenerator.class)
    public AiRoadmapGenerator mockRoadmapGenerator() {
        log.info("AI provider: mock (set app.ai.provider=anthropic and ANTHROPIC_API_KEY for real generation)");
        return new MockRoadmapGenerator();
    }
}
