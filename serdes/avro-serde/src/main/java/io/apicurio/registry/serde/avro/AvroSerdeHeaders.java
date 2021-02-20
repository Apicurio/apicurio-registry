/*
 * Copyright 2021 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.serde.avro;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;

import io.apicurio.registry.serde.SerdeHeaders;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Fabian Martinez
 */
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
