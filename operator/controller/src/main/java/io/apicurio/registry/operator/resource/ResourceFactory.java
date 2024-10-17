package io.apicurio.registry.operator.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Map;

import static io.apicurio.registry.operator.utils.Mapper.YAML_MAPPER;

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
        // WARNING: Make sure to update the io.apicurio.registry.operator.utils.PodTemplateSpecUtils.merge
        // method
        // when making significant changes here:
        var r = getDefaultResource(primary, Deployment.class, RESOURCE_TYPE_DEPLOYMENT, COMPONENT_APP);
        addDefaultLabels(r.getSpec().getTemplate().getMetadata().getLabels(), primary, COMPONENT_APP);
        addSelectorLabels(r.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_APP);
        return r;
    }

    public Deployment getDefaultUIDeployment(ApicurioRegistry3 primary) {
        // WARNING: Make sure to update the io.apicurio.registry.operator.utils.PodTemplateSpecUtils.merge
        // method
        // when making significant changes here:
        var r = getDefaultResource(primary, Deployment.class, RESOURCE_TYPE_DEPLOYMENT, COMPONENT_UI);
        addDefaultLabels(r.getSpec().getTemplate().getMetadata().getLabels(), primary, COMPONENT_UI);
        addSelectorLabels(r.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_UI);
        return r;
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
