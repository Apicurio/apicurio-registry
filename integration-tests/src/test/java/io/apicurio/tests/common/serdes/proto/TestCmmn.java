// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: common.proto

package io.apicurio.tests.common.serdes.proto;

public final class TestCmmn {
    private TestCmmn() {
    }

    public static void registerAllExtensions(com.google.protobuf.ExtensionRegistryLite registry) {
    }

    public static void registerAllExtensions(com.google.protobuf.ExtensionRegistry registry) {
        registerAllExtensions((com.google.protobuf.ExtensionRegistryLite) registry);
    }

    public interface UUIDOrBuilder extends
            // @@protoc_insertion_point(interface_extends:io.apicurio.registry.common.proto.UUID)
            com.google.protobuf.MessageOrBuilder {

        /**
         * <code>fixed64 msb = 1;</code>
         */
        long getMsb();

        /**
         * <code>fixed64 lsb = 2;</code>
         */
        long getLsb();
    }

    /**
     * Protobuf type {@code io.apicurio.registry.common.proto.UUID}
     */
    public static final class UUID extends com.google.protobuf.GeneratedMessageV3 implements
            // @@protoc_insertion_point(message_implements:io.apicurio.registry.common.proto.UUID)
            UUIDOrBuilder {
        private static final long serialVersionUID = 0L;

        // Use UUID.newBuilder() to construct.
        private UUID(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
            super(builder);
        }

        private UUID() {
        }

        @Override
        @SuppressWarnings({ "unused" })
        protected Object newInstance(UnusedPrivateParameter unused) {
            return new UUID();
        }

        @Override
        public final com.google.protobuf.UnknownFieldSet getUnknownFields() {
            return this.unknownFields;
        }

        private UUID(com.google.protobuf.CodedInputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            this();
            if (extensionRegistry == null) {
                throw new NullPointerException();
            }
            com.google.protobuf.UnknownFieldSet.Builder unknownFields = com.google.protobuf.UnknownFieldSet
                    .newBuilder();
            try {
                boolean done = false;
                while (!done) {
                    int tag = input.readTag();
                    switch (tag) {
                        case 0:
                            done = true;
                            break;
                        case 9: {

                            msb_ = input.readFixed64();
                            break;
                        }
                        case 17: {

                            lsb_ = input.readFixed64();
                            break;
                        }
                        default: {
                            if (!parseUnknownField(input, unknownFields, extensionRegistry, tag)) {
                                done = true;
                            }
                            break;
                        }
                    }
                }
            } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                throw e.setUnfinishedMessage(this);
            } catch (java.io.IOException e) {
                throw new com.google.protobuf.InvalidProtocolBufferException(e).setUnfinishedMessage(this);
            } finally {
                this.unknownFields = unknownFields.build();
                makeExtensionsImmutable();
            }
        }

        public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
            return TestCmmn.internal_static_io_apicurio_registry_common_proto_UUID_descriptor;
        }

        @Override
        protected FieldAccessorTable internalGetFieldAccessorTable() {
            return TestCmmn.internal_static_io_apicurio_registry_common_proto_UUID_fieldAccessorTable
                    .ensureFieldAccessorsInitialized(UUID.class, Builder.class);
        }

        public static final int MSB_FIELD_NUMBER = 1;
        private long msb_;

        /**
         * <code>fixed64 msb = 1;</code>
         */
        public long getMsb() {
            return msb_;
        }

        public static final int LSB_FIELD_NUMBER = 2;
        private long lsb_;

        /**
         * <code>fixed64 lsb = 2;</code>
         */
        public long getLsb() {
            return lsb_;
        }

        private byte memoizedIsInitialized = -1;

        @Override
        public final boolean isInitialized() {
            byte isInitialized = memoizedIsInitialized;
            if (isInitialized == 1)
                return true;
            if (isInitialized == 0)
                return false;

            memoizedIsInitialized = 1;
            return true;
        }

        @Override
        public void writeTo(com.google.protobuf.CodedOutputStream output) throws java.io.IOException {
            if (msb_ != 0L) {
                output.writeFixed64(1, msb_);
            }
            if (lsb_ != 0L) {
                output.writeFixed64(2, lsb_);
            }
            unknownFields.writeTo(output);
        }

        @Override
        public int getSerializedSize() {
            int size = memoizedSize;
            if (size != -1)
                return size;

            size = 0;
            if (msb_ != 0L) {
                size += com.google.protobuf.CodedOutputStream.computeFixed64Size(1, msb_);
            }
            if (lsb_ != 0L) {
                size += com.google.protobuf.CodedOutputStream.computeFixed64Size(2, lsb_);
            }
            size += unknownFields.getSerializedSize();
            memoizedSize = size;
            return size;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj == this) {
                return true;
            }
            if (!(obj instanceof UUID)) {
                return super.equals(obj);
            }
            UUID other = (UUID) obj;

            if (getMsb() != other.getMsb())
                return false;
            if (getLsb() != other.getLsb())
                return false;
            if (!unknownFields.equals(other.unknownFields))
                return false;
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public int hashCode() {
            if (memoizedHashCode != 0) {
                return memoizedHashCode;
            }
            int hash = 41;
            hash = (19 * hash) + getDescriptor().hashCode();
            hash = (37 * hash) + MSB_FIELD_NUMBER;
            hash = (53 * hash) + com.google.protobuf.Internal.hashLong(getMsb());
            hash = (37 * hash) + LSB_FIELD_NUMBER;
            hash = (53 * hash) + com.google.protobuf.Internal.hashLong(getLsb());
            hash = (29 * hash) + unknownFields.hashCode();
            memoizedHashCode = hash;
            return hash;
        }

        public static UUID parseFrom(java.nio.ByteBuffer data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return PARSER.parseFrom(data);
        }

        public static UUID parseFrom(java.nio.ByteBuffer data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return PARSER.parseFrom(data, extensionRegistry);
        }

        public static UUID parseFrom(com.google.protobuf.ByteString data)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return PARSER.parseFrom(data);
        }

        public static UUID parseFrom(com.google.protobuf.ByteString data,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return PARSER.parseFrom(data, extensionRegistry);
        }

        public static UUID parseFrom(byte[] data) throws com.google.protobuf.InvalidProtocolBufferException {
            return PARSER.parseFrom(data);
        }

        public static UUID parseFrom(byte[] data, com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                throws com.google.protobuf.InvalidProtocolBufferException {
            return PARSER.parseFrom(data, extensionRegistry);
        }

        public static UUID parseFrom(java.io.InputStream input) throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
        }

        public static UUID parseFrom(java.io.InputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input,
                    extensionRegistry);
        }

        public static UUID parseDelimitedFrom(java.io.InputStream input) throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input);
        }

        public static UUID parseDelimitedFrom(java.io.InputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageV3.parseDelimitedWithIOException(PARSER, input,
                    extensionRegistry);
        }

        public static UUID parseFrom(com.google.protobuf.CodedInputStream input) throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input);
        }

        public static UUID parseFrom(com.google.protobuf.CodedInputStream input,
                com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
            return com.google.protobuf.GeneratedMessageV3.parseWithIOException(PARSER, input,
                    extensionRegistry);
        }

        @Override
        public Builder newBuilderForType() {
            return newBuilder();
        }

        public static Builder newBuilder() {
            return DEFAULT_INSTANCE.toBuilder();
        }

        public static Builder newBuilder(UUID prototype) {
            return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
        }

        @Override
        public Builder toBuilder() {
            return this == DEFAULT_INSTANCE ? new Builder() : new Builder().mergeFrom(this);
        }

        @Override
        protected Builder newBuilderForType(BuilderParent parent) {
            Builder builder = new Builder(parent);
            return builder;
        }

        /**
         * Protobuf type {@code io.apicurio.registry.common.proto.UUID}
         */
        public static final class Builder extends com.google.protobuf.GeneratedMessageV3.Builder<Builder>
                implements
                // @@protoc_insertion_point(builder_implements:io.apicurio.registry.common.proto.UUID)
                UUIDOrBuilder {
            public static final com.google.protobuf.Descriptors.Descriptor getDescriptor() {
                return TestCmmn.internal_static_io_apicurio_registry_common_proto_UUID_descriptor;
            }

            @Override
            protected FieldAccessorTable internalGetFieldAccessorTable() {
                return TestCmmn.internal_static_io_apicurio_registry_common_proto_UUID_fieldAccessorTable
                        .ensureFieldAccessorsInitialized(UUID.class, Builder.class);
            }

            // Construct using io.apicurio.registry.support.Cmmn.UUID.newBuilder()
            private Builder() {
                maybeForceBuilderInitialization();
            }

            private Builder(BuilderParent parent) {
                super(parent);
                maybeForceBuilderInitialization();
            }

            private void maybeForceBuilderInitialization() {
                if (com.google.protobuf.GeneratedMessageV3.alwaysUseFieldBuilders) {
                }
            }

            @Override
            public Builder clear() {
                super.clear();
                msb_ = 0L;

                lsb_ = 0L;

                return this;
            }

            @Override
            public com.google.protobuf.Descriptors.Descriptor getDescriptorForType() {
                return TestCmmn.internal_static_io_apicurio_registry_common_proto_UUID_descriptor;
            }

            @Override
            public UUID getDefaultInstanceForType() {
                return UUID.getDefaultInstance();
            }

            @Override
            public UUID build() {
                UUID result = buildPartial();
                if (!result.isInitialized()) {
                    throw newUninitializedMessageException(result);
                }
                return result;
            }

            @Override
            public UUID buildPartial() {
                UUID result = new UUID(this);
                result.msb_ = msb_;
                result.lsb_ = lsb_;
                onBuilt();
                return result;
            }

            @Override
            public Builder clone() {
                return super.clone();
            }

            @Override
            public Builder setField(com.google.protobuf.Descriptors.FieldDescriptor field, Object value) {
                return super.setField(field, value);
            }

            @Override
            public Builder clearField(com.google.protobuf.Descriptors.FieldDescriptor field) {
                return super.clearField(field);
            }

            @Override
            public Builder clearOneof(com.google.protobuf.Descriptors.OneofDescriptor oneof) {
                return super.clearOneof(oneof);
            }

            @Override
            public Builder setRepeatedField(com.google.protobuf.Descriptors.FieldDescriptor field, int index,
                    Object value) {
                return super.setRepeatedField(field, index, value);
            }

            @Override
            public Builder addRepeatedField(com.google.protobuf.Descriptors.FieldDescriptor field,
                    Object value) {
                return super.addRepeatedField(field, value);
            }

            @Override
            public Builder mergeFrom(com.google.protobuf.Message other) {
                if (other instanceof UUID) {
                    return mergeFrom((UUID) other);
                } else {
                    super.mergeFrom(other);
                    return this;
                }
            }

            public Builder mergeFrom(UUID other) {
                if (other == UUID.getDefaultInstance())
                    return this;
                if (other.getMsb() != 0L) {
                    setMsb(other.getMsb());
                }
                if (other.getLsb() != 0L) {
                    setLsb(other.getLsb());
                }
                this.mergeUnknownFields(other.unknownFields);
                onChanged();
                return this;
            }

            @Override
            public final boolean isInitialized() {
                return true;
            }

            @Override
            public Builder mergeFrom(com.google.protobuf.CodedInputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry) throws java.io.IOException {
                UUID parsedMessage = null;
                try {
                    parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    parsedMessage = (UUID) e.getUnfinishedMessage();
                    throw e.unwrapIOException();
                } finally {
                    if (parsedMessage != null) {
                        mergeFrom(parsedMessage);
                    }
                }
                return this;
            }

            private long msb_;

            /**
             * <code>fixed64 msb = 1;</code>
             */
            public long getMsb() {
                return msb_;
            }

            /**
             * <code>fixed64 msb = 1;</code>
             */
            public Builder setMsb(long value) {

                msb_ = value;
                onChanged();
                return this;
            }

            /**
             * <code>fixed64 msb = 1;</code>
             */
            public Builder clearMsb() {

                msb_ = 0L;
                onChanged();
                return this;
            }

            private long lsb_;

            /**
             * <code>fixed64 lsb = 2;</code>
             */
            public long getLsb() {
                return lsb_;
            }

            /**
             * <code>fixed64 lsb = 2;</code>
             */
            public Builder setLsb(long value) {

                lsb_ = value;
                onChanged();
                return this;
            }

            /**
             * <code>fixed64 lsb = 2;</code>
             */
            public Builder clearLsb() {

                lsb_ = 0L;
                onChanged();
                return this;
            }

            @Override
            public final Builder setUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
                return super.setUnknownFields(unknownFields);
            }

            @Override
            public final Builder mergeUnknownFields(final com.google.protobuf.UnknownFieldSet unknownFields) {
                return super.mergeUnknownFields(unknownFields);
            }

            // @@protoc_insertion_point(builder_scope:io.apicurio.registry.common.proto.UUID)
        }

        // @@protoc_insertion_point(class_scope:io.apicurio.registry.common.proto.UUID)
        private static final UUID DEFAULT_INSTANCE;
        static {
            DEFAULT_INSTANCE = new UUID();
        }

        public static UUID getDefaultInstance() {
            return DEFAULT_INSTANCE;
        }

        private static final com.google.protobuf.Parser<UUID> PARSER = new com.google.protobuf.AbstractParser<UUID>() {
            @Override
            public UUID parsePartialFrom(com.google.protobuf.CodedInputStream input,
                    com.google.protobuf.ExtensionRegistryLite extensionRegistry)
                    throws com.google.protobuf.InvalidProtocolBufferException {
                return new UUID(input, extensionRegistry);
            }
        };

        public static com.google.protobuf.Parser<UUID> parser() {
            return PARSER;
        }

        @Override
        public com.google.protobuf.Parser<UUID> getParserForType() {
            return PARSER;
        }

        @Override
        public UUID getDefaultInstanceForType() {
            return DEFAULT_INSTANCE;
        }

    }

    private static final com.google.protobuf.Descriptors.Descriptor internal_static_io_apicurio_registry_common_proto_UUID_descriptor;
    private static final com.google.protobuf.GeneratedMessageV3.FieldAccessorTable internal_static_io_apicurio_registry_common_proto_UUID_fieldAccessorTable;

    public static com.google.protobuf.Descriptors.FileDescriptor getDescriptor() {
        return descriptor;
    }

    private static com.google.protobuf.Descriptors.FileDescriptor descriptor;
    static {
        String[] descriptorData = { "\n\014common.proto\022!io.apicurio.registry.com"
                + "mon.proto\" \n\004UUID\022\013\n\003msb\030\001 \001(\006\022\013\n\003lsb\030\002 "
                + "\001(\006B)\n!io.apicurio.registry.common.proto" + "B\004Cmmnb\006proto3" };
        descriptor = com.google.protobuf.Descriptors.FileDescriptor.internalBuildGeneratedFileFrom(
                descriptorData, new com.google.protobuf.Descriptors.FileDescriptor[] {});
        internal_static_io_apicurio_registry_common_proto_UUID_descriptor = getDescriptor().getMessageTypes()
                .get(0);
        internal_static_io_apicurio_registry_common_proto_UUID_fieldAccessorTable = new com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
                internal_static_io_apicurio_registry_common_proto_UUID_descriptor,
                new String[] { "Msb", "Lsb", });
    }

    // @@protoc_insertion_point(outer_class_scope)
}
