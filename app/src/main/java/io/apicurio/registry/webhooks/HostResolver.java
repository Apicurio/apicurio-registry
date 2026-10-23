package io.apicurio.registry.webhooks;

import java.net.InetAddress;
import java.net.UnknownHostException;

@FunctionalInterface
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
