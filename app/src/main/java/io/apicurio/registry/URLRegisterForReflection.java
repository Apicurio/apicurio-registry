package io.apicurio.registry;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.net.URL;

@RegisterForReflection(targets = URL.class)
public class URLRegisterForReflection {

}
