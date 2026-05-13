package com.feijimiao.xianyuassistant.config.rag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicAIChatClientManagerTest {

    @Test
    void openAiBaseUrlKeepsExistingV1Version() {
        assertEquals(
                "https://sun.meowai.net/v1",
                DynamicAIChatClientManager.normalizeOpenAiBaseUrl(
                        "https://sun.meowai.net/v1",
                        "https://api.openai.com/v1"
                )
        );
    }

    @Test
    void openAiBaseUrlKeepsProviderSpecificVersionPath() {
        assertEquals(
                "https://api.lkeap.cloud.tencent.com/plan/v3",
                DynamicAIChatClientManager.normalizeOpenAiBaseUrl(
                        "https://api.lkeap.cloud.tencent.com/plan/v3",
                        "https://api.openai.com/v1"
                )
        );
    }

    @Test
    void springAiEndpointUsesConfiguredVersionPathAndPlainChatPath() {
        DynamicAIChatClientManager.OpenAiEndpoint endpoint =
                DynamicAIChatClientManager.resolveOpenAiEndpoint(
                        "https://api.lkeap.cloud.tencent.com/plan/v3",
                        "https://api.openai.com/v1"
                );

        assertEquals("https://api.lkeap.cloud.tencent.com/plan/v3", endpoint.getBaseUrl());
        assertEquals("/chat/completions", endpoint.getCompletionsPath());
    }

    @Test
    void plainDomainDefaultsToV1Endpoint() {
        DynamicAIChatClientManager.OpenAiEndpoint endpoint =
                DynamicAIChatClientManager.resolveOpenAiEndpoint(
                        "https://sun.meowai.net",
                        "https://api.openai.com/v1"
                );

        assertEquals("https://sun.meowai.net/v1", endpoint.getBaseUrl());
        assertEquals("/chat/completions", endpoint.getCompletionsPath());
    }
}
