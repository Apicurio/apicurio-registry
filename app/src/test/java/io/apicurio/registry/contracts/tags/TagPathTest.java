package io.apicurio.registry.contracts.tags;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TagPathTest {

    @Test
    public void testExactMatch() {
        TagPath path = TagPath.parse("field");
        Assertions.assertTrue(path.matches("field"));
        Assertions.assertFalse(path.matches("other"));
    }

    @Test
    public void testNestedExactMatch() {
        TagPath path = TagPath.parse("record.field");
        Assertions.assertTrue(path.matches("record.field"));
        Assertions.assertFalse(path.matches("record.other"));
    }

    @Test
    public void testSingleLevelWildcard() {
        TagPath path = TagPath.parse("record.*");
        Assertions.assertTrue(path.matches("record.field"));
        Assertions.assertFalse(path.matches("record.nested.field"));
        Assertions.assertFalse(path.matches("record"));
    }

    @Test
    public void testAnyDepthWildcard() {
        TagPath path = TagPath.parse("record.**");
        Assertions.assertTrue(path.matches("record"));
        Assertions.assertTrue(path.matches("record.field"));
        Assertions.assertTrue(path.matches("record.nested.field"));

        TagPath suffix = TagPath.parse("**.id");
        Assertions.assertTrue(suffix.matches("id"));
        Assertions.assertTrue(suffix.matches("record.user.id"));
    }

    @Test
    public void testMixedWildcards() {
        TagPath path = TagPath.parse("record.*.id");
        Assertions.assertTrue(path.matches("record.user.id"));
        Assertions.assertFalse(path.matches("record.user.profile.id"));
    }

    @Test
    public void testParseValidation() {
        TagPath parsed = TagPath.parse(" field ");
        Assertions.assertEquals("field", parsed.path());
        Assertions.assertThrows(IllegalArgumentException.class, () -> TagPath.parse("   "));
        Assertions.assertThrows(IllegalArgumentException.class, () -> TagPath.parse(null));
    }
}
