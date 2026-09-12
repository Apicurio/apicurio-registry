package io.apicurio.registry.promotion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContentLineDiffTest {

    @Test
    public void testIdentical() {
        assertTrue(ContentLineDiff.diff("a\nb", "a\nb").isEmpty());
    }

    @Test
    public void testAddAndRemove() {
        List<ContentLineDiff.Line> diff = ContentLineDiff.diff("keep\nold", "keep\nnew");
        assertEquals(2, diff.size());
        assertEquals("removed", diff.get(0).op());
        assertEquals("old", diff.get(0).text());
        assertEquals("added", diff.get(1).op());
        assertEquals("new", diff.get(1).text());
    }

    @Test
    public void testFromEmpty() {
        List<ContentLineDiff.Line> diff = ContentLineDiff.diff("", "only");
        assertEquals(1, diff.size());
        assertEquals("added", diff.get(0).op());
        assertEquals("only", diff.get(0).text());
    }
}
