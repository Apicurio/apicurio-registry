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

package io.apicurio.registry.serde.protobuf;

import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.common.errors.SerializationException;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;
import com.squareup.wire.schema.internal.parser.ProtoParser;
import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.serde.SchemaParser;
import io.apicurio.registry.serde.protobuf.schema.FileDescriptorUtils;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;

/**
 * @author Fabian Martinez
 */
public class ProtobufSchemaParser implements SchemaParser<FileDescriptor> {

    /**
     * @see io.apicurio.registry.serde.SchemaParser#artifactType()
     */
    @Override
    public ArtifactType artifactType() {
        return ArtifactType.PROTOBUF;
    }

    /**
     * @see io.apicurio.registry.serde.SchemaParser#parseSchema(byte[])
     */
    @Override
    public FileDescriptor parseSchema(byte[] rawSchema) {
        try {
            //textual .proto file
            ProtoFileElement fileElem = ProtoParser.Companion.parse(FileDescriptorUtils.DEFAULT_LOCATION, IoUtil.toString(rawSchema));
            return FileDescriptorUtils.protoFileToFileDescriptor(fileElem);
        } catch (DescriptorValidationException pe) {
            //compatibility
            //official protobuf file descriptor set (.desc files)
            try {
                DescriptorProtos.FileDescriptorSet set = DescriptorProtos.FileDescriptorSet.parseFrom(rawSchema);
                return FileDescriptor.buildFrom(set.getFile(0), FileDescriptorUtils.baseDependencies());
            } catch (InvalidProtocolBufferException | DescriptorValidationException e1) {
                //old PROTOBUF_FD format, with our custom protobuf message
                try {
                    Serde.Schema s = Serde.Schema.parseFrom(rawSchema);
                    return toFileDescriptor(s);
                } catch (InvalidProtocolBufferException | DescriptorValidationException e) {
                    pe.addSuppressed(e1);
                    pe.addSuppressed(e);
                    throw new SerializationException("Error parsing protobuf schema ", pe);
                }
            }
        }
    }

    private FileDescriptor toFileDescriptor(Serde.Schema s) throws DescriptorValidationException {
        List<FileDescriptor> imports = new ArrayList<>();
        for (Serde.Schema i : s.getImportList()) {
            imports.add(toFileDescriptor(i));
        }
        return FileDescriptor.buildFrom(s.getFile(), imports.toArray(new FileDescriptor[0]));
    }

    /**
     * This method converts the Descriptor to a ProtoFileElement that allows to get a textual representation .proto file
     * @param fileDescriptor
     * @return textual protobuf representation
     */
    public byte[] serializeSchema(Descriptor fileDescriptor) {
        FileDescriptorProto fileProto = fileDescriptor.getFile().toProto();
        ProtoFileElement fileElement = FileDescriptorUtils.fileDescriptorToProtoFile(fileProto);
        return IoUtil.toBytes(fileElement.toSchema());
    }

}
