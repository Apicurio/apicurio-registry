package io.apicurio.example.debezium;


import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Jakub Senko <em>m@jsenko.net</em>
 */
public enum Operation {

    CREATE("c"),
    READ("r"), // Used for snapshots, i.e. writes the initial (or incremental) state of database tables to each topic
    UPDATE("u"),
    DELETE("d"),
    TRUNCATE("t");

    @Getter
    private String op;

    Operation(String op) {
        this.op = op;
    }

    private final static Map<String, Operation> CONSTANTS = new HashMap<>();

    static {
        for (Operation c : values()) {
            CONSTANTS.put(c.op, c);
        }
    }

    public static Operation from(String value) {
        return CONSTANTS.get(value);
    }
}
