package io.apicurio.registry.storage.impl.sql.jdb;

import java.sql.SQLException;

public class RuntimeSqlException extends RuntimeException {

    private static final long serialVersionUID = 2262442842283175353L;

    public RuntimeSqlException() {
        super();
    }

    public RuntimeSqlException(String message) {
        super(message);
    }

    /** Describes a failed operation, whose cause is the SQLException it failed with or an earlier failure. */
    public RuntimeSqlException(String message, Throwable cause) {
        super(message, cause);
    }

    public RuntimeSqlException(SQLException cause) {
        super(cause);
    }

}
