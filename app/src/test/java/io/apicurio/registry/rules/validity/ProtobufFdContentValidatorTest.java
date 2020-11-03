/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.rules.validity;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors;

import io.apicurio.registry.AbstractRegistryTestBase;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.rules.RuleViolationException;
import io.apicurio.registry.support.TestCmmn;

/**
 * Tests the Protobuf FD content validator.
 * @author Ales Justin
 */
public class ProtobufFdContentValidatorTest extends AbstractRegistryTestBase {

    private Serde.Schema toSchemaProto(Descriptors.FileDescriptor file) {
        Serde.Schema.Builder b = Serde.Schema.newBuilder();
        b.setFile(file.toProto());
        for (Descriptors.FileDescriptor d : file.getDependencies()) {
            b.addImport(toSchemaProto(d));
        }
        return b.build();
    }

    @Test
    public void testValidProtobufFdContent() {
        TestCmmn.UUID uuid = TestCmmn.UUID.newBuilder()
                                          .setLsb(77)
                                          .setMsb(23)
                                          .build();
        ContentHandle content = ContentHandle.create(toSchemaProto(uuid.getDescriptorForType().getFile()).toByteArray());
        ProtobufFdContentValidator validator = new ProtobufFdContentValidator();
        validator.validate(ValidityLevel.FULL, content);
    }

    @Test
    public void testInvalidProtobufFdContent() {
        ContentHandle content = ContentHandle.create("qwerty");
        ProtobufFdContentValidator validator = new ProtobufFdContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content);
        });
    }

}
