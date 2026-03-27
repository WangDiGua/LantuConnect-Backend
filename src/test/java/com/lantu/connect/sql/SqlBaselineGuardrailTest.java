package com.lantu.connect.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlBaselineGuardrailTest {

    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile("CREATE TABLE\\s+`([^`]+)`", Pattern.CASE_INSENSITIVE);
    private static final String BASELINE_FILE = "lantu_connect.sql";
    private static final Set<String> HISTORY_FILES = Set.of("2026.3.24.10.04最新版本数据库数据.sql");

    @Test
    void allCreateTableStatementsShouldBeCoveredByBaseline() throws IOException {
        Path sqlDir = Path.of("sql");
        Path baselinePath = sqlDir.resolve(BASELINE_FILE);
        Set<String> baselineTables = parseCreateTables(Files.readString(baselinePath, StandardCharsets.UTF_8));

        Set<String> uncovered = new HashSet<>();
        try (Stream<Path> stream = Files.walk(sqlDir)) {
            List<Path> sqlFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .filter(p -> !p.getFileName().toString().equals(BASELINE_FILE))
                    .filter(p -> !HISTORY_FILES.contains(p.getFileName().toString()))
                    .toList();
            for (Path file : sqlFiles) {
                Set<String> tables = parseCreateTables(Files.readString(file, StandardCharsets.UTF_8));
                for (String table : tables) {
                    if (!baselineTables.contains(table)) {
                        uncovered.add(sqlDir.relativize(file) + " -> " + table);
                    }
                }
            }
        }

        assertTrue(uncovered.isEmpty(),
                "存在未回写到 lantu_connect.sql 的建表语句: " + uncovered);
    }

    private static Set<String> parseCreateTables(String sqlText) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(sqlText);
        Set<String> tables = new HashSet<>();
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }
}
