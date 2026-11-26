package io.apicurio.registry.storage.impl.sql;

import io.apicurio.registry.utils.IoUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DdlStatementBoundaryTest {

    private static final String[] DIALECTS = { "h2", "mssql", "mysql", "postgresql" };

    private static final Set<String> STATEMENT_KEYWORDS = Set.of("CREATE", "ALTER", "UPDATE", "INSERT", "DROP",
            "DELETE", "IF");

    private static final String UPGRADER_DIRECTIVE = "UPGRADER:";

    @Test
    void everyStatementInEveryScriptIsComplete() throws Exception {
        int currentVersion = Integer.parseInt(readResource("db-version").trim());
        List<String> scripts = new ArrayList<>();
        for (String dialect : DIALECTS) {
            scripts.add(dialect + ".ddl");
            for (int version = 2; version <= currentVersion; version++) {
                String upgrade = "upgrades/" + version + "/" + dialect + ".upgrade.ddl";
                if (readResource(upgrade) != null) {
                    scripts.add(upgrade);
                }
            }
        }
        assertTrue(scripts.size() > DIALECTS.length, "Expected upgrade scripts as well as base scripts");

        List<String> broken = new ArrayList<>();
        for (String script : scripts) {
            for (String statement : parse(readResource(script))) {
                String first = statement.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
                if (!STATEMENT_KEYWORDS.contains(first) && !first.startsWith(UPGRADER_DIRECTIVE)) {
                    broken.add(script + ": " + statement.lines().findFirst().orElse(""));
                }
            }
        }
        assertEquals(List.of(), broken, "Statements split by DdlParser into fragments");
    }

    @Test
    void wrappedForeignKeyIsDetected() throws Exception {
        String ddl = "ALTER TABLE t ADD CONSTRAINT FK_1\n  FOREIGN KEY (a) REFERENCES u(a);\n";
        List<String> statements = parse(ddl);
        assertEquals(List.of("ALTER TABLE t ADD CONSTRAINT FK_1", "FOREIGN KEY (a) REFERENCES u(a)"),
                statements);
    }

   
    @Test
    void ifGuardedStatements_areParsedComplete() throws Exception {
        // Single-line IF NOT EXISTS guard (MSSQL CREATE INDEX style).
        String singleLine = "IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IDX_1') CREATE INDEX IDX_1 ON t(col);\n";
        List<String> single = parse(singleLine);
        assertEquals(1, single.size(), "Single-line IF NOT EXISTS guard must parse as one statement");
        assertTrue(single.get(0).startsWith("IF NOT EXISTS"),
                "Statement must start with IF NOT EXISTS: " + single.get(0));

        // Multi-line IF OBJECT_ID IS NULL guard (MSSQL CREATE TABLE style).
        String multiLine = "IF OBJECT_ID(N't', N'U') IS NULL CREATE TABLE t (\n  col INT NOT NULL\n);\n";
        List<String> multi = parse(multiLine);
        assertEquals(1, multi.size(), "Multi-line IF OBJECT_ID guard must parse as one statement");
        assertTrue(multi.get(0).startsWith("IF OBJECT_ID"),
                "Statement must start with IF OBJECT_ID: " + multi.get(0));
    }

    private static List<String> parse(String ddl) throws Exception {
        return new DdlParser().parse(new ByteArrayInputStream(ddl.getBytes(StandardCharsets.UTF_8)));
    }

    private static String readResource(String name) {
        try (InputStream is = DdlStatementBoundaryTest.class.getResourceAsStream(name)) {
            return is == null ? null : IoUtil.toString(is);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
