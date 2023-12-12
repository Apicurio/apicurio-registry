package io.apicurio.registry.noprofile.maven;

import io.apicurio.registry.maven.DownloadArtifact;
import io.apicurio.registry.maven.DownloadRegistryMojo;
import io.apicurio.registry.maven.RegisterArtifact;
import io.apicurio.registry.maven.RegisterArtifactReference;
import io.apicurio.registry.maven.RegisterRegistryMojo;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

@QuarkusTest
public class RegistryMojoWithReferencesTest extends RegistryMojoTestBase {

    RegisterRegistryMojo registerMojo;
    DownloadRegistryMojo downloadMojo;

    @BeforeEach
    public void createMojos() {
        this.registerMojo = new RegisterRegistryMojo();
        this.registerMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));

        this.downloadMojo = new DownloadRegistryMojo();
        this.downloadMojo.setRegistryUrl(TestUtils.getRegistryV3ApiUrl(testPort));
    }

    @Test
    public void testMojosWithReferences() throws IOException, MojoFailureException, MojoExecutionException {

        String groupId = "RegisterWithReferencesRegistryMojoTest";

        File exchangeFile = new File(getClass().getResource("Exchange.avsc").getFile());
        File tradeKeyFile = new File(getClass().getResource("TradeKey.avsc").getFile());
        File tradeRawFile = new File(getClass().getResource("TradeRaw.avsc").getFile());

        RegisterArtifact tradeRawArtifact = new RegisterArtifact();
        tradeRawArtifact.setGroupId(groupId);
        tradeRawArtifact.setArtifactId("tradeRaw");
        tradeRawArtifact.setType(ArtifactType.AVRO);
        tradeRawArtifact.setFile(tradeRawFile);

        RegisterArtifactReference tradeKeyArtifact = new RegisterArtifactReference();
        tradeKeyArtifact.setGroupId(groupId);
        tradeKeyArtifact.setArtifactId("tradeKey");
        tradeKeyArtifact.setType(ArtifactType.AVRO);
        tradeKeyArtifact.setFile(tradeKeyFile);
        tradeKeyArtifact.setName("tradeKey");

        RegisterArtifactReference exchangeArtifact = new RegisterArtifactReference();
        exchangeArtifact.setGroupId(groupId);
        exchangeArtifact.setArtifactId("exchange");
        exchangeArtifact.setType(ArtifactType.AVRO);
        exchangeArtifact.setFile(exchangeFile);
        exchangeArtifact.setName("exchange");

        tradeKeyArtifact.setReferences(Collections.singletonList(exchangeArtifact));
        tradeRawArtifact.setReferences(Collections.singletonList(tradeKeyArtifact));

        registerMojo.setArtifacts(Collections.singletonList(tradeRawArtifact));
        registerMojo.execute();

        DownloadArtifact tradeRawDownload = new DownloadArtifact();
        tradeRawDownload.setArtifactId("tradeRaw");
        tradeRawDownload.setGroupId(groupId);
        tradeRawDownload.setFile(new File(this.tempDirectory, "tradeRaw.avsc"));

        DownloadArtifact tradeKeyDownload = new DownloadArtifact();
        tradeKeyDownload.setArtifactId("tradeKey");
        tradeKeyDownload.setGroupId(groupId);
        tradeKeyDownload.setFile(new File(this.tempDirectory, "tradeKey.avsc"));

        DownloadArtifact exchangeDownload = new DownloadArtifact();
        exchangeDownload.setArtifactId("tradeKey");
        exchangeDownload.setGroupId(groupId);
        exchangeDownload.setFile(new File(this.tempDirectory, "exchange.avsc"));

        tradeKeyDownload.setArtifactReferences(Collections.singletonList(exchangeDownload));
        tradeRawDownload.setArtifactReferences(Collections.singletonList(tradeKeyDownload));

        downloadMojo.setArtifacts(Collections.singletonList(tradeRawDownload));
        downloadMojo.execute();
    }
}
