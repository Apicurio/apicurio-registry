package io.apicurio.registry;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.client.RegistryV2ClientFactory;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.client.RegistryClientFacadeImpl;
import io.apicurio.registry.resolver.client.RegistryClientFacadeImpl_v2;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.utils.tests.TestUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

/**
 * Base class for all tests that need to use the ClientFacade for something, e.g.
 * all of the SerDe tests.
 */
public abstract class AbstractClientFacadeTestBase extends AbstractResourceTestBase {

    public interface ClientFacadeSupplier {
        RegistryClientFacade getFacade(AbstractClientFacadeTestBase test);
    }

    public io.apicurio.registry.rest.client.v2.RegistryClient isolatedClientV2;
    public RegistryClient isolatedClientV3;

    @BeforeAll
    public void createIsolatedClients() {
        isolatedClientV2 = RegistryV2ClientFactory.create(RegistryClientOptions.create()
                .registryUrl(TestUtils.getRegistryV2ApiUrl(testPort))
                .vertx(vertx));
        isolatedClientV3 = RegistryClientFactory.create(RegistryClientOptions.create()
                .registryUrl(TestUtils.getRegistryV3ApiUrl(testPort))
                .vertx(vertx));
    }

    public static Stream<Arguments> isolatedClientFacadeProvider() {
        return Stream.of(
                Arguments.of(Named.of("v3",
                        new AbstractClientFacadeTestBase.ClientFacadeSupplier() {
                            @Override
                            public RegistryClientFacade getFacade(AbstractClientFacadeTestBase test) {
                                return new RegistryClientFacadeImpl(test.isolatedClientV3);
                            }
                        }
                )),
                Arguments.of(Named.of("v2",
                        new AbstractClientFacadeTestBase.ClientFacadeSupplier() {
                            @Override
                            public RegistryClientFacade getFacade(AbstractClientFacadeTestBase test) {
                                return new RegistryClientFacadeImpl_v2(test.isolatedClientV2);
                            }
                        }
                ))
        );
    }


}
