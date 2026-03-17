package com.javaclaw.core.tools;

import com.javaclaw.core.model.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCodeToolTest {

    @TempDir
    Path tempDir;

    private SearchCodeTool tool;

    @BeforeEach
    void setUp() throws IOException {
        tool = new SearchCodeTool(tempDir);

        // Create test files
        Files.writeString(tempDir.resolve("App.java"), """
                public class App {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);

        Path sub = tempDir.resolve("sub");
        Files.createDirectories(sub);
        Files.writeString(sub.resolve("Util.java"), """
                public class Util {
                    public static String greet() {
                        return "Hello";
                    }
                }
                """);
    }

    @Test
    void definitionHasCorrectMetadata() {
        var def = tool.definition();
        assertThat(def.name()).isEqualTo("search_code");
        assertThat(def.riskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    void findsMatchesAcrossFiles() {
        String result = tool.execute("""
                {"pattern": "Hello", "directory": "."}
                """);

        assertThat(result).contains("Found 2 match(es)");
        assertThat(result).contains("App.java");
        assertThat(result).contains("Util.java");
    }

    @Test
    void findsMatchesWithRegex() {
        String result = tool.execute("""
                {"pattern": "public static .+ main", "directory": "."}
                """);

        assertThat(result).contains("Found 1 match(es)");
        assertThat(result).contains("App.java");
    }

    @Test
    void searchesInSubdirectory() {
        String result = tool.execute("""
                {"pattern": "greet", "directory": "sub"}
                """);

        assertThat(result).contains("Found 1 match(es)");
        assertThat(result).contains("Util.java");
    }

    @Test
    void returnsNoMatchesMessage() {
        String result = tool.execute("""
                {"pattern": "nonexistent_xyz", "directory": "."}
                """);

        assertThat(result).contains("No matches found");
    }

    @Test
    void blocksPathTraversal() {
        String result = tool.execute("""
                {"pattern": "test", "directory": "../../etc"}
                """);

        assertThat(result).contains("ERROR").contains("Access denied");
    }

    @Test
    void handlesInvalidJson() {
        String result = tool.execute("not json");

        assertThat(result).contains("ERROR").contains("Invalid JSON");
    }

    @Test
    void handlesInvalidRegex() {
        String result = tool.execute("""
                {"pattern": "[invalid", "directory": "."}
                """);

        assertThat(result).contains("ERROR").contains("Invalid regex");
    }

    @Test
    void handlesMissingPattern() {
        String result = tool.execute("""
                {"directory": "."}
                """);

        assertThat(result).contains("ERROR").contains("'pattern' field is required");
    }

    @Test
    void handlesNonexistentDirectory() {
        String result = tool.execute("""
                {"pattern": "test", "directory": "nope"}
                """);

        assertThat(result).contains("ERROR").contains("Directory not found");
    }
}
