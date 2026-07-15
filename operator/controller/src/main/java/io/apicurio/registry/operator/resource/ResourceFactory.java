package io.apicurio.registry.operator.resource;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.apicurio.registry.operator.Configuration;
import io.apicurio.registry.operator.OperatorException;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3;
import io.apicurio.registry.operator.api.v1.ApicurioRegistry3Spec;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.AutoscalingSpec;
import io.apicurio.registry.operator.api.v1.spec.ComponentSpec;
import io.apicurio.registry.operator.api.v1.spec.TLSSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import io.apicurio.registry.operator.status.StatusManager;
import io.apicurio.registry.operator.status.ValidationErrorConditionManager;
import io.apicurio.registry.operator.utils.SecretKeyRefTool;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v2.HorizontalPodAutoscaler;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpec;
import io.fabric8.kubernetes.api.model.autoscaling.v2.MetricSpecBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.NetworkPolicy;
import io.fabric8.kubernetes.api.model.policy.v1.PodDisruptionBudget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.apicurio.registry.operator.Constants.DEFAULT_LIVENESS_PROBE;
import static io.apicurio.registry.operator.Constants.DEFAULT_READINESS_PROBE;
import static io.apicurio.registry.operator.Constants.DEFAULT_REPLICAS;
import static io.apicurio.registry.operator.Constants.TLS_DEFAULT_LIVENESS_PROBE;
import static io.apicurio.registry.operator.Constants.TLS_DEFAULT_READINESS_PROBE;
import static io.apicurio.registry.operator.api.v1.ContainerNames.CONSOLE_PLUGIN_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_APP_CONTAINER_NAME;
import static io.apicurio.registry.operator.api.v1.ContainerNames.REGISTRY_UI_CONTAINER_NAME;
import static io.apicurio.registry.operator.resource.Labels.getSelectorLabels;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainerFromPodTemplateSpec;
import static io.apicurio.registry.operator.resource.app.AppDeploymentResource.getContainersFromPTS;
import static io.apicurio.registry.operator.utils.Mapper.YAML_MAPPER;
import static io.apicurio.registry.operator.utils.Utils.isBlank;
import static java.util.Optional.ofNullable;

public class ResourceFactory {

    private static final Logger log = LoggerFactory.getLogger(ResourceFactory.class);

    public static final ResourceFactory INSTANCE = new ResourceFactory();

    public static final String COMPONENT_APP = "app";
    public static final String COMPONENT_UI = "ui";
    public static final String COMPONENT_CONSOLE_PLUGIN = "console-plugin";

    // TODO: Merge the two sets of constants into an enum. Also consider including container names.
    public static final String COMPONENT_APP_SPEC_FIELD_NAME = "app";
    public static final String COMPONENT_UI_SPEC_FIELD_NAME = "ui";
    public static final String COMPONENT_CONSOLE_PLUGIN_SPEC_FIELD_NAME = "consolePlugin";

    public static final String RESOURCE_TYPE_DEPLOYMENT = "deployment";
    public static final String RESOURCE_TYPE_SERVICE = "service";
    public static final String RESOURCE_TYPE_INGRESS = "ingress";
    public static final String RESOURCE_TYPE_POD_DISRUPTION_BUDGET = "poddisruptionbudget";
    public static final String RESOURCE_TYPE_NETWORK_POLICY = "networkpolicy";
    public static final String RESOURCE_TYPE_HORIZONTAL_POD_AUTOSCALER = "horizontalpodautoscaler";

    public Deployment getDefaultAppDeployment(ApicurioRegistry3 primary) {
        var autoscaling = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                .map(ComponentSpec::getAutoscaling).orElse(null);
        boolean autoscalingEnabled = ofNullable(autoscaling).map(AutoscalingSpec::getEnabled)
                .orElse(Boolean.FALSE);

        int replicas;
        if (autoscalingEnabled) {
            replicas = ofNullable(autoscaling).map(AutoscalingSpec::getMinReplicas).orElse(DEFAULT_REPLICAS);
            var explicitReplicas = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(AppSpec::getReplicas).orElse(null);
            if (explicitReplicas != null) {
                StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                        .recordError("Both spec.app.replicas and spec.app.autoscaling are configured. "
                                + "The replicas field will be ignored when autoscaling is enabled.");
            }
        } else {
            replicas = Optional.ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                    .map(AppSpec::getReplicas).orElse(DEFAULT_REPLICAS);
        }

        var r = initDefaultDeployment(primary, COMPONENT_APP, replicas,
                ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                        .map(AppSpec::getPodTemplateSpec).orElse(null));

        var readinessProbe = DEFAULT_READINESS_PROBE;
        var livenessProbe = DEFAULT_LIVENESS_PROBE;
        var containerPort = List.of(
                new ContainerPortBuilder().withName("http").withProtocol("TCP").withContainerPort(8080).build(),
                new ContainerPortBuilder().withName("management").withProtocol("TCP").withContainerPort(9000).build()
        );

        Optional<TLSSpec> tlsSpec = ofNullable(primary.getSpec())
                .map(ApicurioRegistry3Spec::getApp)
                .map(AppSpec::getTls);

        if (tlsSpec.isPresent()) {
            var keystore = new SecretKeyRefTool(tlsSpec.map(TLSSpec::getKeystoreSecretRef)
                    .orElse(null), "keystore");

            var keystorePassword = new SecretKeyRefTool(tlsSpec.map(TLSSpec::getKeystorePasswordSecretRef)
                    .orElse(null), "password");

            if (keystore.isValid() && keystorePassword.isValid()) {
                readinessProbe = TLS_DEFAULT_READINESS_PROBE;
                livenessProbe = TLS_DEFAULT_LIVENESS_PROBE;
                containerPort = List.of(
                        new ContainerPortBuilder().withName("https").withProtocol("TCP").withContainerPort(8443).build(),
                        new ContainerPortBuilder().withName("management").withProtocol("TCP").withContainerPort(9000).build()
                );
            }
        }

        // Replicas
        mergeDeploymentPodTemplateSpec(
                COMPONENT_APP_SPEC_FIELD_NAME,
                primary,
                r.getSpec().getTemplate(),
                REGISTRY_APP_CONTAINER_NAME,
                Configuration.getAppImage(),
                containerPort,
                readinessProbe,
                livenessProbe,
                Map.of("cpu", new Quantity("500m"), "memory", new Quantity("512Mi")),
                Map.of("cpu", new Quantity("1"), "memory", new Quantity("1Gi"))
        );

        addDefaultLabels(r.getMetadata().getLabels(), primary, COMPONENT_APP);
        addSelectorLabels(r.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_APP);
        addDefaultLabels(r.getSpec().getTemplate().getMetadata().getLabels(), primary, COMPONENT_APP);
        return r;
    }

    public Deployment getDefaultUIDeployment(ApicurioRegistry3 primary) {
        var uiAutoscaling = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                .map(ComponentSpec::getAutoscaling).orElse(null);
        boolean uiAutoscalingEnabled = ofNullable(uiAutoscaling).map(AutoscalingSpec::getEnabled)
                .orElse(Boolean.FALSE);

        int uiReplicas;
        if (uiAutoscalingEnabled) {
            uiReplicas = ofNullable(uiAutoscaling).map(AutoscalingSpec::getMinReplicas)
                    .orElse(DEFAULT_REPLICAS);
            var explicitReplicas = ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getReplicas).orElse(null);
            if (explicitReplicas != null) {
                StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                        .recordError("Both spec.ui.replicas and spec.ui.autoscaling are configured. "
                                + "The replicas field will be ignored when autoscaling is enabled.");
            }
        } else {
            uiReplicas = Optional.ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                    .map(UiSpec::getReplicas).orElse(DEFAULT_REPLICAS);
        }

        var r = initDefaultDeployment(primary, COMPONENT_UI, uiReplicas,
                ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                        .map(UiSpec::getPodTemplateSpec).orElse(null));
        // Replicas
        mergeDeploymentPodTemplateSpec(
                COMPONENT_UI_SPEC_FIELD_NAME,
                primary,
                r.getSpec().getTemplate(),
                REGISTRY_UI_CONTAINER_NAME,
                Configuration.getUIImage(),
                List.of(new ContainerPortBuilder().withName("http").withProtocol("TCP").withContainerPort(8080).build()),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/config.js").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build(),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/config.js").withPort(new IntOrString(8080)).withScheme("HTTP").build()).build(),
                Map.of("cpu", new Quantity("100m"), "memory", new Quantity("256Mi")),
                Map.of("cpu", new Quantity("200m"), "memory", new Quantity("512Mi"))
        );
        addDefaultLabels(r.getMetadata().getLabels(), primary, COMPONENT_UI);
        addSelectorLabels(r.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_UI);
        addDefaultLabels(r.getSpec().getTemplate().getMetadata().getLabels(), primary, COMPONENT_UI);
        return r;
    }

    public Deployment getDefaultConsolePluginDeployment(ApicurioRegistry3 primary) {
        var r = initDefaultDeployment(primary, COMPONENT_CONSOLE_PLUGIN, 1, null);
        mergeDeploymentPodTemplateSpec(
                COMPONENT_CONSOLE_PLUGIN_SPEC_FIELD_NAME,
                primary,
                r.getSpec().getTemplate(),
                CONSOLE_PLUGIN_CONTAINER_NAME,
                Configuration.getConsolePluginImage().orElseThrow(),
                List.of(new ContainerPortBuilder().withName("https").withProtocol("TCP").withContainerPort(9443).build()),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/q/health/ready").withPort(new IntOrString(9443)).withScheme("HTTPS").build()).withInitialDelaySeconds(5).withPeriodSeconds(10).build(),
                new ProbeBuilder().withHttpGet(new HTTPGetActionBuilder().withPath("/q/health/live").withPort(new IntOrString(9443)).withScheme("HTTPS").build()).withInitialDelaySeconds(5).withPeriodSeconds(10).build(),
                Map.of("cpu", new Quantity("100m"), "memory", new Quantity("256Mi")),
                Map.of("cpu", new Quantity("500m"), "memory", new Quantity("512Mi"))
        );
        var pts = r.getSpec().getTemplate();
        if (pts.getSpec() == null) {
            pts.setSpec(new PodSpec());
        }
        if (pts.getSpec().getVolumes() == null) {
            pts.getSpec().setVolumes(new ArrayList<>());
        }
        pts.getSpec().getVolumes().add(new VolumeBuilder()
                .withName("serving-cert")
                .withNewSecret()
                .withSecretName(primary.getMetadata().getName() + "-" + COMPONENT_CONSOLE_PLUGIN + "-cert")
                .withDefaultMode(420)
                .endSecret()
                .build());
        var container = getContainerFromPodTemplateSpec(pts, CONSOLE_PLUGIN_CONTAINER_NAME);
        if (container != null) {
            if (container.getVolumeMounts() == null) {
                container.setVolumeMounts(new ArrayList<>());
            }
            container.getVolumeMounts().add(new VolumeMountBuilder()
                    .withName("serving-cert")
                    .withMountPath("/var/serving-cert")
                    .withReadOnly(true)
                    .build());
        }
        addDefaultLabels(r.getMetadata().getLabels(), primary, COMPONENT_CONSOLE_PLUGIN);
        addSelectorLabels(r.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_CONSOLE_PLUGIN);
        addDefaultLabels(r.getSpec().getTemplate().getMetadata().getLabels(), primary, COMPONENT_CONSOLE_PLUGIN);
        return r;
    }

    public Service getDefaultConsolePluginService(ApicurioRegistry3 primary) {
        var r = getDefaultResource(primary, Service.class, RESOURCE_TYPE_SERVICE, COMPONENT_CONSOLE_PLUGIN);
        r.getMetadata().getAnnotations().put("service.beta.openshift.io/serving-cert-secret-name",
                primary.getMetadata().getName() + "-" + COMPONENT_CONSOLE_PLUGIN + "-cert");
        addSelectorLabels(r.getSpec().getSelector(), primary, COMPONENT_CONSOLE_PLUGIN);
        return r;
    }

    private static Deployment initDefaultDeployment(ApicurioRegistry3 primary, String componentId,
                                                    int replicas, PodTemplateSpec pts) {
        var r = new Deployment();
        r.setMetadata(new ObjectMeta());
        r.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        r.getMetadata().setName(
                primary.getMetadata().getName() + "-" + componentId + "-" + RESOURCE_TYPE_DEPLOYMENT);
        r.setSpec(new DeploymentSpec());
        r.getSpec().setReplicas(replicas);
        r.getSpec().setSelector(new LabelSelector());
        if (pts != null) {
            r.getSpec().setTemplate(pts);
        } else {
            r.getSpec().setTemplate(new PodTemplateSpec());
        }
        return r;
    }

    /**
     * Merge default values for a Deployment into the target PTS (from spec).
     */
    private static void mergeDeploymentPodTemplateSpec(
            String componentFieldName,
            ApicurioRegistry3 primary,
            PodTemplateSpec target,
            String containerName,
            String image,
            List<ContainerPort> ports,
            Probe readinessProbe,
            Probe livenessProbe,
            Map<String, Quantity> requests,
            Map<String, Quantity> limits
    ) {
        // Validate that the containers do not have an empty name
        getContainersFromPTS(target)
                .filter(container -> isBlank(container.getName()))
                .findAny()
                .ifPresent(container -> {
                    StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                            .recordError("""
                                            Field `spec.%s.podTemplateSpec.spec.containers` has a container without a name. \
                                            If you meant to modify a container for the Registry %s component, use name '%s', \
                                            otherwise specify a name for your custom container.""",
                                    componentFieldName, componentFieldName, containerName);
                });

        if (target.getMetadata() == null) {
            target.setMetadata(new ObjectMeta());
        }
        var c = getContainerFromPodTemplateSpec(target, containerName);
        if (c == null) {
            getContainersFromPTS(target)
                    .filter(container -> !containerName.equals(container.getName()))
                    // TODO: The next line will reduce the number of false positives, but maybe it would be worth it?
                    .filter(container -> Stream.of("registry", "app", "ui", "apicurio").anyMatch(n -> container.getName().contains(n)))
                    .findAny()
                    .ifPresent(container -> {
                        log.warn("""
                                        Container for component {} not found in `spec.{}.podTemplateSpec.spec.containers`, but instead found '{}'. \
                                        If you meant to modify a container for the Registry {} component, use name '{}', otherwise ignore this message.""",
                                componentFieldName, componentFieldName, container.getName(),
                                componentFieldName, containerName);
                    });
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
            StatusManager.get(primary).getConditionManager(ValidationErrorConditionManager.class)
                    .recordError("""
                            Field spec.%s.podTemplateSpec.spec.containers[name = %s].env must be empty. \
                            Use spec.%s.env to configure environment variables.""", componentFieldName, containerName, componentFieldName);
        }
        if (c.getPorts() == null) {
            c.setPorts(new ArrayList<>());
        }
        var targetPorts = c.getPorts();
        ports.forEach(sourcePort -> {
            if (targetPorts.stream()
                    .noneMatch(targetPort -> sourcePort.getName().equals(targetPort.getName()))) {
                targetPorts.add(sourcePort);
            }
        });
        if (c.getReadinessProbe() == null) {
            c.setReadinessProbe(readinessProbe);
        }
        if (c.getLivenessProbe() == null) {
            c.setLivenessProbe(livenessProbe);
        }
        if (c.getResources() == null) {
            c.setResources(new ResourceRequirements());
            c.getResources().setRequests(requests);
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

    public PodDisruptionBudget getDefaultAppPodDisruptionBudget(ApicurioRegistry3 primary) {
        var pdb = getDefaultResource(primary, PodDisruptionBudget.class, RESOURCE_TYPE_POD_DISRUPTION_BUDGET,
                COMPONENT_APP);
        addSelectorLabels(pdb.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_APP);
        return pdb;
    }

    public PodDisruptionBudget getDefaultUIPodDisruptionBudget(ApicurioRegistry3 primary) {
        var pdb = getDefaultResource(primary, PodDisruptionBudget.class, RESOURCE_TYPE_POD_DISRUPTION_BUDGET,
                COMPONENT_UI);
        addSelectorLabels(pdb.getSpec().getSelector().getMatchLabels(), primary, COMPONENT_UI);
        return pdb;
    }

    public HorizontalPodAutoscaler getDefaultAppHorizontalPodAutoscaler(ApicurioRegistry3 primary) {
        return buildHorizontalPodAutoscaler(primary, COMPONENT_APP,
                ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getApp)
                        .map(ComponentSpec::getAutoscaling).orElse(null));
    }

    public HorizontalPodAutoscaler getDefaultUIHorizontalPodAutoscaler(ApicurioRegistry3 primary) {
        return buildHorizontalPodAutoscaler(primary, COMPONENT_UI,
                ofNullable(primary.getSpec()).map(ApicurioRegistry3Spec::getUi)
                        .map(ComponentSpec::getAutoscaling).orElse(null));
    }

    private HorizontalPodAutoscaler buildHorizontalPodAutoscaler(ApicurioRegistry3 primary,
            String component, AutoscalingSpec autoscaling) {
        var hpa = getDefaultResource(primary, HorizontalPodAutoscaler.class,
                RESOURCE_TYPE_HORIZONTAL_POD_AUTOSCALER, component);

        var deploymentName = primary.getMetadata().getName() + "-" + component + "-"
                + RESOURCE_TYPE_DEPLOYMENT;
        hpa.getSpec().getScaleTargetRef().setName(deploymentName);

        int minReplicas = ofNullable(autoscaling).map(AutoscalingSpec::getMinReplicas).orElse(1);
        int maxReplicas = ofNullable(autoscaling).map(AutoscalingSpec::getMaxReplicas).orElse(minReplicas);
        hpa.getSpec().setMinReplicas(minReplicas);
        hpa.getSpec().setMaxReplicas(maxReplicas);

        var metrics = new ArrayList<MetricSpec>();
        int cpuTarget = ofNullable(autoscaling).map(AutoscalingSpec::getTargetCPUUtilizationPercentage)
                .orElse(80);
        metrics.add(new MetricSpecBuilder()
                .withType("Resource")
                .withNewResource()
                .withName("cpu")
                .withNewTarget()
                .withType("Utilization")
                .withAverageUtilization(cpuTarget)
                .endTarget()
                .endResource()
                .build());

        ofNullable(autoscaling).map(AutoscalingSpec::getTargetMemoryUtilizationPercentage)
                .ifPresent(memTarget -> metrics.add(new MetricSpecBuilder()
                        .withType("Resource")
                        .withNewResource()
                        .withName("memory")
                        .withNewTarget()
                        .withType("Utilization")
                        .withAverageUtilization(memTarget)
                        .endTarget()
                        .endResource()
                        .build()));

        hpa.getSpec().setMetrics(metrics);
        return hpa;
    }

    private <T extends HasMetadata> T getDefaultResource(ApicurioRegistry3 primary, Class<T> klass,
                                                         String resourceType, String component) {
        var r = deserialize("/k8s/default/" + component + "." + resourceType + ".yaml", klass);
        r.getMetadata().setNamespace(primary.getMetadata().getNamespace());
        r.getMetadata().setName(primary.getMetadata().getName() + "-" + component + "-" + resourceType);
        addDefaultLabels(r.getMetadata().getLabels(), primary, component);
        return r;
    }

    public NetworkPolicy getDefaultAppNetworkPolicy(ApicurioRegistry3 primary) {
        var networkPolicy = getDefaultResource(primary, NetworkPolicy.class, RESOURCE_TYPE_NETWORK_POLICY,
                COMPONENT_APP);
        addSelectorLabels(networkPolicy.getSpec().getPodSelector().getMatchLabels(), primary, COMPONENT_APP);
        return networkPolicy;
    }

    public NetworkPolicy getDefaultUINetworkPolicy(ApicurioRegistry3 primary) {
        var networkPolicy = getDefaultResource(primary, NetworkPolicy.class, RESOURCE_TYPE_NETWORK_POLICY,
                COMPONENT_UI);
        addSelectorLabels(networkPolicy.getSpec().getPodSelector().getMatchLabels(), primary, COMPONENT_UI);
        return networkPolicy;
    }

    private void addDefaultLabels(Map<String, String> labels, ApicurioRegistry3 primary, String component) {
        labels.putAll(Map.of(
                "app", primary.getMetadata().getName(),
                "app.kubernetes.io/name", "apicurio-registry",
                "app.kubernetes.io/component", component,
                "app.kubernetes.io/instance", primary.getMetadata().getName(),
                "app.kubernetes.io/version", Configuration.getRegistryVersion(),
                "app.kubernetes.io/part-of", "apicurio-registry",
                "app.kubernetes.io/managed-by", "apicurio-registry-operator"
        ));
    }

    private void addSelectorLabels(Map<String, String> labels, ApicurioRegistry3 primary, String component) {
        labels.putAll(getSelectorLabels(primary, component));
    }

    public static <T> T deserialize(String path, Class<T> klass) {
        try {
            return YAML_MAPPER.readValue(load(path), klass);
        } catch (JsonProcessingException ex) {
            throw new OperatorException("Could not deserialize resource: " + path, ex);
        }
    }

    public static <T> T deserialize(String path, Class<T> klass, ClassLoader classLoader) {
        try {
            return YAML_MAPPER.readValue(load(path, classLoader), klass);
        } catch (JsonProcessingException ex) {
            throw new OperatorException("Could not deserialize resource: " + path, ex);
        }
    }

    public static String load(String path) {
        return load(path, Thread.currentThread().getContextClassLoader());
    }

    public static String load(String path, ClassLoader classLoader) {
        try (var stream = classLoader.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), Charset.defaultCharset());
        } catch (Exception ex) {
            throw new OperatorException("Could not read resource: " + path, ex);
        }
    }
}
