package com.cholog.bootcamp;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.util.Map;

/**
 * Spring AI 1.1.x의 extra_body 직렬화 버그 workaround.
 *
 * Spring AI 1.1.1+에서 빈 extra_body가 직렬화되어 OpenAI API가 400을 반환하는 문제를 해결합니다.
 * Spring AI 1.1.3 또는 2.0.0 GA 릴리스 시 이 클래스를 제거하세요.
 *
 * 이 파일은 수정할 필요가 없습니다.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5196">spring-ai#5196</a>
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.ai.openai.api.OpenAiApi")
public class OpenAiExtraBodyWorkaround {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer extraBodyMixinCustomizer() {
        return builder -> builder.mixIn(
                OpenAiApi.ChatCompletionRequest.class,
                ExtraBodyExcludeMixin.class
        );
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private abstract static class ExtraBodyExcludeMixin {
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("extra_body")
        abstract Map<String, Object> extraBody();
    }

    @Bean
    public RestClientCustomizer openAiExtraBodyStripper(final ObjectMapper objectMapper) {
        return builder -> builder.requestInterceptor((request, body, execution) -> {
            if (body.length > 0) {
                try {
                    final Map<String, Object> map = objectMapper.readValue(body, MAP_TYPE);
                    if (map.remove("extra_body") != null) {
                        body = objectMapper.writeValueAsBytes(map);
                    }
                } catch (final IOException ignored) {
                }
            }
            return execution.execute(request, body);
        });
    }
}
