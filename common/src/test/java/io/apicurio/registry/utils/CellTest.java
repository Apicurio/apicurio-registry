package io.apicurio.registry.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.utils.Cell.cell;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class CellTest {

    @Test
    void testCellSerDes() throws JsonProcessingException {
        var mapper = new ObjectMapper();

        var wrapper1 = new Wrapper1(cell("hello"));
        var json = "{\"foo\": \"hello\"}";
        assertEquals(mapper.readTree(json), mapper.valueToTree(wrapper1));
        assertEquals(wrapper1, mapper.readValue(json, Wrapper1.class));

        var wrapper2 = new Wrapper2(cell(true));
        json = "{\"bar\": true}";
        assertEquals(mapper.readTree(json), mapper.valueToTree(wrapper2));
        assertEquals(wrapper2, mapper.readValue(json, Wrapper2.class));

        var wrapper3 = new Wrapper3<>(cell(42));
        json = "{\"baz\": 42}";
        assertEquals(mapper.readTree(json), mapper.valueToTree(wrapper3));
        assertEquals(wrapper3, mapper.readValue(json, Wrapper3.class));
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Wrapper1 {
        Cell<String> foo;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Wrapper2 {
        Cell<Boolean> bar;
    }

    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class Wrapper3<T> {
        Cell<T> baz;
    }
}
