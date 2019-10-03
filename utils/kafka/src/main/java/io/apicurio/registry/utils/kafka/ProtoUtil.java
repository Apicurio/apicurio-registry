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

package io.apicurio.registry.utils.kafka;

import io.apicurio.registry.common.proto.Cmmn;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author Ales Justin
 */
public class ProtoUtil {

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

    public static <B, R> R getNullable(B b, Predicate<? super B> tester, Function<? super B, ? extends R> getter) {
        return tester.test(b) ? getter.apply(b) : null;
    }
}