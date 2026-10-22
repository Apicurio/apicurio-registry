package io.apicurio.registry.thrift.idl;

public class ThriftIdlParseException extends Exception {

    private static final long serialVersionUID = 1L;

    public ThriftIdlParseException(String message) {
        super(message);
    }

    public ThriftIdlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
