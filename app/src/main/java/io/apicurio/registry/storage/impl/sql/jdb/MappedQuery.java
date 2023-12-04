package io.apicurio.registry.storage.impl.sql.jdb;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;


public interface MappedQuery<R> {

    public R one();

    public R first();

    public Optional<R> findOne();

    public Optional<R> findFirst();

    public Optional<R> findLast();

    public List<R> list();

    public Stream<R> stream();

}
