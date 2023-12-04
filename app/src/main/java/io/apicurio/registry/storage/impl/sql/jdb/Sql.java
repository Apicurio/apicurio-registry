package io.apicurio.registry.storage.impl.sql.jdb;

import java.util.Date;


public interface Sql<Q> {

    public Q bind(int position, String value);

    public Q bind(int position, Long value);

    public Q bind(int position, Integer value);

    public Q bind(int position, Enum<?> value);

    public Q bind(int position, Date value);

    public Q bind(int position, byte[] value);


}
