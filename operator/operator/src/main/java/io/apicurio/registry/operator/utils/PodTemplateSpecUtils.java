package io.apicurio.registry.operator.utils;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;

import static io.apicurio.registry.operator.utils.TraverseUtils.*;

public class PodTemplateSpecUtils {

    private static final Logger log = LoggerFactory.getLogger(PodTemplateSpecUtils.class);

    public static void process(PodTemplateSpec spec, PodTemplateSpec base, String containerName) {
        // .metadata
        if (spec.getMetadata() == null) {
            spec.setMetadata(new ObjectMeta());
        }

        // .metadata.labels
        if (spec.getMetadata().getLabels() == null) {
            spec.getMetadata().setLabels(new HashMap<>());
        }
        mergeOverride(spec.getMetadata().getLabels(), base.getMetadata().getLabels());

        // .spec.containers[name = containerName]
        where(spec.getSpec().getContainers(), sc -> containerName.equals(sc.getName()), sc -> {
            where(base.getSpec().getContainers(), fc -> containerName.equals(fc.getName()), fc -> {

                if (!isEmpty(sc.getEnv())) {
                    log.warn(
                            ".spec.containers[name = {}].env field cannot be used updated by PodTemplateSpec",
                            containerName); // TODO: Status condition
                }
                sc.setEnv(List.of()); // TODO: This might not even be necessary as it will be overridden

                if (!isEmpty(sc.getImage())) {
                    log.warn(
                            ".spec.containers[name = {}].image field cannot be used updated by PodTemplateSpec",
                            containerName); // TODO: Status condition
                }
                sc.setImage(fc.getImage()); // TODO: This might not be necessary soon

                // TODO: These might be eventually moved from a factory into actions
                // .ports
                mergeNoOverride(sc.getPorts(), fc.getPorts(), ContainerPort::getContainerPort);

                // .readinessProbe
                if (sc.getReadinessProbe() == null) {
                    sc.setReadinessProbe(fc.getReadinessProbe());
                }

                // .livenessProbe
                if (sc.getLivenessProbe() == null) {
                    sc.setLivenessProbe(fc.getLivenessProbe());
                }

                // .resources
                if (sc.getResources() == null) {
                    sc.setResources(fc.getResources());
                } else {
                    // .resources.requests
                    if (sc.getResources().getRequests() == null) {
                        sc.getResources().setRequests(fc.getResources().getRequests());
                    } else {
                        mergeNoOverride(sc.getResources().getRequests(), fc.getResources().getRequests());
                    }
                    // .resources.limits
                    if (sc.getResources().getLimits() == null) {
                        sc.getResources().setLimits(fc.getResources().getLimits());
                    } else {
                        mergeNoOverride(sc.getResources().getLimits(), fc.getResources().getLimits());
                    }
                }
            });
        });
    }
}
