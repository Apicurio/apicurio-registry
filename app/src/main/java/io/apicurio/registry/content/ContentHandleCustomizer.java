/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry.content;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.module.SimpleSerializers;
import com.fasterxml.jackson.databind.ser.Serializers;
import io.quarkus.jackson.ObjectMapperCustomizer;

import java.io.IOException;
import java.util.Collections;
import javax.inject.Singleton;

/**
 * @author Ales Justin
 */
@Singleton
public class ContentHandleCustomizer implements ObjectMapperCustomizer {
    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new ContentHandleModule());
    }

    private static class ContentHandleModule extends Module {
        @Override
        public String getModuleName() {
            return ContentHandle.class.getSimpleName();
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public void setupModule(SetupContext context) {
            Serializers serializers = new SimpleSerializers(
                Collections.singletonList(new ContentHandleSerializer())
            );
            context.addSerializers(serializers);
        }
    }

    private static class ContentHandleSerializer extends JsonSerializer<ContentHandle> {
        @Override
        public Class<ContentHandle> handledType() {
            return ContentHandle.class;
        }

        @Override
        public void serialize(ContentHandle value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeRaw(value.content());
        }

        @Override
        public void serializeWithType(ContentHandle value, JsonGenerator gen, SerializerProvider serializers, TypeSerializer typeSer) throws IOException {
            typeSer.writeTypePrefix(gen, typeSer.typeId(value, JsonToken.VALUE_STRING));
            serialize(value, gen, serializers);
            typeSer.writeTypeSuffix(gen, typeSer.typeId(value, JsonToken.VALUE_STRING));
        }
    }
}
