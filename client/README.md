If using this client with Jersey already on the classpath, something like this must be used (in your pom.xml):


        <dependency>
            <groupId>io.apicurio</groupId>
            <artifactId>apicurio-registry-client</artifactId>
            <exclusions>
                <exclusion>
                    <groupId>org.jboss.resteasy</groupId>
                    <artifactId>resteasy-client-microprofile</artifactId>
                </exclusion>
            </exclusions>
        </dependency>
        <dependency>
            <groupId>org.glassfish.jersey.ext.microprofile</groupId>
            <artifactId>jersey-mp-rest-client</artifactId>
            <version>2.29.1</version>
        </dependency>
        <dependency>
            <groupId>org.glassfish.jersey.ext.microprofile</groupId>
            <artifactId>jersey-mp-config</artifactId>
            <version>2.29.1</version>
        </dependency>
        <dependency>
            <groupId>org.apache.geronimo.config</groupId>
            <artifactId>geronimo-config-impl</artifactId>
            <version>1.2.2</version>
        </dependency>


(from: https://github.com/Apicurio/apicurio-registry/issues/181)
