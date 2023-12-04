package io.apicurio.registry.storage.impl.sql;


@FunctionalInterface
public interface IdGenerator {

    Long generate();

    static IdGenerator single(long id) {
        return new SingleIdGenerator(id);
    }

    class SingleIdGenerator implements IdGenerator {

        private final long id;

        public SingleIdGenerator(long id) {
            this.id = id;
        }

        @Override
        public Long generate() {
            return id;
        }
    }
}
