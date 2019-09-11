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

package io.apicurio.registry.kafka.utils;

import java.util.Objects;

/**
 * Either 1st or 2nd. Never none. Never both.
 */
public final class Oneof2<FST, SND> {

    public static <FST, SND> Oneof2<FST, SND> first(FST first) {
        return new Oneof2<>(Objects.requireNonNull(first), null);
    }

    public static <FST, SND> Oneof2<FST, SND> second(SND second) {
        return new Oneof2<>(null, Objects.requireNonNull(second));
    }

    private final FST fst;
    private final SND snd;

    private Oneof2(FST fst, SND snd) {
        this.fst = fst;
        this.snd = snd;
    }

    public boolean isFirst() {
        return fst != null;
    }

    public boolean isSecond() {
        return snd != null;
    }

    public FST getFirst() {
        return Objects.requireNonNull(fst);
    }

    public SND getSecond() {
        return Objects.requireNonNull(snd);
    }

    @Override
    public String toString() {
        return isFirst() ? getFirst().toString() : getSecond().toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Oneof2<?, ?> either = (Oneof2<?, ?>) o;
        return Objects.equals(fst, either.fst) &&
               Objects.equals(snd, either.snd);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(fst) * 31 + Objects.hashCode(snd);
    }
}
