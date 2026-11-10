package io.apicurio.registry.resolver.strategy;

/**
 * Registers a referenced schema in the group the resolver would use anyway, with the reference name as its
 * artifact ID. This is the behaviour of serdes before reference naming strategies existed.
 */
public class DefaultReferenceNamingStrategy implements ReferenceNamingStrategy {

    /**
     * {@inheritDoc}
     */
    @Override
    public ReferenceCoordinates name(ReferenceNamingContext context) {
        return new ReferenceCoordinates(context.defaultGroupId(), context.referenceName());
    }
}
