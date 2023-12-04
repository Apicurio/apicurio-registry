package io.apicurio.tests.common.serdes;

import java.util.Objects;



public class TestObject {

    private String name;

    public TestObject() {
    }

    public TestObject(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TestObject tester = (TestObject) o;
        return name.equals(tester.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}