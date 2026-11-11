package io.apicurio.authz;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import io.apicurio.scope.tck.ResourceScopeProviderTck;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Subject;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;

/**
 * Registry's access rules pass the resource scope conformance tests, on the general fixture and
 * on the prefix fixture of {@link AccessRulesTest}.
 */
class AccessRulesConformanceTest {

    @TempDir
    static Path dir;

    private static AccessRulesAuthorizer load(String name, String rules) throws Exception {
        return AccessRulesAuthorizer.create(Files.writeString(dir.resolve(name), rules),
                Set.of(Artifact.class, Group.class));
    }

    @Nested
    class GeneralRules extends ResourceScopeProviderTck<AccessRulesAuthorizer> {
        @Override
        protected AccessRulesAuthorizer provider() throws Exception {
            return load("general.acl", AccessRulesTest.RULES);
        }

        @Override
        protected List<Subject> subjects() {
            return AccessRulesTest.SUBJECTS;
        }

        @Override
        protected List<ResourceType<?>> operations() {
            return AccessRulesTest.OPERATIONS;
        }

        @Override
        protected List<String> resourceNames() {
            return AccessRulesTest.NAMES;
        }
    }

    @Nested
    class PrefixRules extends ResourceScopeProviderTck<AccessRulesAuthorizer> {
        @Override
        protected AccessRulesAuthorizer provider() throws Exception {
            return load("prefix.acl", AccessRulesTest.PREFIX_RULES);
        }

        @Override
        protected List<Subject> subjects() {
            return AccessRulesTest.PREFIX_SUBJECTS;
        }

        @Override
        protected List<ResourceType<?>> operations() {
            return AccessRulesTest.OPERATIONS;
        }

        @Override
        protected List<String> resourceNames() {
            return AccessRulesTest.PREFIX_NAMES;
        }
    }
}
