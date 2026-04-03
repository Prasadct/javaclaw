package com.javaclaw.core.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class MemoryServiceTest {

    private MemoryStore store;
    private MemoryService service;

    @BeforeEach
    void setUp() {
        store = mock(MemoryStore.class);
        service = new MemoryService(store);
        when(store.findByKey(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void rememberRepo_savesWithCategoryRepo() {
        service.rememberRepo("myapp", "https://github.com/org/myapp");

        verify(store).save(argThat(entry ->
                "repo".equals(entry.category()) &&
                "myapp".equals(entry.key()) &&
                "https://github.com/org/myapp".equals(entry.value()) &&
                "user".equals(entry.source())
        ));
    }

    @Test
    void rememberPreference_savesWithCategoryPreference() {
        service.rememberPreference("language", "java");

        verify(store).save(argThat(entry ->
                "preference".equals(entry.category()) &&
                "language".equals(entry.key()) &&
                "java".equals(entry.value()) &&
                "user".equals(entry.source())
        ));
    }

    @Test
    void recall_returnsValueFromFindByKey() {
        Instant now = Instant.now();
        when(store.findByKey("repo", "myapp")).thenReturn(
                Optional.of(new MemoryEntry("id1", "repo", "myapp", "https://github.com/org/myapp", "user", now, now))
        );

        Optional<String> result = service.recall("repo", "myapp");

        assertThat(result).isPresent().contains("https://github.com/org/myapp");
    }

    @Test
    void summarizeContext_returnsNonEmptyWhenEntriesExist() {
        Instant now = Instant.now();
        when(store.findAll()).thenReturn(List.of(
                new MemoryEntry("id1", "repo", "myapp", "https://github.com/org/myapp", "user", now, now),
                new MemoryEntry("id2", "preference", "language", "java", "user", now, now)
        ));

        String context = service.summarizeContext();

        assertThat(context).isNotBlank();
        assertThat(context).contains("[repo]");
        assertThat(context).contains("[preference]");
        assertThat(context).contains("myapp");
        assertThat(context).contains("java");
    }

    @Test
    void summarizeContext_returnsEmptyStringWhenStoreIsEmpty() {
        when(store.findAll()).thenReturn(List.of());

        String context = service.summarizeContext();

        assertThat(context).isEmpty();
    }

    @Test
    void wipeAll_delegatesToStore() {
        service.wipeAll();

        verify(store).wipeAll();
    }

    @Test
    void recordTaskOutcome_savesWithCategoryTaskOutcome() {
        service.recordTaskOutcome("Deploy app", "completed");

        verify(store).save(argThat(entry ->
                "task_outcome".equals(entry.category()) &&
                entry.key().startsWith("task_") &&
                entry.value().contains("Deploy app") &&
                entry.value().contains("completed") &&
                "agent".equals(entry.source())
        ));
    }

    @Test
    void auditLog_returnsSortedByUpdatedAtDesc() {
        Instant older = Instant.parse("2025-01-01T00:00:00Z");
        Instant newer = Instant.parse("2025-06-01T00:00:00Z");
        when(store.findAll()).thenReturn(List.of(
                new MemoryEntry("id1", "repo", "old", "v1", "user", older, older),
                new MemoryEntry("id2", "repo", "new", "v2", "user", newer, newer)
        ));

        List<MemoryEntry> log = service.auditLog();

        assertThat(log).hasSize(2);
        assertThat(log.get(0).key()).isEqualTo("new");
        assertThat(log.get(1).key()).isEqualTo("old");
    }
}
