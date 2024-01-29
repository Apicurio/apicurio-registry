package io.apicurio.registry.utils.protobuf.schema;

import java.util.Objects;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;

public class ProtobufSchema {

    private final FileDescriptor fileDescriptor;
    private final Descriptors.Descriptor descriptor;
    private ProtoFileElement protoFileElement;
    private ProtobufFile protobufFile;

    public ProtobufSchema(FileDescriptor fileDescriptor, ProtoFileElement protoFileElement) {
        Objects.requireNonNull(fileDescriptor);
        Objects.requireNonNull(protoFileElement);
        this.fileDescriptor = fileDescriptor;
        this.protoFileElement = protoFileElement;
        this.descriptor = null;
    }

    public ProtobufSchema(Descriptors.Descriptor descriptor, ProtoFileElement protoFileElement) {
        Objects.requireNonNull(descriptor);
        Objects.requireNonNull(protoFileElement);
        this.descriptor = descriptor;
        this.protoFileElement = protoFileElement;
        this.fileDescriptor = null;
    }

    /**
     * @return the fileDescriptor
     */
    public FileDescriptor getFileDescriptor() {
        return fileDescriptor;
    }

    /**
     * @return the protoFileElement
     */
    public ProtoFileElement getProtoFileElement() {
        return protoFileElement;
    }

    /**
     * @return the protobufFile
     */
    public ProtobufFile getProtobufFile() {
        if (protobufFile == null) {
            protobufFile = new ProtobufFile(protoFileElement);
        }
        return protobufFile;
    }

    public Descriptors.Descriptor getDescriptor() {
        return descriptor;
    }
}
