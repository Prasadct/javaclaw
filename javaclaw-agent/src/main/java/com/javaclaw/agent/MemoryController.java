package com.javaclaw.agent;

import com.javaclaw.core.memory.MemoryEntry;
import com.javaclaw.core.memory.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    @Autowired(required = false)
    private MemoryService memoryService;

    @GetMapping
    public List<MemoryEntry> getAll() {
        if (memoryService == null) {
            return Collections.emptyList();
        }
        return memoryService.getAllEntries();
    }

    @PostMapping
    public Map<String, String> save(@RequestBody Map<String, String> request) {
        if (memoryService == null) {
            return Map.of("status", "disabled");
        }

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
    public Map<String, String> wipeAll(@RequestBody Map<String, String> request) {
        if (memoryService == null) {
            return Map.of("status", "disabled");
        }
        if (!"wipe".equals(request.get("confirm"))) {
            throw new IllegalArgumentException("Request body must contain {\"confirm\":\"wipe\"} to confirm deletion");
        }
        log.warn("Wiping all memory entries");
        memoryService.wipeAll();
        return Map.of("status", "wiped");
    }

    @GetMapping("/context")
    public String getContext() {
        if (memoryService == null) {
            return "";
        }
        return memoryService.summarizeContext();
    }
}
