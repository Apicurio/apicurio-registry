package io.apicurio.registry.support;

import java.util.Objects;

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
