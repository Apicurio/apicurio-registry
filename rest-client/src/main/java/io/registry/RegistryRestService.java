package io.registry;

import io.apicurio.registry.rest.ArtifactsResource;
import io.apicurio.registry.rest.IdsResource;
import io.apicurio.registry.rest.RulesResource;
import io.apicurio.registry.rest.SearchResource;

public interface RegistryRestService extends ArtifactsResource, IdsResource, RulesResource, SearchResource, AutoCloseable  {
}
