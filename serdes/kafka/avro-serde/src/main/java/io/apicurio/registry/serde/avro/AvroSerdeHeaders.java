package io.apicurio.registry.serde.avro;

import io.apicurio.registry.serde.headers.SerdeHeaders;
import io.apicurio.registry.utils.IoUtil;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

public class AvroSerdeHeaders {

    private final String encodingHeaderName;

    public AvroSerdeHeaders(boolean isKey) {
        if (isKey) {
            encodingHeaderName = SerdeHeaders.HEADER_KEY_ENCODING;
        } else {
            encodingHeaderName = SerdeHeaders.HEADER_VALUE_ENCODING;
        }
    }

    public void addEncodingHeader(Headers headers, String encoding) {
        headers.add(new RecordHeader(encodingHeaderName, encoding.getBytes()));
    }

    public String getEncoding(Headers headers) {
        Header encodingHeader = headers.lastHeader(encodingHeaderName);
        if (encodingHeader != null) {
            return IoUtil.toString(encodingHeader.value());
        }
        return null;
    }

}
