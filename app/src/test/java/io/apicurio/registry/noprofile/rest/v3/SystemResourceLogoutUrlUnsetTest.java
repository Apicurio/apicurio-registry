package io.apicurio.registry.noprofile.rest.v3;
import io.apicurio.registry.rest.v3.beans.UserInterfaceConfig;
import io.apicurio.registry.rest.v3.impl.SystemResourceImpl;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author kanu
 **/
@QuarkusTest
@TestProfile(SystemResourceLogoutUrlUnsetTestProfile.class)
public class SystemResourceLogoutUrlUnsetTest {
    @Inject
    SystemResourceImpl systemResource;
    @Test
    public void testLogoutUrlAbsentWhenUnset() {
        UserInterfaceConfig config = systemResource.getUIConfig();
        assertFalse(config.getAuth().getOptions().containsKey("logoutUrl"));
    }

}
