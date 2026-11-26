package io.apicurio.tests.gitops.infra;

public final class GitOpsTestFixture {

    public static final String GROUP_ID = "foo";
    public static final String ARTIFACT_ID = "petstore";
    public static final String REGISTRY_ID = "e2e-test";

    private GitOpsTestFixture() {
    }

    public static String seedScript() {
        return String.join(" && ",
                "mkdir -p /tmp/seed/content",
                "cd /tmp/seed",
                "git init --initial-branch=main",
                "git config user.email 'test@apicurio.io'",
                "git config user.name 'Test Init'",
                "printf '%s\\n'"
                        + " '$type: registry-v0'"
                        + " 'registryId: " + REGISTRY_ID + "'"
                        + " 'globalRules:'"
                        + " '  - ruleType: VALIDITY'"
                        + " '    config: FULL'"
                        + " 'properties: []'"
                        + " > registry.registry.yaml",
                "printf '%s\\n'"
                        + " '$type: group-v0'"
                        + " 'registryIds: [" + REGISTRY_ID + "]'"
                        + " 'groupId: " + GROUP_ID + "'"
                        + " > group-foo.registry.yaml",
                "printf '%s\\n'"
                        + " '$type: artifact-v0'"
                        + " 'registryIds: [" + REGISTRY_ID + "]'"
                        + " 'groupId: " + GROUP_ID + "'"
                        + " 'artifactId: " + ARTIFACT_ID + "'"
                        + " 'artifactType: OPENAPI'"
                        + " 'versions:'"
                        + " '  - version: \"1\"'"
                        + " '    state: ENABLED'"
                        + " '    content: ./content/petstore-1.0.0.yaml'"
                        + " 'rules:'"
                        + " '  - ruleType: COMPATIBILITY'"
                        + " '    config: BACKWARD'"
                        + " > artifact-petstore.registry.yaml",
                "printf '%s\\n'"
                        + " 'openapi: \"3.0.0\"'"
                        + " 'info:'"
                        + " '  version: 1.0.0'"
                        + " '  title: Swagger Petstore'"
                        + " 'paths: {}'"
                        + " > content/petstore-1.0.0.yaml",
                "git add .",
                "git commit -m 'Initial test data'",
                "git clone --bare /tmp/seed /repos/default",
                "rm -rf /tmp/seed"
        );
    }
}
