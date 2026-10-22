package io.apicurio.registry.storage.impl.search;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.extract.StructuredElement;
import io.apicurio.registry.iceberg.content.IcebergStructuredContentExtractor;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IcebergStructuredContentExtractorTest {

    private final IcebergStructuredContentExtractor tableExtractor = new IcebergStructuredContentExtractor(true);
    private final IcebergStructuredContentExtractor viewExtractor = new IcebergStructuredContentExtractor(false);

    @Test
    void testExtractTableName() {
        String metadata = """
                {
                    "format-version": 2,
                    "table-uuid": "abc-123-def",
                    "location": "s3://bucket/table",
                    "schemas": []
                }
                """;

        List<StructuredElement> elements = tableExtractor.extract(ContentHandle.create(metadata));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("abc-123-def")));
    }

    @Test
    void testExtractTableColumns() {
        String metadata = """
                {
                    "format-version": 2,
                    "table-uuid": "abc-123",
                    "location": "s3://bucket/table",
                    "current-schema-id": 0,
                    "schemas": [
                        {
                            "type": "struct",
                            "schema-id": 0,
                            "fields": [
                                { "id": 1, "name": "id", "required": true, "type": "long" },
                                { "id": 2, "name": "name", "required": true, "type": "string" },
                                { "id": 3, "name": "email", "required": false, "type": "string" }
                            ]
                        }
                    ]
                }
                """;

        List<StructuredElement> elements = tableExtractor.extract(ContentHandle.create(metadata));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("id")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("name")));
        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("email")));
    }

    @Test
    void testExtractTableColumnsFromCurrentSchema() {
        String metadata = """
                {
                    "format-version": 2,
                    "table-uuid": "abc-123",
                    "location": "s3://bucket/table",
                    "current-schema-id": 1,
                    "schemas": [
                        {
                            "type": "struct",
                            "schema-id": 0,
                            "fields": [
                                { "id": 1, "name": "old_field", "required": true, "type": "string" }
                            ]
                        },
                        {
                            "type": "struct",
                            "schema-id": 1,
                            "fields": [
                                { "id": 1, "name": "new_field", "required": true, "type": "string" },
                                { "id": 2, "name": "added_field", "required": false, "type": "int" }
                            ]
                        }
                    ]
                }
                """;

        List<StructuredElement> elements = tableExtractor.extract(ContentHandle.create(metadata));

        // Should extract from schema-id=1 (current), not schema-id=0
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("new_field")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("column") && e.name().equals("added_field")));
        assertTrue(elements.stream()
                .noneMatch(e -> e.kind().equals("column") && e.name().equals("old_field")));
    }

    @Test
    void testExtractPartitionFields() {
        String metadata = """
                {
                    "format-version": 2,
                    "table-uuid": "abc-123",
                    "location": "s3://bucket/table",
                    "current-schema-id": 0,
                    "schemas": [
                        {
                            "type": "struct",
                            "schema-id": 0,
                            "fields": [
                                { "id": 1, "name": "date", "required": true, "type": "date" },
                                { "id": 2, "name": "region", "required": true, "type": "string" }
                            ]
                        }
                    ],
                    "default-spec-id": 0,
                    "partition-specs": [
                        {
                            "spec-id": 0,
                            "fields": [
                                { "field-id": 1000, "source-id": 1, "name": "date_month", "transform": "month" },
                                { "field-id": 1001, "source-id": 2, "name": "region_identity", "transform": "identity" }
                            ]
                        }
                    ]
                }
                """;

        List<StructuredElement> elements = tableExtractor.extract(ContentHandle.create(metadata));

        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("partition") && e.name().equals("date_month")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("partition") && e.name().equals("region_identity")));
    }

    @Test
    void testExtractSortFields() {
        String metadata = """
                {
                    "format-version": 2,
                    "table-uuid": "abc-123",
                    "location": "s3://bucket/table",
                    "current-schema-id": 0,
                    "schemas": [
                        {
                            "type": "struct",
                            "schema-id": 0,
                            "fields": [
                                { "id": 1, "name": "ts", "required": true, "type": "timestamp" }
                            ]
                        }
                    ],
                    "default-sort-order-id": 1,
                    "sort-orders": [
                        {
                            "order-id": 1,
                            "fields": [
                                { "source-id": 1, "transform": "identity", "direction": "asc", "null-order": "nulls-last" }
                            ]
                        }
                    ]
                }
                """;

        List<StructuredElement> elements = tableExtractor.extract(ContentHandle.create(metadata));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("sort") && e.name().equals("identity")));
    }

    @Test
    void testExtractViewName() {
        String metadata = """
                {
                    "format-version": 1,
                    "view-uuid": "view-xyz-789",
                    "location": "s3://bucket/view",
                    "schemas": [],
                    "versions": [],
                    "current-version-id": 1
                }
                """;

        List<StructuredElement> elements = viewExtractor.extract(ContentHandle.create(metadata));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("view-xyz-789")));
    }

    @Test
    void testExtractViewColumns() {
        String metadata = """
                {
                    "format-version": 1,
                    "view-uuid": "view-123",
                    "location": "s3://bucket/view",
                    "current-schema-id": 0,
                    "schemas": [
                        {
                            "type": "struct",
                            "schema-id": 0,
                            "fields": [
                                { "id": 1, "name": "user_id", "required": true, "type": "long" },
                                { "id": 2, "name": "total_orders", "required": true, "type": "long" }
                            ]
                        }
                    ],
                    "versions": [],
                    "current-version-id": 1
                }
                """;

        List<StructuredElement> elements = viewExtractor.extract(ContentHandle.create(metadata));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("user_id")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("column") && e.name().equals("total_orders")));
    }

    @Test
    void testExtractViewSqlRepresentations() {
        String metadata = """
                {
                    "format-version": 1,
                    "view-uuid": "view-123",
                    "location": "s3://bucket/view",
                    "schemas": [],
                    "current-version-id": 1,
                    "versions": [
                        {
                            "version-id": 1,
                            "representations": [
                                {
                                    "type": "sql",
                                    "sql": "SELECT user_id, COUNT(*) AS total_orders FROM orders GROUP BY user_id",
                                    "dialect": "spark"
                                }
                            ]
                        }
                    ]
                }
                """;

        List<StructuredElement> elements = viewExtractor.extract(ContentHandle.create(metadata));

        assertTrue(elements.stream().anyMatch(e -> e.kind().equals("sql")
                && e.name().equals("SELECT user_id, COUNT(*) AS total_orders FROM orders GROUP BY user_id")));
    }

    @Test
    void testComprehensiveTableMetadata() {
        String metadata = """
                {
                    "format-version": 2,
                    "table-uuid": "full-table-uuid",
                    "location": "s3://warehouse/db/events",
                    "current-schema-id": 0,
                    "schemas": [
                        {
                            "type": "struct",
                            "schema-id": 0,
                            "fields": [
                                { "id": 1, "name": "event_id", "required": true, "type": "long" },
                                { "id": 2, "name": "event_ts", "required": true, "type": "timestamp" },
                                { "id": 3, "name": "user_id", "required": true, "type": "long" },
                                { "id": 4, "name": "event_type", "required": true, "type": "string" }
                            ]
                        }
                    ],
                    "default-spec-id": 0,
                    "partition-specs": [
                        {
                            "spec-id": 0,
                            "fields": [
                                { "field-id": 1000, "source-id": 2, "name": "event_day", "transform": "day" }
                            ]
                        }
                    ],
                    "default-sort-order-id": 1,
                    "sort-orders": [
                        {
                            "order-id": 1,
                            "fields": [
                                { "source-id": 2, "transform": "identity", "direction": "desc", "null-order": "nulls-last" }
                            ]
                        }
                    ]
                }
                """;

        List<StructuredElement> elements = tableExtractor.extract(ContentHandle.create(metadata));

        // Name
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("name") && e.name().equals("full-table-uuid")));

        // Columns
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("event_id")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("event_ts")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("user_id")));
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("column") && e.name().equals("event_type")));

        // Partition fields
        assertTrue(elements.stream()
                .anyMatch(e -> e.kind().equals("partition") && e.name().equals("event_day")));

        // Sort fields
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("sort") && e.name().equals("identity")));

        // No view-specific elements
        assertTrue(elements.stream().noneMatch(e -> e.kind().equals("sql")));
    }

    @Test
    void testExtractFromInvalidContent() {
        List<StructuredElement> tableElements = tableExtractor
                .extract(ContentHandle.create("not valid json"));
        List<StructuredElement> viewElements = viewExtractor
                .extract(ContentHandle.create("not valid json"));

        assertTrue(tableElements.isEmpty());
        assertTrue(viewElements.isEmpty());
    }

    @Test
    void testExtractTableWithFormatV1Schema() {
        String metadata = """
                {
                    "format-version": 1,
                    "table-uuid": "v1-table",
                    "location": "s3://bucket/table",
                    "schema": {
                        "type": "struct",
                        "fields": [
                            { "id": 1, "name": "col_a", "required": true, "type": "string" },
                            { "id": 2, "name": "col_b", "required": false, "type": "int" }
                        ]
                    }
                }
                """;

        List<StructuredElement> elements = tableExtractor.extract(ContentHandle.create(metadata));

        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("name") && e.name().equals("v1-table")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("col_a")));
        assertTrue(
                elements.stream().anyMatch(e -> e.kind().equals("column") && e.name().equals("col_b")));
    }
}
