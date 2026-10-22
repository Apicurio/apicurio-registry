package io.apicurio.registry.utils.export.mappers;

import java.util.List;
import java.util.stream.Collectors;

public interface Mapper<S, T> {

    T map(S entity);

    default List<T> map(List<S> entities) {
        return entities.stream().map(this::map).collect(Collectors.toList());
    }
}
