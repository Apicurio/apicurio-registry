package io.apicurio.registry.serde.generic;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@EqualsAndHashCode
@ToString
public class GenericHeaders implements Iterable<GenericHeader> {


    private Map<String, List<GenericHeader>> headers;


    public GenericHeaders() {
        this.headers = new HashMap<>();
    }


    @NotNull
    @Override
    public Iterator<GenericHeader> iterator() {
        return headers.values().stream().flatMap(Collection::stream).iterator();
    }
}
