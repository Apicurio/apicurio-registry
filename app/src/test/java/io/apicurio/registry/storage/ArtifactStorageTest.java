package io.apicurio.registry.storage;

import io.apicurio.registry.storage.inmemory.InMemory;
import io.apicurio.registry.storage.model.ArtifactId;
import io.apicurio.registry.storage.model.ArtifactVersion;
import io.apicurio.registry.storage.model.MetaValue;
import io.apicurio.registry.storage.model.RuleInstance;
import io.apicurio.registry.storage.model.RuleType;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.HashMap;

@QuarkusTest
public class ArtifactStorageTest {

    @Inject
    @InMemory
    private ArtifactStorage artifactStorage;

    @Test
    public void createAndGetSimple() {
        ArtifactVersion value1 = artifactStorage.create().create(new ArtifactVersion("contentA"));
        ArtifactVersion value2 = artifactStorage.get(value1.getId().asArtifactId()).get(value1.getId());
        Assertions.assertEquals(value1, value2);
        Assertions.assertEquals(value1.getContent(), value2.getContent());
    }

    @Test
    public void createAndGetExplicit() {
        String artifactId = "foo";
        ArtifactVersion value1 = artifactStorage.create(artifactId).create(new ArtifactVersion("contentB"));
        ArtifactVersion value2 = artifactStorage.get(new ArtifactId(artifactId)).get(value1.getId());
        Assertions.assertEquals(value1, value2);
        Assertions.assertEquals(value1.getContent(), value2.getContent());
    }

    @Test
    public void createExplicitBad() {
        try {
            String artifactId = "1";
            ArtifactVersion value1 = artifactStorage.create(artifactId).create(new ArtifactVersion("contentC"));
            Assertions.fail();
        } catch (StorageException ex) {
            // ok
        }
    }

    @Test
    public void getAllKeys() {
        int size1 = artifactStorage.getAllKeys().size();
        ArtifactVersion value = artifactStorage.create().create(new ArtifactVersion("contentD"));
        int size2 = artifactStorage.getAllKeys().size();
        Assertions.assertEquals(size2, size1 + 1);
        Assertions.assertTrue(artifactStorage.getAllKeys().contains(value.getId().asArtifactId()));
    }

    @Test
    public void metaData() {
        ArtifactVersion value = artifactStorage.create().create(new ArtifactVersion("contentE"));
        MetaDataStorage meta = artifactStorage.getMetaDataStorage(value.getId().asArtifactId());
        Assertions.assertNotNull(meta);
        Assertions.assertTrue(meta.getAll().isEmpty());
        // ...
        meta.put("foo", new MetaValue("foo", "baz", true)); // TODO reduce duplication - value contains it's key
        meta = artifactStorage.getMetaDataStorage(value.getId().asArtifactId());
        Assertions.assertNotNull(meta);
        Assertions.assertEquals(1, meta.getAll().size());
        // ...
        meta.delete("foo");
        meta = artifactStorage.getMetaDataStorage(value.getId().asArtifactId());
        Assertions.assertNotNull(meta);
        Assertions.assertTrue(meta.getAll().isEmpty());
    }

    @Test
    public void metaDataBad() {
        try {
            ArtifactVersion value = artifactStorage.create().create(new ArtifactVersion("contentF"));
            artifactStorage.getMetaDataStorage(value.getId().asArtifactId())
                    .put("cat", new MetaValue("dog", "value", true));
            Assertions.fail();
        } catch (StorageException ex) {
            // ok
        }
    }

    @Test
    public void rules() {
        ArtifactVersion value = artifactStorage.create().create(new ArtifactVersion("contentF"));
        RuleInstanceStorage rules = artifactStorage.getRuleInstanceStorage(value.getId().asArtifactId());
        Assertions.assertNotNull(rules);
        Assertions.assertTrue(rules.getAll().isEmpty());
        // ...
        rules.put(RuleType.NOOP, new RuleInstance(RuleType.NOOP, new HashMap<>(), true)); // TODO reduce duplication - value contains it's key
        rules = artifactStorage.getRuleInstanceStorage(value.getId().asArtifactId());
        Assertions.assertNotNull(rules);
        Assertions.assertEquals(1, rules.getAll().size());
        // ...
        rules.delete(RuleType.NOOP);
        rules = artifactStorage.getRuleInstanceStorage(value.getId().asArtifactId());
        Assertions.assertNotNull(rules);
        Assertions.assertTrue(rules.getAll().isEmpty());
    }

    @Test
    public void ruleBad() {
        try {
            ArtifactVersion value = artifactStorage.create().create(new ArtifactVersion("contentF"));
            artifactStorage.getRuleInstanceStorage(value.getId().asArtifactId())
                    .put(RuleType.NOOP, new RuleInstance(RuleType.VALIDATION, new HashMap<>(), true));
            Assertions.fail();
        } catch (StorageException ex) {
            // ok
        }
    }
}
