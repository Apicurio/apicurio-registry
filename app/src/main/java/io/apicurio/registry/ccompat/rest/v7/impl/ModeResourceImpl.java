package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.rest.v7.ModeResource;
import io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateRequest;
import io.apicurio.registry.ccompat.rest.v7.beans.ModeUpdateResponse;
import io.apicurio.registry.logging.Logged;
import io.apicurio.registry.metrics.health.liveness.ResponseErrorLivenessCheck;
import io.apicurio.registry.metrics.health.readiness.ResponseTimeoutReadinessCheck;
import io.apicurio.registry.model.GA;
import jakarta.interceptor.Interceptors;

@Interceptors({ ResponseErrorLivenessCheck.class, ResponseTimeoutReadinessCheck.class })
@Logged
public class ModeResourceImpl extends AbstractResource implements ModeResource {

    private static final String MODE_PROPERTY_NAME = "apicurio.ccompat.mode";
    private static final String SUBJECT_MODE_PROPERTY_PREFIX = "apicurio.ccompat.mode.subject.";

    private String getSubjectModePropertyName(String groupId, String artifactId) {
        String prefix = groupId != null ? groupId + "/" : "";
        return SUBJECT_MODE_PROPERTY_PREFIX + prefix + artifactId;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Read)
    public ModeUpdateResponse getMode() {
        // First check if storage is in read-only mode
        if (storage.isReadOnly()) {
            ModeUpdateResponse response = new ModeUpdateResponse();
            response.setMode(ModeUpdateResponse.Mode.READONLY);
            return response;
        }

        // Check if we have a stored mode configuration
        try {
            DynamicConfigPropertyDto property = storage.getRawConfigProperty(MODE_PROPERTY_NAME);
            if (property != null && property.getValue() != null && !property.getValue().isEmpty()) {
                ModeUpdateResponse response = new ModeUpdateResponse();
                response.setMode(ModeUpdateResponse.Mode.valueOf(property.getValue()));
                return response;
            }
        } catch (Exception e) {
            // Property not found or error, return default
        }

        // Default to READWRITE
        ModeUpdateResponse response = new ModeUpdateResponse();
        response.setMode(ModeUpdateResponse.Mode.READWRITE);
        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public ModeUpdateResponse updateMode(Boolean force, ModeUpdateRequest data) {
        // Validate the mode
        ModeUpdateRequest.Mode newMode = data.getMode();
        if (newMode == null) {
            throw new IllegalArgumentException("Mode is required");
        }

        // Store the mode as a dynamic config property
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto(MODE_PROPERTY_NAME, newMode.name());
        storage.setConfigProperty(property);

        ModeUpdateResponse response = new ModeUpdateResponse();
        response.setMode(ModeUpdateResponse.Mode.valueOf(newMode.name()));
        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public ModeUpdateResponse getSubjectMode(String subject, String groupId) {
        final GA ga = getGA(groupId, subject);
        String propertyName = getSubjectModePropertyName(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());

        // Try to get subject-specific mode
        try {
            DynamicConfigPropertyDto property = storage.getRawConfigProperty(propertyName);
            if (property != null && property.getValue() != null && !property.getValue().isEmpty()) {
                ModeUpdateResponse response = new ModeUpdateResponse();
                response.setMode(ModeUpdateResponse.Mode.valueOf(property.getValue()));
                return response;
            }
        } catch (Exception e) {
            // Property not found or error
        }

        // Fall back to global mode
        return getMode();
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public ModeUpdateResponse updateSubjectMode(String subject, Boolean force, String groupId,
            ModeUpdateRequest data) {
        final GA ga = getGA(groupId, subject);

        // Validate the mode
        ModeUpdateRequest.Mode newMode = data.getMode();
        if (newMode == null) {
            throw new IllegalArgumentException("Mode is required");
        }

        // Store mode as a subject-specific config property
        String propertyName = getSubjectModePropertyName(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto(propertyName, newMode.name());
        storage.setConfigProperty(property);

        ModeUpdateResponse response = new ModeUpdateResponse();
        response.setMode(ModeUpdateResponse.Mode.valueOf(newMode.name()));
        return response;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public ModeUpdateResponse deleteSubjectMode(String subject, String groupId) {
        final GA ga = getGA(groupId, subject);
        String propertyName = getSubjectModePropertyName(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());

        // Get the current mode before deleting
        ModeUpdateResponse.Mode currentMode = ModeUpdateResponse.Mode.READWRITE;
        try {
            DynamicConfigPropertyDto property = storage.getRawConfigProperty(propertyName);
            if (property != null && property.getValue() != null && !property.getValue().isEmpty()) {
                currentMode = ModeUpdateResponse.Mode.valueOf(property.getValue());
            }
        } catch (Exception e) {
            // Property not found, use default
        }

        // Delete the subject-specific mode configuration
        storage.deleteConfigProperty(propertyName);

        // Return the mode that was deleted
        ModeUpdateResponse response = new ModeUpdateResponse();
        response.setMode(currentMode);
        return response;
    }
}
