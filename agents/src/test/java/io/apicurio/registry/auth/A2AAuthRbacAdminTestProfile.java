package io.apicurio.registry.auth;

import java.util.Map;

/**
 * {@link A2AAuthTestProfile} with admin override off: administrators are recognised only through the
 * RBAC admin role (issue #10951).
 */
public class A2AAuthRbacAdminTestProfile extends A2AAuthTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> map = super.getConfigOverrides();
        map.put("apicurio.auth.admin-override.enabled", "false");
        return map;
    }
}
