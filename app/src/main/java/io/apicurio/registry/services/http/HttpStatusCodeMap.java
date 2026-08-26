package io.apicurio.registry.services.http;

import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.InvalidCompatibilityLevelException;
import io.apicurio.registry.ccompat.rest.error.ReferenceExistsException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotFoundException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SchemaSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.content.dereference.DereferencingNotSupportedException;
import io.apicurio.registry.limits.LimitExceededException;
import io.apicurio.registry.rest.InvalidParameterValueException;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.ParametersConflictException;
import io.apicurio.registry.rest.RestConfig;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.rules.violation.UnprocessableSchemaException;
import io.apicurio.registry.storage.error.AlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.BranchAlreadyExistsException;
import io.apicurio.registry.storage.error.BranchNotFoundException;
import io.apicurio.registry.storage.error.CommentNotFoundException;
import io.apicurio.registry.storage.error.ConfigPropertyNotFoundException;
import io.apicurio.registry.storage.error.ContentAlreadyExistsException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.ContentSearchNotSupportedException;
import io.apicurio.registry.storage.error.DownloadNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactIdException;
import io.apicurio.registry.storage.error.InvalidArtifactStateException;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.storage.error.InvalidContentException;
import io.apicurio.registry.storage.error.InvalidContractMetadataException;
import io.apicurio.registry.storage.error.InvalidGroupIdException;
import io.apicurio.registry.storage.error.InvalidPropertyValueException;
import io.apicurio.registry.storage.error.InvalidVersionStateException;
import io.apicurio.registry.storage.error.NotAllowedException;
import io.apicurio.registry.storage.error.NotFoundException;
import io.apicurio.registry.storage.error.ReadOnlyStorageException;
import io.apicurio.registry.storage.error.RoleMappingAlreadyExistsException;
import io.apicurio.registry.storage.error.RoleMappingNotFoundException;
import io.apicurio.registry.storage.error.RuleAlreadyExistsException;
import io.apicurio.registry.storage.error.RuleNotFoundException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsException;
import io.apicurio.registry.storage.error.VersionAlreadyExistsOnBranchException;
import io.apicurio.registry.storage.error.VersionNotFoundException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.TimeoutException;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.BadRequestException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

@Singleton
public class HttpStatusCodeMap {

    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;

    @Inject
    RestConfig restConfig;

    private Map<Class<? extends Exception>, ErrorInfo> codeMap;

    @PostConstruct
    void init() {
        // TODO Merge this list with io.apicurio.registry.rest.RegistryExceptionMapper
        // Keep alphabetical

        Map<Class<? extends Exception>, ErrorInfo> map = new HashMap<>();
        register(map, AlreadyExistsException.class, HTTP_CONFLICT, RegistryErrorCode.ALREADY_EXISTS);
        register(map, ArtifactAlreadyExistsException.class, HTTP_CONFLICT,
                RegistryErrorCode.ARTIFACT_ALREADY_EXISTS);
        register(map, ArtifactNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.ARTIFACT_NOT_FOUND);
        register(map, BadRequestException.class, HTTP_BAD_REQUEST, RegistryErrorCode.BAD_REQUEST);
        register(map, BranchAlreadyExistsException.class, HTTP_CONFLICT, RegistryErrorCode.BRANCH_ALREADY_EXISTS);
        register(map, BranchNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.BRANCH_NOT_FOUND);
        register(map, CommentNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.COMMENT_NOT_FOUND);
        register(map, ConfigPropertyNotFoundException.class, HTTP_NOT_FOUND,
                RegistryErrorCode.CONFIG_PROPERTY_NOT_FOUND);
        register(map, ConflictException.class, HTTP_CONFLICT, RegistryErrorCode.CONFLICT);
        register(map, io.apicurio.registry.rest.ConflictException.class, HTTP_CONFLICT,
                RegistryErrorCode.CONFLICT);
        register(map, ContentAlreadyExistsException.class, HTTP_CONFLICT,
                RegistryErrorCode.CONTENT_ALREADY_EXISTS);
        register(map, ContentNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.CONTENT_NOT_FOUND);
        register(map, ContentSearchNotSupportedException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.CONTENT_SEARCH_NOT_SUPPORTED);
        register(map, DefaultRuleDeletionException.class, HTTP_CONFLICT,
                RegistryErrorCode.DEFAULT_RULE_DELETION_NOT_ALLOWED);
        register(map, DereferencingNotSupportedException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.DEREFERENCING_NOT_SUPPORTED);
        register(map, DownloadNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.DOWNLOAD_NOT_FOUND);
        register(map, ForbiddenException.class, HTTP_FORBIDDEN, RegistryErrorCode.FORBIDDEN);
        register(map, GroupAlreadyExistsException.class, HTTP_CONFLICT, RegistryErrorCode.GROUP_ALREADY_EXISTS);
        register(map, GroupNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.GROUP_NOT_FOUND);
        register(map, InvalidArtifactIdException.class, HTTP_BAD_REQUEST, RegistryErrorCode.INVALID_ARTIFACT_ID);
        register(map, InvalidArtifactStateException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.INVALID_ARTIFACT_STATE);
        register(map, InvalidArtifactTypeException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.INVALID_ARTIFACT_TYPE);
        register(map, InvalidCompatibilityLevelException.class, HTTP_UNPROCESSABLE_ENTITY,
                RegistryErrorCode.INVALID_COMPATIBILITY_LEVEL);
        register(map, InvalidContentException.class, HTTP_BAD_REQUEST, RegistryErrorCode.INVALID_CONTENT);
        register(map, InvalidContractMetadataException.class, HTTP_CONFLICT,
                RegistryErrorCode.INVALID_CONTRACT_METADATA);
        register(map, InvalidGroupIdException.class, HTTP_BAD_REQUEST, RegistryErrorCode.INVALID_GROUP_ID);
        register(map, InvalidParameterValueException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.INVALID_PARAMETER_VALUE);
        register(map, InvalidPropertyValueException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.INVALID_PROPERTY_VALUE);
        register(map, InvalidVersionStateException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.INVALID_VERSION_STATE);
        register(map, LimitExceededException.class, HTTP_CONFLICT, RegistryErrorCode.LIMIT_EXCEEDED);
        register(map, MissingRequiredParameterException.class, HTTP_BAD_REQUEST,
                RegistryErrorCode.MISSING_REQUIRED_PARAMETER);
        // Use 409 instead of 403 to reserve the latter for authx only.
        register(map, NotAllowedException.class, HTTP_CONFLICT, RegistryErrorCode.NOT_ALLOWED);
        register(map, NotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.NOT_FOUND);
        register(map, ParametersConflictException.class, HTTP_CONFLICT, RegistryErrorCode.PARAMETERS_CONFLICT);
        register(map, ReadOnlyStorageException.class, HTTP_CONFLICT, RegistryErrorCode.READ_ONLY_STORAGE);
        register(map, ReferenceExistsException.class, HTTP_UNPROCESSABLE_ENTITY, RegistryErrorCode.REFERENCE_EXISTS);
        register(map, RoleMappingAlreadyExistsException.class, HTTP_CONFLICT,
                RegistryErrorCode.ROLE_MAPPING_ALREADY_EXISTS);
        register(map, RoleMappingNotFoundException.class, HTTP_NOT_FOUND,
                RegistryErrorCode.ROLE_MAPPING_NOT_FOUND);
        register(map, RuleAlreadyExistsException.class, HTTP_CONFLICT, RegistryErrorCode.RULE_ALREADY_EXISTS);
        register(map, RuleNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.RULE_NOT_FOUND);
        register(map, RuleViolationException.class,
                restConfig.isLegacyErrorCodesEnabled() ? HTTP_CONFLICT : HTTP_BAD_REQUEST,
                RegistryErrorCode.RULE_VIOLATION);
        register(map, SchemaNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.SCHEMA_NOT_FOUND);
        register(map, SchemaNotSoftDeletedException.class, HTTP_CONFLICT,
                RegistryErrorCode.SCHEMA_NOT_SOFT_DELETED);
        register(map, SchemaSoftDeletedException.class, HTTP_CONFLICT, RegistryErrorCode.SCHEMA_SOFT_DELETED);
        register(map, SubjectNotSoftDeletedException.class, HTTP_CONFLICT,
                RegistryErrorCode.SUBJECT_NOT_SOFT_DELETED);
        register(map, SubjectSoftDeletedException.class, HTTP_NOT_FOUND,
                RegistryErrorCode.SUBJECT_SOFT_DELETED);
        register(map, TimeoutException.class, HTTP_UNAVAILABLE, RegistryErrorCode.TIMEOUT);
        register(map, UnauthorizedException.class, HTTP_UNAUTHORIZED, RegistryErrorCode.UNAUTHORIZED);
        register(map, UnprocessableEntityException.class, HTTP_UNPROCESSABLE_ENTITY,
                RegistryErrorCode.UNPROCESSABLE_ENTITY);
        register(map, UnprocessableSchemaException.class, HTTP_UNPROCESSABLE_ENTITY,
                RegistryErrorCode.UNPROCESSABLE_SCHEMA);
        register(map, ValidationException.class, HTTP_BAD_REQUEST, RegistryErrorCode.VALIDATION_FAILED);
        register(map, VersionAlreadyExistsException.class, HTTP_CONFLICT,
                RegistryErrorCode.VERSION_ALREADY_EXISTS);
        register(map, VersionAlreadyExistsOnBranchException.class, HTTP_CONFLICT,
                RegistryErrorCode.VERSION_ALREADY_EXISTS_ON_BRANCH);
        register(map, VersionNotFoundException.class, HTTP_NOT_FOUND, RegistryErrorCode.VERSION_NOT_FOUND);

        codeMap = Collections.unmodifiableMap(map);
    }

    public int getCode(Class<?> exceptionClass) {
        ErrorInfo errorInfo = getErrorInfo(exceptionClass);
        return errorInfo != null ? errorInfo.httpStatus() : HTTP_INTERNAL_ERROR;
    }

    public RegistryErrorCode getErrorCode(Class<?> exceptionClass, int httpStatus) {
        ErrorInfo errorInfo = getErrorInfo(exceptionClass);
        return errorInfo != null ? errorInfo.errorCode() : RegistryErrorCode.fromHttpStatus(httpStatus);
    }

    private ErrorInfo getErrorInfo(Class<?> exceptionClass) {
        // Walk the superclass chain so that a subclass of a mapped exception (e.g. a new
        // AlreadyExistsException variant that wasn't registered explicitly) still resolves
        // to its ancestor's status code instead of silently falling back to 500.
        Class<?> clazz = exceptionClass;
        while (clazz != null && clazz != Object.class) {
            ErrorInfo errorInfo = codeMap.get(clazz);
            if (errorInfo != null) {
                return errorInfo;
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    public boolean isIgnored(Class<? extends Throwable> aClass) {
        return codeMap.containsKey(aClass);
    }

    private static void register(Map<Class<? extends Exception>, ErrorInfo> map,
            Class<? extends Exception> exceptionClass, int httpStatus, RegistryErrorCode errorCode) {
        map.put(exceptionClass, new ErrorInfo(httpStatus, errorCode));
    }

    private record ErrorInfo(int httpStatus, RegistryErrorCode errorCode) {
    }
}
