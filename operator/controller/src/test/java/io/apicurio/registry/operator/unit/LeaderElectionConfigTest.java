package io.apicurio.registry.operator.unit;

import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfigurationBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
  * Tests direct construction of {@link LeaderElectionConfiguration},
  * verifying explicit values and documented defaults.
  *
  * <p>This test does not validate wiring into the JOSDK
  * {@link ConfigurationServiceOverrider}.
 */
public class LeaderElectionConfigTest {

    @Test
    public void testLeaderElectionConfigurationValues() {
        var leaseName = "test-lease";
        var leaseNamespace = "test-namespace";

        var config = LeaderElectionConfigurationBuilder.aLeaderElectionConfiguration(leaseName)
                .withLeaseNamespace(leaseNamespace)
                .build();

        assertThat(config.getLeaseName()).isEqualTo(leaseName);
        assertThat(config.getLeaseNamespace()).isPresent().hasValue(leaseNamespace);
    }

    @Test
    public void testLeaderElectionConfigurationDefaults() {
        var leaseName = "apicurio-registry-operator-lease";

        var config = LeaderElectionConfigurationBuilder.aLeaderElectionConfiguration(leaseName).build();

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
