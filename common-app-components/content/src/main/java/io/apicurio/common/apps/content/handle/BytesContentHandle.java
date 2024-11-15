package io.apicurio.common.apps.content.handle;

/**
 * @author Ales Justin
 */
class BytesContentHandle extends AbstractContentHandle {

    BytesContentHandle(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public byte[] bytes() {
        return bytes;
    }
}
