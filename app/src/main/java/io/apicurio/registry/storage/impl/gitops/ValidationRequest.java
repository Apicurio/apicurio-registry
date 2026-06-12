package io.apicurio.registry.storage.impl.gitops;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POJO representing the validation request JSON file exchanged between the registry and the sidecar
 * on the shared volume. Follows a Kubernetes-inspired spec/status pattern.
 *
 * <p>The registry writes the file with {@code spec} populated and {@code status} absent.
 * The sidecar detects the file, fetches the git ref, and updates {@code status}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationRequest {

    public static final String CURRENT_API_VERSION = "v1";

    private String apiVersion;
    private String kind;
    private Spec spec;
    private Status status;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Spec {
        private String type;
        private String repoId;
        private String ref;
        private String requestedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Status {
        private SidecarState state;
        private String checkoutPath;
        private String completedAt;
        private String error;
    }
}
