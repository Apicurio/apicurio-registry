package io.apicurio.registry.operator.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromPodTemplateSpec;
import static io.apicurio.registry.operator.utils.Mapper.YAML_MAPPER;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static io.apicurio.registry.operator.utils.Utils.mergeNotOverride;

public class ResourceFactory {

    private static final Logger log = LoggerFactory.getLogger(ResourceFactory.class);

    public static final ResourceFactory INSTANCE = new ResourceFactory();

    public static final String COMPONENT_APP = "app";
    public static final String COMPONENT_UI = "ui";

    public static final String RESOURCE_TYPE_DEPLOYMENT = "deployment";
    public static final String RESOURCE_TYPE_SERVICE = "service";
    public static final String RESOURCE_TYPE_INGRESS = "ingress";

    public static final String APP_CONTAINER_NAME = "apicurio-registry-app";
    public static final String UI_CONTAINER_NAME = "apicurio-registry-ui";

    public Deployment getDefaultAppDeployment(ApicurioRegistry3 primary) {
        var r = new Deployment();
        r.setMetadata(new ObjectMeta());
        r.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        r.getMetadata().setName(
                primary.getMetadata().getName() + "-" + COMPONENT_APP + "-" + RESOURCE_TYPE_DEPLOYMENT);
        r.setSpec(new DeploymentSpec());
        r.getSpec().setReplicas(1);
        r.getSpec().setSelector(new LabelSelector());
        if (primary.getSpec().getApp().getPodTemplateSpec() != null) {
            r.getSpec().setTemplate(primary.getSpec().getApp().getPodTemplateSpec());
        } else {
            r.getSpec().setTemplate(new PodTemplateSpec());
        }
        mergeDeploymentPodTemplateSpec(
                // spotless:off
                r.getSpec().getTemplate(),
                APP_CONTAINER_NAME,
                "quay.io/apicurio/apicurio-registry:latest-snapshot",
                List.of(new ContainerPortBuilder().withName("http").withProtocol("TCP").withContainerPort(8080).build()),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/health/ready").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build(),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/health/live").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build(),
                Map.of("cpu", new Quantity("500m"), "memory", new Quantity("512Mi")),
                Map.of("cpu", new Quantity("1"), "memory", new Quantity("1Gi"))
                // spotless:on
        );
        addDefaultLabels(r.getMetadata().getLabels(), primary, COMPONENT_APP);
        addSelectorLabels(r.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_APP);
        addDefaultLabels(r.getSpec().getTemplate().getMetadata().getLabels(), primary, COMPONENT_APP);
        return r;
    }

    public Deployment getDefaultUIDeployment(ApicurioRegistry3 primary) {
        var r = new Deployment();
        r.setMetadata(new ObjectMeta());
        r.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        r.getMetadata().setName(
                primary.getMetadata().getName() + "-" + COMPONENT_UI + "-" + RESOURCE_TYPE_DEPLOYMENT);
        r.setSpec(new DeploymentSpec());
        r.getSpec().setReplicas(1);
        r.getSpec().setSelector(new LabelSelector());
        if (primary.getSpec().getUi().getPodTemplateSpec() != null) {
            r.getSpec().setTemplate(primary.getSpec().getUi().getPodTemplateSpec());
        } else {
            r.getSpec().setTemplate(new PodTemplateSpec());
        }
        mergeDeploymentPodTemplateSpec(
                // spotless:off
                r.getSpec().getTemplate(),
                UI_CONTAINER_NAME,
                "quay.io/apicurio/apicurio-registry-ui:latest-snapshot",
                List.of(new ContainerPortBuilder().withName("http").withProtocol("TCP").withContainerPort(8080).build()),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/config.js").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build(),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/config.js").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build(),
                Map.of("cpu", new Quantity("100m"), "memory", new Quantity("256Mi")),
                Map.of("cpu", new Quantity("200m"), "memory", new Quantity("512Mi"))
                // spotless:on
        );
        addDefaultLabels(r.getMetadata().getLabels(), primary, COMPONENT_UI);
        addSelectorLabels(r.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_UI);
        addDefaultLabels(r.getSpec().getTemplate().getMetadata().getLabels(), primary, COMPONENT_UI);
        return r;
    }

    /**
     * Merge default values for a Deployment into the target PTS (from spec).
     */
    private static void mergeDeploymentPodTemplateSpec(
            // spotless:off
            PodTemplateSpec target,
            String containerName,
            String image,
            List<ContainerPort> ports,
            Probe readinessProbe,
            Probe livenessProbe,
            Map<String, Quantity> requests,
            Map<String, Quantity> limits
            // spotless:on
    ) {
        if (target.getMetadata() == null) {
            target.setMetadata(new ObjectMeta());
        }
        var c = getContainerFromPodTemplateSpec(target, containerName);
        if (c == null) {
            if (target.getSpec() == null) {
                target.setSpec(new PodSpec());
            }
            c = new Container();
            c.setName(containerName);
            if (target.getSpec().getContainers() == null) {
                target.getSpec().setContainers(new ArrayList<>());
            }
            target.getSpec().getContainers().add(c);
        }
        if (isBlank(c.getImage())) {
            c.setImage(image);
        }
        if (c.getEnv() != null && !c.getEnv().isEmpty()) {
            throw new OperatorException("""
                    Field spec.(app/ui).podTemplateSpec.spec.containers[name = %s].env must be empty. \
                    Use spec.(app/ui).env to configure environment variables.""".formatted(containerName));
        }
        if (c.getPorts() == null) {
            c.setPorts(new ArrayList<>());
        }
        mergeNotOverride(c.getPorts(), ports, ContainerPort::getName);
        if (c.getReadinessProbe() == null) {
            c.setReadinessProbe(readinessProbe);
        }
        if (c.getLivenessProbe() == null) {
            c.setLivenessProbe(livenessProbe);
        }
        if (c.getResources() == null) {
            c.setResources(new ResourceRequirements());
        }
        if (c.getResources().getRequests() == null || c.getResources().getRequests().isEmpty()) {
            c.getResources().setRequests(requests);
        }
        if (c.getResources().getLimits() == null || c.getResources().getLimits().isEmpty()) {
            c.getResources().setLimits(limits);
        }
    }

    public Service getDefaultAppService(ApicurioRegistry3 primary) {
        var r = getDefaultResource(primary, Service.class, RESOURCE_TYPE_SERVICE, COMPONENT_APP);
        addSelectorLabels(r.getSpec().getSelector(), primary, COMPONENT_APP);
        return r;
    }

    public Service getDefaultUIService(ApicurioRegistry3 primary) {
        var r = getDefaultResource(primary, Service.class, RESOURCE_TYPE_SERVICE, COMPONENT_UI);
        addSelectorLabels(r.getSpec().getSelector(), primary, COMPONENT_UI);
        return r;
    }

    public Ingress getDefaultAppIngress(ApicurioRegistry3 primary) {
        var r = getDefaultResource(primary, Ingress.class, RESOURCE_TYPE_INGRESS, COMPONENT_APP);
        r.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService()
                .setName(primary.getMetadata().getName() + "-" + COMPONENT_APP + "-" + RESOURCE_TYPE_SERVICE);
        return r;
    }

    public Ingress getDefaultUIIngress(ApicurioRegistry3 primary) {
        var r = getDefaultResource(primary, Ingress.class, RESOURCE_TYPE_INGRESS, COMPONENT_UI);
        r.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService()
                .setName(primary.getMetadata().getName() + "-" + COMPONENT_UI + "-" + RESOURCE_TYPE_SERVICE);
        return r;
    }

    private <T extends HasMetadata> T getDefaultResource(ApicurioRegistry3 primary, Class<T> klass,
            String resourceType, String component) {
        var r = deserialize("/k8s/default/" + component + "." + resourceType + ".yaml", klass);
        r.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        r.getMetadata().setName(primary.getMetadata().getName() + "-" + component + "-" + resourceType);
        addDefaultLabels(r.getMetadata().getLabels(), primary, component);
        return r;
    }

    private void addDefaultLabels(Map<String, String> labels, ApicurioRegistry3 primary, String component) {
        // spotless:off
        labels.putAll(Map.of(
                "app", primary.getMetadata().getName(),
                "app.kubernetes.io/name", "apicurio-registry",
                "app.kubernetes.io/component", component,
                "app.kubernetes.io/instance", primary.getMetadata().getName(),
                "app.kubernetes.io/version", "1.0.0", // TODO
                "app.kubernetes.io/part-of", "apicurio-registry",
                "app.kubernetes.io/managed-by", "apicurio-registry-operator"
        ));
        // spotless:on
    }

    private void addSelectorLabels(Map<String, String> labels, ApicurioRegistry3 primary, String component) {
        // spotless:off
        labels.putAll(Map.of(
                "app", primary.getMetadata().getName(),
                "app.kubernetes.io/name", "apicurio-registry",
                "app.kubernetes.io/component", component,
                "app.kubernetes.io/instance", primary.getMetadata().getName(),
                "app.kubernetes.io/part-of", "apicurio-registry"
        ));
        // spotless:on
    }

    public static <T> T deserialize(String path, Class<T> klass) {
        try {
            return YAML_MAPPER.readValue(load(path), klass);
        } catch (JsonProcessingException ex) {
            throw new OperatorException("Could not deserialize resource: " + path, ex);
        }
    }

    private static String load(String path) {
        try (var stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), Charset.defaultCharset());
        } catch (Exception ex) {
            throw new OperatorException("Could not read resource: " + path, ex);
        }
    }
}
