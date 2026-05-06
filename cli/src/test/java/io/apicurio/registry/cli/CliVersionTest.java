package io.apicurio.registry.cli;

import io.apicurio.registry.cli.services.CliVersion;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class CliVersionTest {

    @Test
    public void testParseCommunityVersion() {
        var v = CliVersion.parse("3.2.4");
        assertThat(v).isNotNull();
        assertThat(v.major()).isEqualTo(3);
        assertThat(v.minor()).isEqualTo(2);
        assertThat(v.patch()).isEqualTo(4);
        assertThat(v.isProductized()).isFalse();
        assertThat(v.redhatBuild()).isEqualTo(0);
        assertThat(v.toString()).isEqualTo("3.2.4");
    }

    @Test
    public void testParseRedhatVersion() {
        var v = CliVersion.parse("3.1.6.redhat-00011");
        assertThat(v).isNotNull();
        assertThat(v.major()).isEqualTo(3);
        assertThat(v.minor()).isEqualTo(1);
        assertThat(v.patch()).isEqualTo(6);
        assertThat(v.isProductized()).isTrue();
        assertThat(v.redhatBuild()).isEqualTo(11);
    }

    @Test
    public void testParseRedhatVersionWithFinalQualifier() {
        var v = CliVersion.parse("2.6.6.Final-redhat-00001");
        assertThat(v).isNotNull();
        assertThat(v.major()).isEqualTo(2);
        assertThat(v.minor()).isEqualTo(6);
        assertThat(v.patch()).isEqualTo(6);
        assertThat(v.isProductized()).isTrue();
        assertThat(v.redhatBuild()).isEqualTo(1);
    }

    @Test
    public void testParseRedhatVersionWithSP() {
        var v = CliVersion.parse("2.4.4.SP1-redhat-00001");
        assertThat(v).isNotNull();
        assertThat(v.major()).isEqualTo(2);
        assertThat(v.minor()).isEqualTo(4);
        assertThat(v.patch()).isEqualTo(4);
        assertThat(v.isProductized()).isTrue();
        assertThat(v.redhatBuild()).isEqualTo(1);
    }

    @Test
    public void testParseRedhatVersionWithFuseQualifier() {
        var v = CliVersion.parse("1.0.2.fuse-750001-redhat-00002");
        assertThat(v).isNotNull();
        assertThat(v.major()).isEqualTo(1);
        assertThat(v.minor()).isEqualTo(0);
        assertThat(v.patch()).isEqualTo(2);
        assertThat(v.isProductized()).isTrue();
        assertThat(v.redhatBuild()).isEqualTo(2);
    }

    @Test
    public void testParseNullAndBlank() {
        assertThat(CliVersion.parse(null)).isNull();
        assertThat(CliVersion.parse("")).isNull();
        assertThat(CliVersion.parse("   ")).isNull();
    }

    @Test
    public void testParseInvalidPreservesOriginal() {
        var v = CliVersion.parse("not-a-version");
        assertThat(v).isNotNull();
        assertThat(v.isParsed()).isFalse();
        assertThat(v.toString()).isEqualTo("not-a-version");
        assertThat(v.isNewerThan(CliVersion.parse("3.2.4"))).isFalse();

        var v2 = CliVersion.parse("abc.def.ghi");
        assertThat(v2).isNotNull();
        assertThat(v2.isParsed()).isFalse();
        assertThat(v2.toString()).isEqualTo("abc.def.ghi");
    }

    @Test
    public void testParseSnapshotVersion() {
        var v = CliVersion.parse("3.3.0-SNAPSHOT");
        assertThat(v).isNotNull();
        assertThat(v.major()).isEqualTo(3);
        assertThat(v.minor()).isEqualTo(3);
        assertThat(v.patch()).isEqualTo(0);
        assertThat(v.isProductized()).isFalse();
    }

    @Test
    public void testComparisonCommunity() {
        var v324 = CliVersion.parse("3.2.4");
        var v325 = CliVersion.parse("3.2.5");
        var v330 = CliVersion.parse("3.3.0");
        var v310 = CliVersion.parse("3.1.0");

        assertThat(v325.isNewerThan(v324)).isTrue();
        assertThat(v324.isNewerThan(v325)).isFalse();
        assertThat(v330.isNewerThan(v324)).isTrue();
        assertThat(v310.isNewerThan(v324)).isFalse();
    }

    @Test
    public void testComparisonRedhat() {
        var v1 = CliVersion.parse("3.1.6.redhat-00008");
        var v2 = CliVersion.parse("3.1.6.redhat-00011");

        assertThat(v2.isNewerThan(v1)).isTrue();
        assertThat(v1.isNewerThan(v2)).isFalse();
    }

    @Test
    public void testSameMajorMinor() {
        var v1 = CliVersion.parse("3.2.4");
        var v2 = CliVersion.parse("3.2.5");
        var v3 = CliVersion.parse("3.3.0");

        assertThat(v1.sameMajorMinor(v2)).isTrue();
        assertThat(v1.sameMajorMinor(v3)).isFalse();
    }

    @Test
    public void testProductizedFiltering() {
        var community = CliVersion.parse("3.2.4");
        var productized = CliVersion.parse("3.1.6.redhat-00011");

        assertThat(community.isProductized()).isFalse();
        assertThat(productized.isProductized()).isTrue();
    }

    @Test
    public void testSorting() {
        var versions = Stream.of(
                "3.2.4", "3.1.0", "3.3.0", "3.2.5", "3.0.1"
        ).map(CliVersion::parse).sorted().toList();

        assertThat(versions).extracting(CliVersion::toString)
                .containsExactly("3.0.1", "3.1.0", "3.2.4", "3.2.5", "3.3.0");
    }

    @Test
    public void testSortingRedhat() {
        var versions = Stream.of(
                "3.1.6.redhat-00011", "3.0.7.redhat-00009", "3.1.0.redhat-00004", "3.1.6.redhat-00008"
        ).map(CliVersion::parse).sorted().toList();

        assertThat(versions).extracting(CliVersion::toString)
                .containsExactly(
                        "3.0.7.redhat-00009",
                        "3.1.0.redhat-00004",
                        "3.1.6.redhat-00008",
                        "3.1.6.redhat-00011"
                );
    }

    @Test
    public void testEquality() {
        var v1 = CliVersion.parse("3.2.4");
        var v2 = CliVersion.parse("3.2.4");
        var v3 = CliVersion.parse("3.2.5");

        assertThat(v1).isEqualTo(v2);
        assertThat(v1).isNotEqualTo(v3);
        assertThat(v1.hashCode()).isEqualTo(v2.hashCode());
    }
}
