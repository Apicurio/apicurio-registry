package io.apicurio.registry.sync.model;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("apicurio.io")
@Version("v1alpha1")
public class ApicurioAgentCard extends CustomResource<ApicurioAgentCardSpec, ApicurioAgentCardStatus>
        implements Namespaced {
}
