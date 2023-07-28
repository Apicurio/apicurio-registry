package io.apicurio.registry.systemtests.framework;

import io.apicurio.registry.systemtests.platform.Kubernetes;
import io.apicurio.registry.systemtests.registryinfra.ResourceManager;
import io.apicurio.registry.systemtests.registryinfra.resources.DeploymentResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.RouteResourceType;
import io.apicurio.registry.systemtests.registryinfra.resources.ServiceResourceType;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.Route;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SeleniumUtils {
    public static void deployDefaultSelenium(ExtensionContext testContext) {
        Deployment deployment = DeploymentResourceType.getDefaultSelenium();
        Service service = ServiceResourceType.getDefaultSelenium();
        Route route = RouteResourceType.getDefaultSelenium();

        try {
            ResourceManager.getInstance().createResource(true, deployment);
            ResourceManager.getInstance().createResource(true, service);
            ResourceManager.getInstance().createResource(true, route);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getSeleniumHost(String namespace, String name) {
        return Kubernetes.getRouteHost(namespace, name);
    }

    public static String getDefaultSeleniumHost() {
        return getSeleniumHost("selenium", "selenium-chrome");
    }
}
