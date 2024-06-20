package io.apicurio.registry.utils.protobuf.schema;

import com.google.protobuf.DescriptorProtos;

public class EnumDefinition {
    // --- public static ---

    public static Builder newBuilder(String enumName) {
        return newBuilder(enumName, null);
    }

    public static Builder newBuilder(String enumName, Boolean allowAlias) {
        return new Builder(enumName, allowAlias);
    }

    // --- public ---

    public String toString() {
        return mEnumType.toString();
    }

    // --- package ---

    DescriptorProtos.EnumDescriptorProto getEnumType() {
        return mEnumType;
    }

    // --- private ---

    private EnumDefinition(DescriptorProtos.EnumDescriptorProto enumType) {
        mEnumType = enumType;
    }

    private DescriptorProtos.EnumDescriptorProto mEnumType;

    /**
     * EnumDefinition.Builder
     */
    public static class Builder {
        // --- public ---

        public Builder addValue(String name, int num) {
            DescriptorProtos.EnumValueDescriptorProto.Builder enumValBuilder = DescriptorProtos.EnumValueDescriptorProto
                    .newBuilder();
            enumValBuilder.setName(name).setNumber(num);
            mEnumTypeBuilder.addValue(enumValBuilder.build());
            return this;
        }

        public EnumDefinition build() {
            return new EnumDefinition(mEnumTypeBuilder.build());
        }

        // --- private ---

        private Builder(String enumName, Boolean allowAlias) {
            mEnumTypeBuilder = DescriptorProtos.EnumDescriptorProto.newBuilder();
            mEnumTypeBuilder.setName(enumName);
            if (allowAlias != null) {
                DescriptorProtos.EnumOptions.Builder optionsBuilder = DescriptorProtos.EnumOptions
                        .newBuilder();
                optionsBuilder.setAllowAlias(allowAlias);
                mEnumTypeBuilder.mergeOptions(optionsBuilder.build());
            }
        }

        private DescriptorProtos.EnumDescriptorProto.Builder mEnumTypeBuilder;
    }
}
