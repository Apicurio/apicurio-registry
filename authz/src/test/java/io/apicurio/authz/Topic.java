package io.apicurio.authz;

import io.kroxylicious.authorizer.service.ResourceType;

/** A resource type of another system (as in Kroxylicious), which Registry does not enforce. */
public enum Topic implements ResourceType<Topic> {
    READ
}
