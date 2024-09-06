package io.apicurio.registry.services.http;

import io.apicurio.registry.ccompat.rest.error.ConflictException;
import io.apicurio.registry.ccompat.rest.error.ReferenceExistsException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotFoundException;
import io.apicurio.registry.ccompat.rest.error.SchemaNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SchemaSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectNotSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.SubjectSoftDeletedException;
import io.apicurio.registry.ccompat.rest.error.UnprocessableEntityException;
import io.apicurio.registry.content.dereference.DereferencingNotSupportedException;
import io.apicurio.registry.limits.LimitExceededException;
import io.apicurio.registry.rest.MissingRequiredParameterException;
import io.apicurio.registry.rest.ParametersConflictException;
import io.apicurio.registry.rules.DefaultRuleDeletionException;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.rules.UnprocessableSchemaException;
import io.apicurio.registry.storage.error.AlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactAlreadyExistsException;
import io.apicurio.registry.storage.error.ArtifactNotFoundException;
import io.apicurio.registry.storage.error.BranchAlreadyExistsException;
import io.apicurio.registry.storage.error.BranchNotFoundException;
import io.apicurio.registry.storage.error.ConfigPropertyNotFoundException;
import io.apicurio.registry.storage.error.ContentNotFoundException;
import io.apicurio.registry.storage.error.DownloadNotFoundException;
import io.apicurio.registry.storage.error.GroupAlreadyExistsException;
import io.apicurio.registry.storage.error.GroupNotFoundException;
import io.apicurio.registry.storage.error.InvalidArtifactIdException;
import io.apicurio.registry.storage.error.InvalidArtifactStateException;
import io.apicurio.registry.storage.error.InvalidArtifactTypeException;
import io.apicurio.registry.storage.error.InvalidGroupIdException;
import io.apicurio.registry.storage.error.InvalidPropertyValueException;
import io.apicurio.registry.storage.error.InvalidVersionStateException;
import io.apicurio.registry.storage.error.LogConfigurationNotFoundException;
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
import io.apicurio.rest.client.auth.exception.ForbiddenException;
import io.apicurio.rest.client.auth.exception.NotAuthorizedException;
import io.smallrye.mutiny.TimeoutException;
import jakarta.inject.Singleton;
import jakarta.validation.ValidationException;
import jakarta.ws.rs.BadRequestException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;

@Singleton
public class HttpStatusCodeMap {

    private static final int HTTP_UNPROCESSABLE_ENTITY = 422;

    protected static final Map<Class<? extends Exception>, Integer> CODE_MAP;

    private static Set<Class<? extends Exception>> getIgnored() {
        return CODE_MAP.keySet();
    }

    static {
        // TODO Merge this list with io.apicurio.registry.rest.RegistryExceptionMapper
        // Keep alphabetical

        Map<Class<? extends Exception>, Integer> map = new HashMap<>();
        map.put(AlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(ArtifactNotFoundException.class, HTTP_NOT_FOUND);
        map.put(BadRequestException.class, HTTP_BAD_REQUEST);
        map.put(BranchAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(BranchNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ConfigPropertyNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ConflictException.class, HTTP_CONFLICT);
        map.put(ContentNotFoundException.class, HTTP_NOT_FOUND);
        map.put(DefaultRuleDeletionException.class, HTTP_CONFLICT);
        map.put(DownloadNotFoundException.class, HTTP_NOT_FOUND);
        map.put(ForbiddenException.class, HTTP_FORBIDDEN);
        map.put(GroupNotFoundException.class, HTTP_NOT_FOUND);
        map.put(GroupAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(InvalidArtifactIdException.class, HTTP_BAD_REQUEST);
        map.put(InvalidArtifactStateException.class, HTTP_BAD_REQUEST);
        map.put(InvalidVersionStateException.class, HTTP_BAD_REQUEST);
        map.put(InvalidArtifactTypeException.class, HTTP_BAD_REQUEST);
        map.put(InvalidGroupIdException.class, HTTP_BAD_REQUEST);
        map.put(InvalidPropertyValueException.class, HTTP_BAD_REQUEST);
        map.put(io.apicurio.registry.rest.ConflictException.class, HTTP_CONFLICT);
        map.put(LimitExceededException.class, HTTP_CONFLICT);
        map.put(LogConfigurationNotFoundException.class, HTTP_NOT_FOUND);
        map.put(MissingRequiredParameterException.class, HTTP_BAD_REQUEST);
        map.put(NotAllowedException.class, HTTP_CONFLICT); // We're using 409 instead of 403 to reserve the
        // latter for authx only.
        map.put(NotAuthorizedException.class, HTTP_FORBIDDEN);
        map.put(NotFoundException.class, HTTP_NOT_FOUND);
        map.put(ParametersConflictException.class, HTTP_CONFLICT);
        map.put(ReadOnlyStorageException.class, HTTP_CONFLICT);
        map.put(ReferenceExistsException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(DereferencingNotSupportedException.class, HTTP_BAD_REQUEST);
        map.put(RoleMappingAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(RoleMappingNotFoundException.class, HTTP_NOT_FOUND);
        map.put(RuleAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(RuleNotFoundException.class, HTTP_NOT_FOUND);
        map.put(RuleViolationException.class, HTTP_CONFLICT);
        map.put(SchemaNotFoundException.class, HTTP_NOT_FOUND);
        map.put(SchemaNotSoftDeletedException.class, HTTP_CONFLICT);
        map.put(SchemaSoftDeletedException.class, HTTP_CONFLICT);
        map.put(SubjectNotSoftDeletedException.class, HTTP_CONFLICT);
        map.put(SubjectSoftDeletedException.class, HTTP_NOT_FOUND);
        map.put(TimeoutException.class, HTTP_UNAVAILABLE);
        map.put(UnprocessableEntityException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(UnprocessableSchemaException.class, HTTP_UNPROCESSABLE_ENTITY);
        map.put(ValidationException.class, HTTP_BAD_REQUEST);
        map.put(VersionAlreadyExistsException.class, HTTP_CONFLICT);
        map.put(VersionAlreadyExistsOnBranchException.class, HTTP_CONFLICT);
        map.put(VersionNotFoundException.class, HTTP_NOT_FOUND);

        CODE_MAP = Collections.unmodifiableMap(map);
    }

    public int getCode(Class<?> exceptionClass) {
        return CODE_MAP.getOrDefault(exceptionClass, HTTP_INTERNAL_ERROR);
    }

    public boolean isIgnored(Class<? extends Throwable> aClass) {
        return getIgnored().contains(aClass);
    }
}
