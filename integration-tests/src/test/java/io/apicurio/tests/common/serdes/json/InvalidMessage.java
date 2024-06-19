package io.apicurio.tests.common.serdes.json;

public class InvalidMessage extends Msg {

    private String foo;
    private String bar;

    /**
     * Constructor.
     */
    public InvalidMessage() {
    }

    /**
     * @return the foo
     */
    public String getFoo() {
        return foo;
    }

    /**
     * @param foo the foo to set
     */
    public void setFoo(String foo) {
        this.foo = foo;
    }

    /**
     * @return the bar
     */
    public String getBar() {
        return bar;
    }

    /**
     * @param bar the bar to set
     */
    public void setBar(String bar) {
        this.bar = bar;
    }

}
