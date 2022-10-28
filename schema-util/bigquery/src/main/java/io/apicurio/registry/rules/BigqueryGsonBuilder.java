package io.apicurio.registry.rules;

import com.google.cloud.bigquery.Field;
import com.google.cloud.bigquery.FieldList;
import com.google.cloud.bigquery.LegacySQLTypeName;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class BigqueryGsonBuilder {

    private final GsonBuilder gsonBuilder;

    public BigqueryGsonBuilder() {
        gsonBuilder = new GsonBuilder()
                .registerTypeAdapter(LegacySQLTypeName.class, new LegacySQLTypeNameAdapter())
                .registerTypeAdapter(FieldList.class, new FieldListAdapter())
                .setFieldNamingStrategy(field -> {
                    if (field.getName().equals("subFields")) {
                        return "fields";
                    }
                    return field.getName();
                });
    }

    protected FieldList parseFields(String content) {
        return FieldList.of(gsonBuilder.create().fromJson(content, Field[].class));
    }

    private static class LegacySQLTypeNameAdapter extends TypeAdapter<LegacySQLTypeName> {
        @Override
        public void write(JsonWriter jsonWriter, LegacySQLTypeName legacySQLTypeName) throws IOException {
            jsonWriter.value(legacySQLTypeName.name());
        }

        @Override
        public LegacySQLTypeName read(JsonReader jsonReader) throws IOException {
            return LegacySQLTypeName.valueOfStrict(jsonReader.nextString());
        }
    }

    private class FieldListAdapter extends TypeAdapter<FieldList> {

        @Override
        public void write(JsonWriter jsonWriter, FieldList fields) throws IOException {
            jsonWriter.value(gsonBuilder.create().toJson(fields));
        }

        @Override
        public FieldList read(JsonReader jsonReader) throws IOException {
            Gson gson = gsonBuilder.create();
            Field[] fields = gson.fromJson(jsonReader, Field[].class);
            return FieldList.of(fields);
        }
    }
}
