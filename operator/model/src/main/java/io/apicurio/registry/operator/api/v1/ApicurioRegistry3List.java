package io.apicurio.registry.operator.api.v1;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;

/**
 * Useful for the fabric8 client:
 *
 * <pre>
 * {@code
 * var items = client.resources(ApicurioRegistry3.class, ApicurioRegistry3List.class).list().getItems();
 * }</pre>
 */
public class ApicurioRegistry3List extends DefaultKubernetesResourceList<ApicurioRegistry3> {
}
