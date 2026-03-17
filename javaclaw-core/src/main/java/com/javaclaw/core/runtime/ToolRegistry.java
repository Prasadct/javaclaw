package com.javaclaw.core.runtime;

import com.javaclaw.core.model.ToolDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    public void register(ToolDefinition tool) {
        tools.put(tool.name(), tool);
    }

    public Optional<ToolDefinition> get(String toolName) {
        return Optional.ofNullable(tools.get(toolName));
    }

    public List<ToolDefinition> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(tools.values()));
    }

    public String describeAll() {
        if (tools.isEmpty()) {
            return "No tools available.";
        }
        var sb = new StringBuilder("Available tools:\n");
        for (var tool : tools.values()) {
            sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append("\n");
        }
        return sb.toString();
    }
}
