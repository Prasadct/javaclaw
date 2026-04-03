package com.javaclaw.core.memory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

public class MemoryService {

    private final MemoryStore store;

    public MemoryService(MemoryStore store) {
        this.store = store;
    }

    public void rememberRepo(String alias, String url) {
        save("repo", alias, url, "user");
    }

    public void rememberPreference(String key, String value) {
        save("preference", key, value, "user");
    }

    public void remember(String category, String key, String value) {
        save(category, key, value, "api");
    }

    public void recordTaskOutcome(String taskSummary, String outcome) {
        String key = "task_" + System.currentTimeMillis();
        String value = "{\"summary\":\"" + escapeJson(taskSummary) + "\",\"outcome\":\"" + escapeJson(outcome) + "\"}";
        save("task_outcome", key, value, "agent");
    }

    public Optional<String> recall(String category, String key) {
        return store.findByKey(category, key).map(MemoryEntry::value);
    }

    public String summarizeContext() {
        List<MemoryEntry> all = store.findAll();
        if (all.isEmpty()) {
            return "";
        }

        Map<String, List<MemoryEntry>> grouped = new TreeMap<>();
        for (MemoryEntry entry : all) {
            grouped.computeIfAbsent(entry.category(), k -> new java.util.ArrayList<>()).add(entry);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== Agent Memory Context ===\n");
        for (var categoryEntry : grouped.entrySet()) {
            sb.append("\n[").append(categoryEntry.getKey()).append("]\n");
            for (MemoryEntry entry : categoryEntry.getValue()) {
                sb.append("  ").append(entry.key()).append(": ").append(entry.value()).append("\n");
            }
        }
        return sb.toString();
    }

    public void wipeAll() {
        store.wipeAll();
    }

    public List<MemoryEntry> auditLog() {
        List<MemoryEntry> all = store.findAll();
        return all.stream()
                .sorted(Comparator.comparing(MemoryEntry::updatedAt).reversed())
                .toList();
    }

    private void save(String category, String key, String value, String source) {
        Instant now = Instant.now();
        Optional<MemoryEntry> existing = store.findByKey(category, key);
        String id = existing.map(MemoryEntry::id).orElse(UUID.randomUUID().toString());
        Instant createdAt = existing.map(MemoryEntry::createdAt).orElse(now);
        store.save(new MemoryEntry(id, category, key, value, source, createdAt, now));
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
