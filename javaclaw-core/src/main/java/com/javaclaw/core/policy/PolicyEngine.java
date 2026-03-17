package com.javaclaw.core.policy;

import com.javaclaw.core.model.AgentDefinition;

public interface PolicyEngine {

    PolicyDecision evaluate(AgentDefinition agent, String toolName, String input);
}
