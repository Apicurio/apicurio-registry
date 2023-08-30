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

package io.apicurio.registry.support;

import java.util.Objects;

/**
 * @author Ales Justin
 */
public class Tester {

    private String name;
    private TesterState state;

    public Tester() {
    }

    public Tester(String name, TesterState state) {
        this.name = name;
        this.state = state;
    }
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TesterState getState() {
        return state;
    }

    public void setState(TesterState state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Tester tester = (Tester) o;
        return Objects.equals(name, tester.name) && state == tester.state;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, state);
    }

    public enum TesterState {
        ONLINE
    }
}
