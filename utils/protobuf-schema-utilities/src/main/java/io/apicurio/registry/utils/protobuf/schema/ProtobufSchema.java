package io.apicurio.registry.utils.protobuf.schema;

import java.util.Objects;

import com.google.protobuf.Descriptors.FileDescriptor;
import com.squareup.wire.schema.internal.parser.ProtoFileElement;


public class ProtobufSchema {

    private final FileDescriptor fileDescriptor;
    private ProtoFileElement protoFileElement;
    private ProtobufFile protobufFile;

    public ProtobufSchema(FileDescriptor fileDescriptor, ProtoFileElement protoFileElement) {
        Objects.requireNonNull(fileDescriptor);
        Objects.requireNonNull(protoFileElement);
        this.fileDescriptor = fileDescriptor;
        this.protoFileElement = protoFileElement;
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

}
