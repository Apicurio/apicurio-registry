package io.apicurio.registry.storage.impl.sql;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

/**
 * Can parse a DDL into a list of individual statements.
 */
public class DdlParser {

    /**
     * Constructor.
     */
    public DdlParser() {
    }

    /**
     * @param ddlFile
     */
    public List<String> parse(File ddlFile) {
        try (InputStream is = new FileInputStream(ddlFile)) {
            return parse(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @param ddlStream
     * @throws IOException
     */
    public List<String> parse(InputStream ddlStream) throws IOException {
        List<String> rval = new LinkedList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(ddlStream, StandardCharsets.UTF_8));
        String line;
        StringBuilder builder = new StringBuilder();
        boolean isInMultiLineStatement = false;
        while ( (line = reader.readLine()) != null) {
            if (line.startsWith("--")) {
                continue;
            }
            if (line.trim().isEmpty() && !isInMultiLineStatement) {
                continue;
            }
            if (line.trim().isEmpty() && isInMultiLineStatement) {
                isInMultiLineStatement = false;
            }
            if (line.endsWith("'") || line.endsWith("(")) {
                isInMultiLineStatement = true;
            }
            if (line.startsWith("'") || line.startsWith(")")) {
                isInMultiLineStatement = false;
            }
            if (line.startsWith("CREATE FUNCTION")) {
                isInMultiLineStatement = true;
            }
            builder.append(line);
            builder.append("\n");

            if (!isInMultiLineStatement) {
                String sqlStatement = builder.toString().trim();
                if (sqlStatement.endsWith(";")) {
                    sqlStatement = sqlStatement.substring(0, sqlStatement.length() - 1);
                }
                rval.add(sqlStatement);
                builder = new StringBuilder();
            }
        }
        return rval;
    }

}
