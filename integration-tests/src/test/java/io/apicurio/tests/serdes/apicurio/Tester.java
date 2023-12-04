package io.apicurio.tests.serdes.apicurio;


public interface Tester {

    public void test() throws Exception;

    public static interface TesterBuilder {
        public Tester build();
    }

}