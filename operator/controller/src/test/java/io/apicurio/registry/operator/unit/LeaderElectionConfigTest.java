package io.apicurio.registry.operator.unit;

import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the leader election configuration is correctly wired
 * into the JOSDK {@link ConfigurationServiceOverrider}.
 */
public class LeaderElectionConfigTest {

    @Test
    public void testLeaderElectionConfigurationValues() {
        var leaseName = "test-lease";
        var leaseNamespace = "test-namespace";

        var config = new LeaderElectionConfiguration(leaseName, leaseNamespace);

        assertThat(config.getLeaseName()).isEqualTo(leaseName);
        assertThat(config.getLeaseNamespace()).isPresent().hasValue(leaseNamespace);
    }

    @Test
    public void testLeaderElectionConfigurationDefaults() {
        var leaseName = "apicurio-registry-operator-lease";

        var config = new LeaderElectionConfiguration(leaseName);

        assertThat(config.getLeaseName()).isEqualTo(leaseName);
        assertThat(config.getLeaseNamespace()).isEmpty();
        assertThat(config.getLeaseDuration())
                .isEqualTo(LeaderElectionConfiguration.LEASE_DURATION_DEFAULT_VALUE);
        assertThat(config.getRenewDeadline())
                .isEqualTo(LeaderElectionConfiguration.RENEW_DEADLINE_DEFAULT_VALUE);
        assertThat(config.getRetryPeriod())
                .isEqualTo(LeaderElectionConfiguration.RETRY_PERIOD_DEFAULT_VALUE);
    }
}
