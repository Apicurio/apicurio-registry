package io.apicurio.registry.utils.converter.json;

public interface FormatStrategy {
    byte[] fromConnectData(long contentId, byte[] payload);

    IdPayload toConnectData(byte[] bytes);

    class IdPayload {
        private long contentId;
        private byte[] payload;

        public IdPayload(long contentId, byte[] payload) {
            this.contentId = contentId;
            this.payload = payload;
        }

        public long getContentId() {
            return contentId;
        }

        public byte[] getPayload() {
            return payload;
        }
    }
}
