package io.apicurio.registry.operator.utils;

import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.resource.ui.UIDeploymentResource;
import io.fabric8.kubernetes.api.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;

import static io.apicurio.registry.operator.utils.Mapper.duplicate;
import static io.apicurio.registry.operator.utils.Mapper.toYAML;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static io.apicurio.registry.operator.utils.Utils.mergeNotOverride;
import static java.util.Objects.requireNonNull;

public class PodTemplateSpecFeature {

    private static final Logger log = LoggerFactory.getLogger(UIDeploymentResource.class);

    private PodTemplateSpecFeature() {
    }

    /**
     * Merge the original PTS from the factory into a copy of the PTS from the spec.
     *
     * @return merged copy
     */
    public static PodTemplateSpec merge(PodTemplateSpec spec, PodTemplateSpec original,
            String containerName) {

        requireNonNull(original);
        if (spec == null) {
            return original;
        }

        var merged = duplicate(spec, PodTemplateSpec.class);

        // .metadata
        if (merged.getMetadata() == null) {
            merged.setMetadata(new ObjectMeta());
        }

        // .metadata.labels
        if (merged.getMetadata().getLabels() == null) {
            merged.getMetadata().setLabels(new HashMap<>());
        }
        if (original.getMetadata() != null) {
            if (original.getMetadata().getLabels() != null) {
                merged.getMetadata().getLabels().putAll(original.getMetadata().getLabels());
            }
        }

        // .metadata.annotations
        if (merged.getMetadata().getAnnotations() == null) {
            merged.getMetadata().setAnnotations(new HashMap<>());
        }
        if (original.getMetadata() != null) {
            if (original.getMetadata().getAnnotations() != null) {
                merged.getMetadata().getAnnotations().putAll(original.getMetadata().getAnnotations());
            }
        }

        // .spec.containers[name = containerName]
        var oc = getContainer(original, containerName);
        if (oc == null) {
            throw new OperatorException("Container %s not found in the original PTS:\n%s"
                    .formatted(containerName, toYAML(original)));
        }

        // .spec
        if (merged.getSpec() == null) {
            merged.setSpec(new PodSpec());
        }

        // .spec.containers
        if (merged.getSpec().getContainers() == null) {
            merged.getSpec().setContainers(new ArrayList<>());
        }

        var mc = getContainer(merged, containerName);
        if (mc == null) {
            merged.getSpec().getContainers().add(oc);
        } else {

            // .spec.containers[name = containerName].env
            if (mc.getEnv() != null && !mc.getEnv().isEmpty()) {
                throw new OperatorException("""
                        Field spec.(app/ui).podTemplateSpec.spec.containers[name = %s].env must be empty.  \
                        Use spec.(app/ui).env to configure environment variables."""
                        .formatted(containerName));
            }
            mc.setEnv(oc.getEnv());

            // .spec.containers[name = containerName].image
            if (isBlank(mc.getImage())) {
                mc.setImage(oc.getImage());
            }

            // .spec.containers[name = containerName].ports
            mergeNotOverride(mc.getPorts(), oc.getPorts(), ContainerPort::getName);

            // .spec.containers[name = containerName].readinessProbe
            if (mc.getReadinessProbe() == null) {
                mc.setReadinessProbe(oc.getReadinessProbe());
            }

            // .spec.containers[name = containerName].livenessProbe
            if (mc.getLivenessProbe() == null) {
                mc.setLivenessProbe(oc.getLivenessProbe());
            }

            // .spec.containers[name = containerName].resources
            if (mc.getResources() == null) {
                mc.setResources(oc.getResources());
            } else {

                // .spec.containers[name = containerName].resources.requests
                if (mc.getResources().getRequests() == null) {
                    mc.getResources().setRequests(new HashMap<>());
                }
                mergeNotOverride(mc.getResources().getRequests(), oc.getResources().getRequests());

                // .spec.containers[name = containerName].resources.limits
                if (mc.getResources().getLimits() == null) {
                    mc.getResources().setLimits(new HashMap<>());
                }
                mergeNotOverride(mc.getResources().getLimits(), oc.getResources().getLimits());
            }
        }

        return merged;
    }

    /**
     * Get container with a given name from the given PTS.
     *
     * @return null when container was not found
     */
    public static Container getContainer(PodTemplateSpec pts, String name) {
        requireNonNull(pts);
        requireNonNull(name);
        if (pts.getSpec() != null && pts.getSpec().getContainers() != null) {
            for (var c : pts.getSpec().getContainers()) {
                if (name.equals(c.getName())) {
                    return c;
                }
            }
        }
        return null;
    }
}
