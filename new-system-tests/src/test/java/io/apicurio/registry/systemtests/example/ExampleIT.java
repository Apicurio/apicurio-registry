package io.apicurio.registry.systemtests.example;

import io.fabric8.junit.jupiter.api.KubernetesTest;
import io.fabric8.junit.jupiter.api.LoadKubernetesManifests;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@KubernetesTest
@LoadKubernetesManifests({"/kubernetes/example.yaml"})
public class ExampleIT {

    KubernetesClient client;

    @Test
    void testConfigMapIsInstalled() {
        var configMap = client
                .configMaps()
                .inNamespace(client.getNamespace())
                .withName("test")
                .get();

        assertEquals("Hello world!\n", configMap.getData().get("test.txt"));
    }

}
