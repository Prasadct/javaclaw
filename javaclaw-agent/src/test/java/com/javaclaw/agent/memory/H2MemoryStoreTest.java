package com.javaclaw.agent.memory;

import com.javaclaw.core.memory.MemoryEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class H2MemoryStoreTest {

    private H2MemoryStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        var jdbcTemplate = new JdbcTemplate(dataSource);
        store = new H2MemoryStore(jdbcTemplate);
        store.initSchema();
    }

    @Test
    void saveAndFindByKey_roundTrip() {
        Instant now = Instant.now();
        var entry = new MemoryEntry(UUID.randomUUID().toString(), "repo", "myapp",
                "https://github.com/org/myapp", "user", now, now);

        store.save(entry);

        Optional<MemoryEntry> found = store.findByKey("repo", "myapp");
        assertThat(found).isPresent();
        assertThat(found.get().value()).isEqualTo("https://github.com/org/myapp");
        assertThat(found.get().category()).isEqualTo("repo");
        assertThat(found.get().source()).isEqualTo("user");
    }

    @Test
    void upsert_sameCategoryAndKey_updatesValueNoDuplicate() {
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-06-01T00:00:00Z");

        store.save(new MemoryEntry("id1", "preference", "theme", "dark", "user", t1, t1));
        store.save(new MemoryEntry("id1", "preference", "theme", "light", "user", t1, t2));

        List<MemoryEntry> all = store.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).value()).isEqualTo("light");
    }

    @Test
    void wipeAll_clearsAllRows() {
        Instant now = Instant.now();
        store.save(new MemoryEntry("id1", "repo", "a", "v1", "user", now, now));
        store.save(new MemoryEntry("id2", "repo", "b", "v2", "user", now, now));

        store.wipeAll();

        assertThat(store.findAll()).isEmpty();
    }

    @Test
    void findRecent_returnsAtMostLimit_orderedByUpdatedAtDesc() {
        Instant t1 = Instant.parse("2025-01-01T00:00:00Z");
        Instant t2 = Instant.parse("2025-03-01T00:00:00Z");
        Instant t3 = Instant.parse("2025-06-01T00:00:00Z");
        Instant t4 = Instant.parse("2025-09-01T00:00:00Z");

        store.save(new MemoryEntry("id1", "repo", "a", "v1", "user", t1, t1));
        store.save(new MemoryEntry("id2", "repo", "b", "v2", "user", t2, t2));
        store.save(new MemoryEntry("id3", "repo", "c", "v3", "user", t3, t3));
        store.save(new MemoryEntry("id4", "repo", "d", "v4", "user", t4, t4));

        List<MemoryEntry> recent = store.findRecent(3);

        assertThat(recent).hasSize(3);
        assertThat(recent.get(0).key()).isEqualTo("d");
        assertThat(recent.get(1).key()).isEqualTo("c");
        assertThat(recent.get(2).key()).isEqualTo("b");
    }

    @Test
    void findByCategory_returnsOnlyMatchingCategory() {
        Instant now = Instant.now();
        store.save(new MemoryEntry("id1", "repo", "a", "v1", "user", now, now));
        store.save(new MemoryEntry("id2", "preference", "b", "v2", "user", now, now));
        store.save(new MemoryEntry("id3", "repo", "c", "v3", "user", now, now));

        List<MemoryEntry> repos = store.findByCategory("repo");

        assertThat(repos).hasSize(2);
        assertThat(repos).allMatch(e -> "repo".equals(e.category()));
    }

    @Test
    void deleteByKey_removesSpecificEntry() {
        Instant now = Instant.now();
        store.save(new MemoryEntry("id1", "repo", "a", "v1", "user", now, now));
        store.save(new MemoryEntry("id2", "repo", "b", "v2", "user", now, now));

        store.deleteByKey("repo", "a");

        assertThat(store.findByKey("repo", "a")).isEmpty();
        assertThat(store.findByKey("repo", "b")).isPresent();
    }
}
