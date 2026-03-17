package com.javaclaw.core.model;

import java.util.List;

public record AgentDefinition(
        String name,
        String systemPrompt,
        List<String> allowedTools
) {
}
