package com.javaclaw.agent;

import com.javaclaw.core.memory.MemoryEntry;
import com.javaclaw.core.memory.MemoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    public List<MemoryEntry> getAll() {
        return memoryService.auditLog();
    }

    @PostMapping
    public Map<String, String> save(@RequestBody Map<String, String> request) {
        String category = request.get("category");
        String key = request.get("key");
        String value = request.get("value");

        if (category == null || key == null || value == null) {
            throw new IllegalArgumentException("'category', 'key', and 'value' fields are required");
        }

        memoryService.remember(category, key, value);
        return Map.of("status", "saved", "category", category, "key", key);
    }

    @DeleteMapping
    public Map<String, String> wipeAll() {
        memoryService.wipeAll();
        return Map.of("status", "wiped");
    }

    @GetMapping("/context")
    public String getContext() {
        return memoryService.summarizeContext();
    }
}
