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

    public RuntimeSqlException(String message, SQLException cause) {
        super(message, cause);
    }

    public RuntimeSqlException(SQLException cause) {
        super(cause);
    }

}
