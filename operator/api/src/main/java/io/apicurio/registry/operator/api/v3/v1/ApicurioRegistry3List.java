package io.apicurio.registry.operator.api.v3.v1;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.sundr.builder.annotations.Buildable;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;

@Buildable(editableEnabled = false, builderPackage = "io.fabric8.kubernetes.api.builder")
@Getter
@Setter
@ToString(callSuper = true)
public class ApicurioRegistry3List extends DefaultKubernetesResourceList<ApicurioRegistry3> {

    public ApicurioRegistry3List() {
        setApiVersion("registry.apicur.io/v1");
        setKind("ApicurioRegistries3");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ApicurioRegistry3List other = (ApicurioRegistry3List) o;

        return Objects.equals(getApiVersion(), other.getApiVersion())
                && Objects.equals(getKind(), other.getKind())
                && Objects.equals(getMetadata(), other.getMetadata())
                && Objects.equals(getItems(), other.getItems());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getApiVersion(), getKind(), getMetadata(), getItems());
    }
}
