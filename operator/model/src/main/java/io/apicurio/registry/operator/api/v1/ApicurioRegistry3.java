package io.apicurio.registry.operator.api.v1;

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

    public ApicurioRegistry3Spec withSpec() {
        if (spec == null) {
            spec = new ApicurioRegistry3Spec();
        }
        return spec;
    }

    public ApicurioRegistry3Status withStatus() {
        if (status == null) {
            status = new ApicurioRegistry3Status();
        }
        return status;
    }
}
