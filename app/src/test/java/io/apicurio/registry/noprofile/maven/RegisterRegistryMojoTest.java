package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@QuarkusTest
public class RegisterRegistryMojoTest extends RegistryMojoTestBase {
    RegisterRegistryMojo mojo;

    private static final String groupId = "RegisterRegistryMojoTest";

    @BeforeEach
    public void createMojo() {
        this.mojo = new RegisterRegistryMojo();
        this.mojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
    }

    @Test
    public void testRegister() throws IOException, MojoFailureException, MojoExecutionException {
        super.testRegister(mojo, groupId);

        Assertions.assertNotNull(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(KEY_SUBJECT).meta().get());
        Assertions.assertNotNull(clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(VALUE_SUBJECT).meta().get());
    }

    @Test
    public void testSkipRegister() throws IOException, MojoFailureException, MojoExecutionException {
        this.mojo.setSkip(true);
        super.testRegister(mojo, groupId);

        Assertions.assertThrows(ExecutionException.class, () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(KEY_SUBJECT).meta().get().get());
        Assertions.assertThrows(ExecutionException.class, () -> clientV3.groups().byGroupId(groupId).artifacts().byArtifactId(VALUE_SUBJECT).meta().get().get());
    }
}
