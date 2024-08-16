package io.apicurio.registry.operator.api.v3;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

@Group("registry.apicur.io")
@Version("v1")
@ShortNames("registry3")
@Plural("ApicurioRegistries3")
public class ApicurioRegistry3 extends CustomResource<ApicurioRegistry3Spec, ApicurioRegistry3Status>
        implements Namespaced {
}