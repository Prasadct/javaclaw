package com.javaclaw.core.memory;

import java.util.List;
import java.util.Optional;

public interface MemoryStore {

    void save(MemoryEntry entry);

    Optional<MemoryEntry> findByKey(String category, String key);

    List<MemoryEntry> findByCategory(String category);

    List<MemoryEntry> findAll();

    void deleteByKey(String category, String key);

    void wipeAll();

    List<MemoryEntry> findRecent(int limit);
}
