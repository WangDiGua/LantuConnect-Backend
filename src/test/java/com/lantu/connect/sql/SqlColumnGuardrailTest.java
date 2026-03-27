package com.lantu.connect.sql;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guardrail: validate raw string column names used in QueryWrapper against baseline schema.
 *
 * This catches mistakes like using target_type on t_usage_record.
 */
class SqlColumnGuardrailTest {

    private static final Path SCHEMA = Path.of("sql", "lantu_connect.sql");
    private static final Path MAIN_JAVA = Path.of("src", "main", "java");
    private static final Pattern TABLE_BLOCK = Pattern.compile(
            "CREATE TABLE\\s+`([^`]+)`\\s*\\((.*?)\\)\\s*ENGINE",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COLUMN_DEF = Pattern.compile("^\\s*`([^`]+)`", Pattern.MULTILINE);
    private static final Pattern TABLE_NAME_ANN = Pattern.compile("@TableName\\s*\\((?:value\\s*=\\s*)?\"([^\"]+)\"", Pattern.DOTALL);
    private static final Pattern CLASS_NAME = Pattern.compile("\\bclass\\s+(\\w+)\\b");
    private static final Pattern QUERY_WRAPPER_DECL = Pattern.compile(
            "QueryWrapper<\\s*(\\w+)\\s*>\\s+(\\w+)\\s*=\\s*new\\s+QueryWrapper<>\\s*\\(\\s*\\)");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"([^\"]+)\"");
    private static final Set<String> QUERY_WRAPPER_RAW_METHODS = Set.of("select", "groupBy", "orderByAsc", "orderByDesc");
    private static final Set<String> NON_COLUMN_TOKENS = Set.of(
            "*", "asc", "desc", "null", "true", "false");

    @Test
    void queryWrapperRawColumnsShouldExistInSchema() throws IOException {
        Map<String, Set<String>> schemaColumns = parseSchemaColumns(Files.readString(SCHEMA, StandardCharsets.UTF_8));
        Map<String, String> entityToTable = parseEntityToTableMap(MAIN_JAVA);
        List<String> mismatches = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(MAIN_JAVA)) {
            List<Path> javaFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, String> wrapperVarToEntity = parseQueryWrapperVariables(code);
                for (Map.Entry<String, String> entry : wrapperVarToEntity.entrySet()) {
                    String wrapperVar = entry.getKey();
                    String entity = entry.getValue();
                    String table = entityToTable.get(entity);
                    if (table == null) {
                        continue;
                    }
                    Set<String> columns = schemaColumns.get(table);
                    if (columns == null || columns.isEmpty()) {
                        continue;
                    }
                    collectMismatchesForWrapper(file, code, wrapperVar, table, columns, mismatches);
                }
            }
        }

        assertTrue(mismatches.isEmpty(),
                "Detected potential unknown QueryWrapper raw columns:\n" + String.join("\n", mismatches));
    }

    private static void collectMismatchesForWrapper(Path file,
                                                    String code,
                                                    String wrapperVar,
                                                    String table,
                                                    Set<String> columns,
                                                    List<String> mismatches) {
        for (String method : QUERY_WRAPPER_RAW_METHODS) {
            Pattern callPattern = Pattern.compile("\\b" + Pattern.quote(wrapperVar) + "\\." + method + "\\((.*?)\\)", Pattern.DOTALL);
            Matcher callMatcher = callPattern.matcher(code);
            while (callMatcher.find()) {
                String args = callMatcher.group(1);
                Matcher literalMatcher = STRING_LITERAL.matcher(args);
                while (literalMatcher.find()) {
                    String literal = literalMatcher.group(1);
                    for (String token : extractColumnCandidates(literal)) {
                        String normalized = token.toLowerCase(Locale.ROOT);
                        if (NON_COLUMN_TOKENS.contains(normalized)) {
                            continue;
                        }
                        if (!columns.contains(normalized)) {
                            mismatches.add(file + " :: " + wrapperVar + "." + method + "(\"" + literal + "\")"
                                    + " -> column `" + token + "` not in table `" + table + "`");
                        }
                    }
                }
            }
        }
    }

    private static Set<String> extractColumnCandidates(String literal) {
        Set<String> out = new HashSet<>();
        for (String rawPart : literal.split(",")) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }

            String lower = part.toLowerCase(Locale.ROOT);
            int asIdx = lower.indexOf(" as ");
            if (asIdx >= 0) {
                part = part.substring(0, asIdx).trim();
                lower = part.toLowerCase(Locale.ROOT);
            }

            Matcher dateFn = Pattern.compile("date\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE).matcher(part);
            if (dateFn.find()) {
                part = dateFn.group(1).trim();
                lower = part.toLowerCase(Locale.ROOT);
            }

            if (lower.contains("(") || lower.contains(")")) {
                continue;
            }

            if (part.contains(".")) {
                part = part.substring(part.lastIndexOf('.') + 1).trim();
            }

            part = part.replace("`", "").trim();
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }

    private static Map<String, Set<String>> parseSchemaColumns(String sql) {
        Map<String, Set<String>> tableColumns = new HashMap<>();
        Matcher tableMatcher = TABLE_BLOCK.matcher(sql);
        while (tableMatcher.find()) {
            String table = tableMatcher.group(1).toLowerCase(Locale.ROOT);
            String block = tableMatcher.group(2);
            Set<String> cols = new HashSet<>();
            Matcher colMatcher = COLUMN_DEF.matcher(block);
            while (colMatcher.find()) {
                cols.add(colMatcher.group(1).toLowerCase(Locale.ROOT));
            }
            tableColumns.put(table, cols);
        }
        return tableColumns;
    }

    private static Map<String, String> parseEntityToTableMap(Path root) throws IOException {
        Map<String, String> result = new HashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path file : stream.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".java")).toList()) {
                String code = Files.readString(file, StandardCharsets.UTF_8);
                Matcher tableMatcher = TABLE_NAME_ANN.matcher(code);
                Matcher classMatcher = CLASS_NAME.matcher(code);
                if (tableMatcher.find() && classMatcher.find()) {
                    result.put(classMatcher.group(1), tableMatcher.group(1).toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    private static Map<String, String> parseQueryWrapperVariables(String code) {
        Map<String, String> map = new HashMap<>();
        Matcher matcher = QUERY_WRAPPER_DECL.matcher(code);
        while (matcher.find()) {
            map.put(matcher.group(2), matcher.group(1));
        }
        return map;
    }
}
