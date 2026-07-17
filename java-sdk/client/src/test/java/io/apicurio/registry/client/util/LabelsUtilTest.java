package io.apicurio.registry.client.util;

import io.apicurio.registry.rest.client.models.Labels;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LabelsUtilTest {

    @Test
    public void testToMap_null() {
        assertEquals(Map.of(), LabelsUtil.toMap(null));
    }

    @Test
    public void testToMap_noAdditionalData() {
        Labels labels = new Labels();
        assertEquals(Map.of(), LabelsUtil.toMap(labels));
    }

    @Test
    public void testToMap_withData() {
        Labels labels = new Labels();
        Map<String, Object> additionalData = new LinkedHashMap<>();
        additionalData.put("env", "prod");
        additionalData.put("team", "platform");
        labels.setAdditionalData(additionalData);

        Map<String, String> result = LabelsUtil.toMap(labels);

        assertEquals(2, result.size());
        assertEquals("prod", result.get("env"));
        assertEquals("platform", result.get("team"));
    }

    @Test
    public void testToDisplayString_labels_null() {
        assertEquals("", LabelsUtil.toDisplayString((Labels) null));
    }

    @Test
    public void testToDisplayString_labels_withData() {
        Labels labels = new Labels();
        Map<String, Object> additionalData = new LinkedHashMap<>();
        additionalData.put("env", "prod");
        additionalData.put("team", "platform");
        labels.setAdditionalData(additionalData);

        String result = LabelsUtil.toDisplayString(labels);

        assertEquals("env=prod,team=platform", result);
    }

    @Test
    public void testToDisplayString_map_withData() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        String result = LabelsUtil.toDisplayString(map);

        assertEquals("key1=value1,key2=value2", result);
    }

    @Test
    public void testToDisplayString_map_empty() {
        assertTrue(LabelsUtil.toDisplayString(Map.of()).isEmpty());
    }

    @Test
    public void testToDisplayString_map_null() {
        assertEquals("", LabelsUtil.toDisplayString((Map<String, ?>) null));
    }

    @Test
    public void testParseLabels_simple() {
        Map<String, String> result = LabelsUtil.parseLabels(List.of("env=prod", "team=platform"));

        assertEquals(2, result.size());
        assertEquals("prod", result.get("env"));
        assertEquals("platform", result.get("team"));
    }

    @Test
    public void testParseLabels_bareKeyNoValue() {
        Map<String, String> result = LabelsUtil.parseLabels(List.of("standalone"));

        assertEquals(1, result.size());
        assertEquals("", result.get("standalone"));
    }

    @Test
    public void testParseLabels_preservesInsertionOrder() {
        Map<String, String> result = LabelsUtil.parseLabels(List.of("b=2", "a=1"));

        assertEquals(List.of("b", "a"), List.copyOf(result.keySet()));
    }

    @Test
    public void testSplitLabel_escapedEquals() {
        Map.Entry<String, String> entry = LabelsUtil.splitLabel("weird\\=key=value");

        assertEquals("weird=key", entry.getKey());
        assertEquals("value", entry.getValue());
    }

    @Test
    public void testSplitLabel_escapedBackslash() {
        Map.Entry<String, String> entry = LabelsUtil.splitLabel("path\\\\to=value");

        assertEquals("path\\to", entry.getKey());
        assertEquals("value", entry.getValue());
    }

    @Test
    public void testSplitLabel_trailingBackslash() {
        Map.Entry<String, String> entry = LabelsUtil.splitLabel("trailing\\");

        assertEquals("trailing\\", entry.getKey());
        assertEquals("", entry.getValue());
    }
}
