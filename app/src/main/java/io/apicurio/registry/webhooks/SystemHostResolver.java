package io.apicurio.registry.webhooks;

import jakarta.enterprise.context.ApplicationScoped;

import java.net.InetAddress;
import java.net.UnknownHostException;

@ApplicationScoped
public class SystemHostResolver implements HostResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
