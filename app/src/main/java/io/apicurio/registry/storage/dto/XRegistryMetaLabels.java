package io.apicurio.registry.storage.dto;

import java.util.HashMap;
import java.util.Map;

public final class XRegistryMetaLabels {

    private static final String P = "xregistry.meta.";

    private XRegistryMetaLabels() {
    }

    public static ResourceMetaDto fromLabels(String groupId, String artifactId,
            Map<String, String> labels) {
        ResourceMetaDto.ResourceMetaDtoBuilder builder = ResourceMetaDto.builder()
                .groupId(groupId).artifactId(artifactId);
        if (labels == null || labels.isEmpty()) {
            return builder.build();
        }
        builder.compatibility(labels.get(P + "compatibility"));
        builder.compatibilityAuthority(labels.get(P + "compatibilityauthority"));
        builder.defaultVersionId(labels.get(P + "defaultversionid"));
        String sticky = labels.get(P + "defaultversionsticky");
        if (sticky != null) {
            builder.defaultVersionSticky(Boolean.valueOf(sticky));
        }
        String ro = labels.get(P + "readonly");
        if (ro != null) {
            builder.readonly(Boolean.valueOf(ro));
        }
        builder.xref(labels.get(P + "xref"));

        String depEffective = labels.get(P + "deprecated.effective");
        String depRemoval = labels.get(P + "deprecated.removal");
        String depAlt = labels.get(P + "deprecated.alternative");
        String depDoc = labels.get(P + "deprecated.documentation");
        if (depEffective != null || depRemoval != null || depAlt != null || depDoc != null) {
            builder.deprecated(DeprecationInfoDto.builder()
                    .effective(depEffective != null ? Long.valueOf(depEffective) : null)
                    .removal(depRemoval != null ? Long.valueOf(depRemoval) : null)
                    .alternative(depAlt)
                    .documentation(depDoc)
                    .build());
        }
        return builder.build();
    }

    public static Map<String, String> toLabels(EditableResourceMetaDto dto) {
        Map<String, String> labels = new HashMap<>();
        if (dto.getCompatibility() != null) {
            labels.put(P + "compatibility", dto.getCompatibility());
        }
        if (dto.getCompatibilityAuthority() != null) {
            labels.put(P + "compatibilityauthority", dto.getCompatibilityAuthority());
        }
        if (dto.getDefaultVersionId() != null) {
            labels.put(P + "defaultversionid", dto.getDefaultVersionId());
        }
        if (dto.getDefaultVersionSticky() != null) {
            labels.put(P + "defaultversionsticky", dto.getDefaultVersionSticky().toString());
        }
        if (dto.getReadonly() != null) {
            labels.put(P + "readonly", dto.getReadonly().toString());
        }
        if (dto.getXref() != null) {
            labels.put(P + "xref", dto.getXref());
        }
        if (dto.getDeprecated() != null) {
            DeprecationInfoDto dep = dto.getDeprecated();
            if (dep.getEffective() != null) {
                labels.put(P + "deprecated.effective", String.valueOf(dep.getEffective()));
            }
            if (dep.getRemoval() != null) {
                labels.put(P + "deprecated.removal", String.valueOf(dep.getRemoval()));
            }
            if (dep.getAlternative() != null) {
                labels.put(P + "deprecated.alternative", dep.getAlternative());
            }
            if (dep.getDocumentation() != null) {
                labels.put(P + "deprecated.documentation", dep.getDocumentation());
            }
        }
        return labels;
    }
}
