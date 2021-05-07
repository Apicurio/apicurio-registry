package io.apicurio.registry.storage.impl.elasticsql;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.util.List;

/**
 * @author vvilerio
 */
@ApplicationScoped
public class ElasticSqlFactory {

    private static final Logger log = LoggerFactory.getLogger(ElasticSqlFactory.class);

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.hosts")
    List<String> hosts;

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.username")
    String username;

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.password")
    String password;

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.protocol")
    String protocol;

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.connection-timeout")
    String connectionTimeout;

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.socket-timeout")
    String socketTimeout;

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.max-connections")
    Integer maxConnections;

    @Inject
    @ConfigProperty(name = "quarkus.elasticsearch.max-connections-per-route")
    Integer maxConnectionsPerRoute;




    @ApplicationScoped
    @Produces
    public ElasticSqlConfiguration createConfiguration() {
        ElasticSqlConfiguration config = new ElasticSqlConfiguration() {

            @Override
            public List<String> hosts() {
                return hosts;
            }

            @Override
            public String username() {
                return username;
            }

            @Override
            public String password() {
                return password;
            }

            @Override
            public String protocol() {
                return protocol;
            }

            @Override
            public String connectionTimeout() {
                return connectionTimeout;
            }

            @Override
            public String socketTimeout() {
                return socketTimeout;
            }

            @Override
            public Integer maxConnections() {
                return maxConnections;
            }

            @Override
            public Integer maxConnectionsPerRoute() {
                return maxConnectionsPerRoute;
            }

            @Override
            public String toString() {
                return "hosts: ".concat(hosts.toString())
                        .concat("username: ").concat(username);
            }
        };
        log.info("ElasticSqlConfiguration generated : {}",config.toString());
        return config;
    }



}
