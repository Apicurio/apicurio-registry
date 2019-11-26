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

package io.apicurio.registry.utils;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import io.apicurio.registry.common.proto.Cmmn;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Ales Justin
 */
public class ProtoUtil {

    public static boolean isEmpty(String string) {
        return string == null || string.isEmpty();
    }

    public static String emptyAsNull(String protoString) {
        return isEmpty(protoString) ? null : protoString;
    }

    public static String nullAsEmpty(String nullableString) {
        return nullableString == null ? "" : nullableString;
    }

    public static <B, R> R getNullable(B b, Predicate<? super B> tester, Function<? super B, ? extends R> getter) {
        return tester.test(b) ? getter.apply(b) : null;
    }

    public static <B, T, R> R getNullable(B b, Predicate<? super B> tester, Function<? super B, ? extends T> getter, Function<? super T, ? extends R> converter) {
        return tester.test(b) ? converter.apply(getter.apply(b)) : null;
    }

    public static <B, R> B setNullable(B b, R r, Consumer<? super B> clearer, BiConsumer<? super B, ? super R> setter) {
        return setNullable(b, r, clearer, setter, Function.identity());
    }

    public static <B, T, R> B setNullable(B b, R r, Consumer<? super B> clearer, BiConsumer<? super B, ? super T> setter, Function<? super R, ? extends T> converter) {
        if (r == null) {
            clearer.accept(b);
        } else {
            setter.accept(b, converter.apply(r));
        }
        return b;
    }

    public static String toJson(Message msg) throws InvalidProtocolBufferException {
        JsonFormat.Printer printer = JsonFormat.printer()
                                               .omittingInsignificantWhitespace()
                                               .usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder()
                                                                                         .add(msg.getDescriptorForType())
                                                                                         .build());
        return printer.print(msg);
    }

    public static <T> T fromJson(Message.Builder builder, String json, boolean ignoreUnknownFields) throws InvalidProtocolBufferException {
        JsonFormat.Parser parser = JsonFormat.parser();
        if (ignoreUnknownFields) {
            parser = parser.ignoringUnknownFields();
        }
        parser.merge(json, builder);
        //noinspection unchecked
        return (T) builder.build();
    }

    public static UUID convert(Cmmn.UUID mpUuid) {
        return new UUID(mpUuid.getMsb(), mpUuid.getLsb());
    }

    public static Cmmn.UUID convert(UUID uuid) {
        return Cmmn.UUID
            .newBuilder()
            .setMsb(uuid.getMostSignificantBits())
            .setLsb(uuid.getLeastSignificantBits())
            .build();
    }

}
