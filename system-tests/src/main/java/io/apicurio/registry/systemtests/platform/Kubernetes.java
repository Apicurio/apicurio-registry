package io.apicurio.registry.systemtests.platform;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.systemtests.framework.OperatorUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.StatefulSet;
import io.fabric8.kubernetes.api.model.apps.StatefulSetStatus;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.Route;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.CatalogSource;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfig;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class Kubernetes {
    private static Kubernetes instance;
    private static KubernetesClient client;

    private Kubernetes() {
        Config config = Config.autoConfigure(
                System.getenv().getOrDefault("TEST_CLUSTER_CONTEXT", null)
        );

        client = new DefaultOpenShiftClient(new OpenShiftConfig(config));
    }

    public static Kubernetes getInstance() {
        if (instance == null) {
            instance = new Kubernetes();
        }

        return instance;
    }

    public static KubernetesClient getClient() {
        return getInstance().client;
    }

    public static List<HasMetadata> loadFromFile(Path path) {
        try {
            return getClient().load(new FileInputStream(path.toString())).get();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<HasMetadata> loadFromFile(String path) {
        return loadFromFile(Path.of(path));
    }

    public static List<HasMetadata> loadFromDirectory(Path path) {
        List<String> filenames;

        try {
            // Get list of files in path
            filenames = OperatorUtils.listFiles(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Initialize resources
        List<HasMetadata> resources = new ArrayList<>();

        // Load files to resources
        for (String file : filenames) {
            // Load one file and add all resources from file to resources
            resources.addAll(loadFromFile(Paths.get(path.toString(), file)));
        }

        return resources;
    }

    public static void createOrReplaceResources(String namespace, Collection<HasMetadata> resourcesList) {
        getClient()
                .resourceList(resourcesList)
                .inNamespace(namespace)
                .createOrReplace();
    }

    public static void deleteResources(String namespace, Collection<HasMetadata> resourcesList) {
        getClient()
                .resourceList(resourcesList)
                .inNamespace(namespace)
                .delete();
    }

    public static Secret getSecret(String namespace, String name) {
        return getClient()
                .secrets()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static void createSecret(String namespace, Secret secret) {
        getClient()
                .secrets()
                .inNamespace(namespace)
                .create(secret);
    }

    public static void createOrReplaceSecret(String namespace, Secret secret) {
        getClient()
                .secrets()
                .inNamespace(namespace)
                .createOrReplace(secret);
    }

    public static void deleteSecret(String namespace, String name) {
        getClient()
                .secrets()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static StatefulSet getStatefulSet(String namespace, String name) {
        return getClient()
                .apps()
                .statefulSets()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static void createCatalogSource(String namespace, CatalogSource catalogSource) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .catalogSources()
                .inNamespace(namespace)
                .create(catalogSource);
    }

    public static void createOrReplaceCatalogSource(String namespace, CatalogSource catalogSource) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .catalogSources()
                .inNamespace(namespace)
                .createOrReplace(catalogSource);
    }

    public static CatalogSource getCatalogSource(String namespace, String name) {
        return ((OpenShiftClient) getClient())
                .operatorHub()
                .catalogSources()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static void deleteCatalogSource(String namespace, String name) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .catalogSources()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static boolean isCatalogSourceReady(String namespace, String name) {
        CatalogSource catalogSource = getCatalogSource(namespace, name);

        if (catalogSource == null || catalogSource.getStatus() == null) {
            return false;
        }

        return catalogSource.getStatus().getConnectionState().getLastObservedState().equals("READY");
    }

    public static void createNamespace(Namespace namespace) {
        Kubernetes.getClient()
                .namespaces()
                .create(namespace);
    }

    public static void createOrReplaceNamespace(Namespace namespace) {
        Kubernetes.getClient()
                .namespaces()
                .createOrReplace(namespace);
    }

    public static Namespace getNamespace(String name) {
        return getClient()
                .namespaces()
                .withName(name)
                .get();
    }

    public static Namespace getNamespace(Namespace namespace) {
        return getNamespace(namespace.getMetadata().getName());
    }

    public static void deleteNamespace(String name) {
        getClient()
                .namespaces()
                .withName(name)
                .delete();
    }

    public static Route getRoute(String namespace, String name) {
        return ((OpenShiftClient) getClient())
                .routes()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static Route getRoute(ApicurioRegistry apicurioRegistry) {
        return ((OpenShiftClient) getClient())
                .routes()
                .inNamespace(apicurioRegistry.getMetadata().getNamespace())
                .withLabels(Collections.singletonMap("app", apicurioRegistry.getMetadata().getName()))
                .list()
                .getItems()
                .get(0);
    }

    public static void createRoute(String namespace, Route route) {
        ((OpenShiftClient) getClient())
                .routes()
                .inNamespace(namespace)
                .create(route);
    }

    public static void createOrReplaceRoute(String namespace, Route route) {
        ((OpenShiftClient) getClient())
                .routes()
                .inNamespace(namespace)
                .createOrReplace(route);
    }

    public static void deleteRoute(String namespace, String name) {
        ((OpenShiftClient) getClient())
                .routes()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static boolean isRouteReady(String namespace, String name) {
        Route route = getRoute(namespace, name);

        if (route == null || route.getStatus() == null) {
            return false;
        }

        return route
                .getStatus()
                .getIngress()
                .size() > 0;
    }

    public static PodList getPods(String namespace, String labelKey, String labelValue) {
        return getClient()
                .pods()
                .inNamespace(namespace)
                .withLabel(labelKey, labelValue)
                .list();
    }

    public static void deletePods(String namespace, String labelKey, String labelValue) {
        getClient()
                .pods()
                .inNamespace(namespace)
                .withLabel(labelKey, labelValue)
                .delete();
    }

    public static OperatorGroup getOperatorGroup(String namespace, String name) {
        return ((OpenShiftClient) getClient())
                .operatorHub()
                .operatorGroups()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static void createOperatorGroup(String namespace, OperatorGroup operatorGroup) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .operatorGroups()
                .inNamespace(namespace)
                .create(operatorGroup);
    }

    public static void createOrReplaceOperatorGroup(String namespace, OperatorGroup operatorGroup) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .operatorGroups()
                .inNamespace(namespace)
                .createOrReplace(operatorGroup);
    }

    public static void deleteOperatorGroup(String namespace, String name) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .operatorGroups()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static void createSubscription(String namespace, Subscription subscription) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .subscriptions()
                .inNamespace(namespace)
                .create(subscription);
    }

    public static void createOrReplaceSubscription(String namespace, Subscription subscription) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .subscriptions()
                .inNamespace(namespace)
                .createOrReplace(subscription);
    }

    public static Subscription getSubscription(String namespace, String name) {
        return ((OpenShiftClient) getClient())
                .operatorHub()
                .subscriptions()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static void deleteSubscription(String namespace, String name) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .subscriptions()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static ClusterServiceVersion getClusterServiceVersion(String namespace, String name) {
        return ((OpenShiftClient) getClient())
                .operatorHub()
                .clusterServiceVersions()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static boolean isClusterServiceVersionReady(String namespace, String name) {
        ClusterServiceVersion csvToBeReady = ((OpenShiftClient) getClient())
                .operatorHub()
                .clusterServiceVersions()
                .inNamespace(namespace)
                .withName(name)
                .get();

        if (csvToBeReady == null || csvToBeReady.getStatus() == null) {
            return false;
        }

        return csvToBeReady
                .getStatus()
                .getPhase()
                .equals("Succeeded");
    }

    public static void deleteClusterServiceVersion(String namespace, String name) {
        ((OpenShiftClient) getClient())
                .operatorHub()
                .clusterServiceVersions()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static String getRouteHost(String namespace, String name) {
        Route route = getRoute(namespace, name);

        if (route == null || route.getStatus() == null) {
            return null;
        }

        return route
                .getStatus()
                .getIngress()
                .get(0)
                .getHost();
    }

    public static String getSecretValue(String namespace, String name, String secretKey) {
        return getSecret(namespace, name)
                .getData()
                .get(secretKey);
    }

    public static PackageManifest getPackageManifest(String catalog, String name) {
        return ((OpenShiftClient) Kubernetes.getClient())
                .operatorHub()
                .packageManifests()
                .list()
                .getItems()
                .stream()
                .filter(p -> p.getMetadata().getName().equals(name))
                .filter(p -> p.getMetadata().getLabels().get("catalog").equals(catalog))
                .findFirst()
                .orElse(null);
    }

    public static Deployment getDeployment(String namespace, String name) {
        try {
            return getClient()
                    .apps()
                    .deployments()
                    .inNamespace(namespace)
                    .withName(name)
                    .get();
        } catch (Exception e) {
            return null;
        }

    }

    public static Deployment getDeploymentByPrefix(String namespace, String prefix) {
        return getClient()
                .apps()
                .deployments()
                .inNamespace(namespace)
                .list()
                .getItems()
                .stream()
                .filter(d -> d.getMetadata().getName().startsWith(prefix))
                .findFirst()
                .orElse(null);
    }

    public static void createDeployment(String namespace, Deployment deployment) {
        getClient()
                .apps()
                .deployments()
                .inNamespace(namespace)
                .create(deployment);
    }

    public static void createOrReplaceDeployment(String namespace, Deployment deployment) {
        getClient()
                .apps()
                .deployments()
                .inNamespace(namespace)
                .createOrReplace(deployment);
    }

    public static void deleteDeployment(String namespace, String name) {
        getClient()
                .apps()
                .deployments()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static Service getService(String namespace, String name) {
        return getClient()
                .services()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static void createService(String namespace, Service service) {
        getClient()
                .services()
                .inNamespace(namespace)
                .create(service);
    }

    public static void createOrReplaceService(String namespace, Service service) {
        getClient()
                .services()
                .inNamespace(namespace)
                .createOrReplace(service);
    }

    public static void deleteService(String namespace, String name) {
        getClient()
                .services()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static boolean isServiceReady(String namespace, Map<String, String> selector) {
        return getClient()
                .pods()
                .inNamespace(namespace)
                .withLabels(selector)
                .list()
                .getItems()
                .size() > 0;
    }

    public static PersistentVolumeClaim getPersistentVolumeClaim(String namespace, String name) {
        return getClient()
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(name)
                .get();
    }

    public static void createPersistentVolumeClaim(String namespace, PersistentVolumeClaim volumeClaim) {
        getClient()
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .create(volumeClaim);
    }

    public static void createOrReplacePersistentVolumeClaim(String namespace, PersistentVolumeClaim volumeClaim) {
        getClient()
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .createOrReplace(volumeClaim);
    }

    public static void deletePersistentVolumeClaim(String namespace, String name) {
        getClient()
                .persistentVolumeClaims()
                .inNamespace(namespace)
                .withName(name)
                .delete();
    }

    public static boolean isStatefulSetReady(String namespace, String name) {
        StatefulSet statefulSet = Kubernetes.getStatefulSet(namespace, name);

        if (statefulSet == null || statefulSet.getStatus() == null) {
            return false;
        }

        StatefulSetStatus status = statefulSet.getStatus();

        if (status.getReadyReplicas() == null) {
            return false;
        }

        return status.getReadyReplicas() > 0;
    }

    public static <T extends HasMetadata> MixedOperation<T, KubernetesResourceList<T>, Resource<T>>
    getResources(Class<T> tClass) {
        return Kubernetes.getClient().resources(tClass);
    }

    public static boolean namespaceHasAnyOperatorGroup(String name) {
        int namespaceOperatorGroupsCount = ((OpenShiftClient) getClient())
                .operatorHub()
                .operatorGroups()
                .inNamespace(name)
                .list()
                .getItems()
                .size();
        return namespaceOperatorGroupsCount > 0;
    }
}
