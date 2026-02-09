package io.apicurio.registry.ccompat.rest.v7.impl;

import io.apicurio.common.apps.config.DynamicConfigPropertyDto;
import io.apicurio.common.apps.logging.Logged;
import io.apicurio.registry.auth.Authorized;
import io.apicurio.registry.auth.AuthorizedLevel;
import io.apicurio.registry.auth.AuthorizedStyle;
import io.apicurio.registry.ccompat.dto.ModeDto;
import io.apicurio.registry.ccompat.dto.ModeDto.Mode;
import io.apicurio.registry.ccompat.rest.v7.ModeResource;
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
    public ModeDto getGlobalMode() {
        // First check if storage is in read-only mode
        if (storage.isReadOnly()) {
            return new ModeDto(Mode.READONLY);
        }

        // Check if we have a stored mode configuration
        try {
            DynamicConfigPropertyDto property = storage.getRawConfigProperty(MODE_PROPERTY_NAME);
            if (property != null && property.getValue() != null && !property.getValue().isEmpty()) {
                return new ModeDto(property.getValue());
            }
        } catch (Exception e) {
            // Property not found or error, return default
        }

        // Default to READWRITE
        return new ModeDto(Mode.READWRITE);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public ModeDto updateGlobalMode(ModeDto request, Boolean force) {
        // Validate the mode
        Mode newMode;
        try {
            newMode = request.getModeEnum();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid mode: " + request.getMode());
        }

        // Store the mode as a dynamic config property
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto(MODE_PROPERTY_NAME, newMode.name());
        storage.setConfigProperty(property);

        return new ModeDto(newMode);
    }

    @Override
    @Authorized(style = AuthorizedStyle.None, level = AuthorizedLevel.Admin)
    public ModeDto deleteGlobalMode() {
        ModeDto current = getGlobalMode();

        // Delete the mode property by setting it to empty string
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto(MODE_PROPERTY_NAME, "");
        storage.setConfigProperty(property);

        return current;
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Read)
    public ModeDto getSubjectMode(String subject, Boolean defaultToGlobal, String groupId) {
        final GA ga = getGA(groupId, subject);
        String propertyName = getSubjectModePropertyName(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());

        // Try to get subject-specific mode
        try {
            DynamicConfigPropertyDto property = storage.getRawConfigProperty(propertyName);
            if (property != null && property.getValue() != null && !property.getValue().isEmpty()) {
                return new ModeDto(property.getValue());
            }
        } catch (Exception e) {
            // Property not found or error
        }

        // Fall back to global if requested
        if (Boolean.TRUE.equals(defaultToGlobal)) {
            return getGlobalMode();
        }

        // Default to READWRITE for subject
        return new ModeDto(Mode.READWRITE);
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public ModeDto updateSubjectMode(String subject, ModeDto request, Boolean force, String groupId) {
        final GA ga = getGA(groupId, subject);

        // Validate the mode
        Mode newMode;
        try {
            newMode = request.getModeEnum();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid mode: " + request.getMode());
        }

        // Store mode as a subject-specific config property
        String propertyName = getSubjectModePropertyName(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto(propertyName, newMode.name());
        storage.setConfigProperty(property);

        return new ModeDto(newMode);
    }

    @Override
    @Authorized(style = AuthorizedStyle.ArtifactOnly, level = AuthorizedLevel.Write)
    public ModeDto deleteSubjectMode(String subject, String groupId) {
        final GA ga = getGA(groupId, subject);

        ModeDto current = getSubjectMode(subject, false, groupId);

        // Delete the subject mode by setting it to empty string
        String propertyName = getSubjectModePropertyName(ga.getRawGroupIdWithNull(), ga.getRawArtifactId());
        DynamicConfigPropertyDto property = new DynamicConfigPropertyDto(propertyName, "");
        storage.setConfigProperty(property);

        return current;
    }
}
