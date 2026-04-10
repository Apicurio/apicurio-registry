package io.apicurio.schema.validation.protobuf;

import java.util.List;

public class ProtobufValidationResult {

    protected static final ProtobufValidationResult SUCCESS = successful();

    private boolean success;
    private List<ValidationError> validationErrors;

    private ProtobufValidationResult(List<ValidationError> validationErrors) {
        this.validationErrors = validationErrors;
        this.success = this.validationErrors == null || this.validationErrors.isEmpty();
    }

    public boolean success() {
        return success;
    }

    public List<ValidationError> getValidationErrors() {
        return validationErrors;
    }

    @Override
    public String toString() {
        if (this.success) {
            return "ProtobufValidationResult [ success ]";
        } else {
            return "ProtobufValidationResult [ errors = " + validationErrors.toString() + " ]";
        }
    }

    public static ProtobufValidationResult fromErrors(List<ValidationError> errors) {
        return new ProtobufValidationResult(errors);
    }

    public static ProtobufValidationResult successful() {
        return new ProtobufValidationResult(null);
    }
}