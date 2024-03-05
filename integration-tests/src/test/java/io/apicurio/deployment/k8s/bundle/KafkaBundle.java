package io.apicurio.deployment.k8s.bundle;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;

import java.util.List;
import java.util.Map;

public class KafkaBundle extends AbstractResourceBundle {


    public void deploy() {
        var labels = Map.of("app", "kafka-service");
        init("/infra/kafka/kafka-manual.yml", Map.of(), List.of(
                ResourceDescriptor.builder().type(Service.class).count(1).labels(labels).build(),
                ResourceDescriptor.builder().type(Deployment.class).count(1).labels(labels).build(),
                ResourceDescriptor.builder().type(Pod.class).count(1).labels(labels).build()
        ));
        super.deploy();
    }
}
