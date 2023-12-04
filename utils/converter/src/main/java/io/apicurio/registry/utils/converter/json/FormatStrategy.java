package io.apicurio.registry.utils.converter.json;


public interface FormatStrategy {
    byte[] fromConnectData(long globalId, byte[] payload);
    IdPayload toConnectData(byte[] bytes);

    class IdPayload {
        private long globalId;
        private byte[] payload;

        public IdPayload(long globalId, byte[] payload) {
            this.globalId = globalId;
            this.payload = payload;
        }

        public long getGlobalId() {
            return globalId;
        }

        public byte[] getPayload() {
            return payload;
        }
    }
}
