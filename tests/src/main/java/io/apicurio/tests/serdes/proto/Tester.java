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

package io.apicurio.tests.serdes.proto;

import com.google.protobuf.Descriptors;

import io.apicurio.registry.common.proto.Serde;
import io.apicurio.registry.common.proto.Serde.Schema;

/**
 * @author eric.wittmann@gmail.com
 */
public class Tester {
    
    public static final void main(String [] args) {
        MsgTypes.Msg msg = MsgTypes.Msg.newBuilder().setWhat("Sample message").setWhen(System.currentTimeMillis()).build();
        Schema schema = toSchemaProto(msg.getDescriptorForType().getFile());
        System.out.println(schema.toString());
    }

    private static Serde.Schema toSchemaProto(Descriptors.FileDescriptor file) {
        Serde.Schema.Builder b = Serde.Schema.newBuilder();
        b.setFile(file.toProto());
        for (Descriptors.FileDescriptor d : file.getDependencies()) {
            b.addImport(toSchemaProto(d));
        }
        return b.build();
    }

}
