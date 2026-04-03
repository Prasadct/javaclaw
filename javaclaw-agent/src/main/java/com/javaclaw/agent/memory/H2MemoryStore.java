package com.javaclaw.agent.memory;

import com.javaclaw.core.memory.MemoryEntry;
import com.javaclaw.core.memory.MemoryStore;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class H2MemoryStore implements MemoryStore {

    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MemoryEntry> ROW_MAPPER = (rs, rowNum) -> new MemoryEntry(
            rs.getString("id"),
            rs.getString("category"),
            rs.getString("mem_key"),
            rs.getString("mem_value"),
            rs.getString("source"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    public H2MemoryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS javaclaw_memory (
                    id VARCHAR(36) NOT NULL,
                    category VARCHAR(255) NOT NULL,
                    mem_key VARCHAR(255) NOT NULL,
                    mem_value CLOB,
                    source VARCHAR(255),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    PRIMARY KEY (category, mem_key)
                )
                """);
    }

    @Override
    public void save(MemoryEntry entry) {
        jdbcTemplate.update("""
                MERGE INTO javaclaw_memory (id, category, mem_key, mem_value, source, created_at, updated_at)
                KEY (category, mem_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                entry.id(),
                entry.category(),
                entry.key(),
                entry.value(),
                entry.source(),
                Timestamp.from(entry.createdAt()),
                Timestamp.from(entry.updatedAt()));
    }

    @Override
    public Optional<MemoryEntry> findByKey(String category, String key) {
        List<MemoryEntry> results = jdbcTemplate.query(
                "SELECT * FROM javaclaw_memory WHERE category = ? AND mem_key = ?",
                ROW_MAPPER, category, key);
        return results.stream().findFirst();
    }

    @Override
    public List<MemoryEntry> findByCategory(String category) {
        return jdbcTemplate.query(
                "SELECT * FROM javaclaw_memory WHERE category = ? ORDER BY updated_at DESC",
                ROW_MAPPER, category);
    }

    @Override
    public List<MemoryEntry> findAll() {
        return jdbcTemplate.query("SELECT * FROM javaclaw_memory ORDER BY category, mem_key", ROW_MAPPER);
    }

    @Override
    public void deleteByKey(String category, String key) {
        jdbcTemplate.update("DELETE FROM javaclaw_memory WHERE category = ? AND mem_key = ?", category, key);
    }

    @Override
    public void wipeAll() {
        jdbcTemplate.update("DELETE FROM javaclaw_memory");
    }

    @Override
    public List<MemoryEntry> findRecent(int limit) {
        return jdbcTemplate.query(
                "SELECT * FROM javaclaw_memory ORDER BY updated_at DESC LIMIT ?",
                ROW_MAPPER, limit);
    }
}
