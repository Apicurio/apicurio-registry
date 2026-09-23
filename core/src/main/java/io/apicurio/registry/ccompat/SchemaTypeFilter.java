package io.apicurio.registry.ccompat;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public class SchemaTypeFilter {

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof String)) {
            return true;
        } else {
            return obj.toString().equals("AVRO");
        }
    }
}