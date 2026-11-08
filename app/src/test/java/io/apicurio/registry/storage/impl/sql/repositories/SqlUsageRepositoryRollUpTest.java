package io.apicurio.registry.storage.impl.sql.repositories;

import io.apicurio.registry.storage.dto.SchemaUsageEventDto;
import io.apicurio.registry.storage.impl.sql.repositories.SqlUsageRepository.DailyUsage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

/**
 * The batch flush folds events of the same day key in Java before the upsert, so these check the fold on its
 * own: counts, the widened first and last timestamps, the UTC day boundary and the write order.
 */
class SqlUsageRepositoryRollUpTest {

    private static final long DAY_MS = SqlUsageRepository.DAY_MS;
    private static final long DAY = 20_000;
    private static final long DAY_START = DAY * DAY_MS;

    @Test
    void eventsOfOneKeyAndDayFoldIntoOneRow() {
        List<DailyUsage> rows = SqlUsageRepository.rollUp(List.of(
                event(1, "app", "SERIALIZE", DAY_START + 5_000),
                event(1, "app", "SERIALIZE", DAY_START + 1_000),
                event(1, "app", "SERIALIZE", DAY_START + 9_000)));

        Assertions.assertEquals(List.of("1|0|app|SERIALIZE|20000|3|" + (DAY_START + 1_000) + "|"
                + (DAY_START + 9_000)), rows.stream().map(SqlUsageRepositoryRollUpTest::describe).toList());
    }

    @Test
    void theLastMillisecondOfADayAndTheFirstOfTheNextGiveTwoRows() {
        List<DailyUsage> rows = SqlUsageRepository.rollUp(List.of(
                event(1, "app", "SERIALIZE", DAY_START + DAY_MS),
                event(1, "app", "SERIALIZE", DAY_START + DAY_MS - 1)));

        Assertions.assertEquals(List.of(
                "1|0|app|SERIALIZE|20000|1|" + (DAY_START + DAY_MS - 1) + "|" + (DAY_START + DAY_MS - 1),
                "1|0|app|SERIALIZE|20001|1|" + (DAY_START + DAY_MS) + "|" + (DAY_START + DAY_MS)),
                rows.stream().map(SqlUsageRepositoryRollUpTest::describe).toList());
    }

    @Test
    void everyKeyColumnSeparatesRowsAndRowsComeOutInKeyOrder() {
        List<DailyUsage> rows = SqlUsageRepository.rollUp(List.of(
                event(2, "app", "SERIALIZE", DAY_START),
                event(1, "web", "SERIALIZE", DAY_START),
                event(1, "app", "DESERIALIZE", DAY_START),
                event(1, "app", "SERIALIZE", DAY_START),
                SchemaUsageEventDto.builder().globalId(1).contentId(4).clientId("app").operation("SERIALIZE")
                        .eventTimestamp(DAY_START).build()));

        Assertions.assertEquals(List.of(
                "1|0|app|DESERIALIZE|20000|1|" + DAY_START + "|" + DAY_START,
                "1|0|app|SERIALIZE|20000|1|" + DAY_START + "|" + DAY_START,
                "1|0|web|SERIALIZE|20000|1|" + DAY_START + "|" + DAY_START,
                "1|4|app|SERIALIZE|20000|1|" + DAY_START + "|" + DAY_START,
                "2|0|app|SERIALIZE|20000|1|" + DAY_START + "|" + DAY_START),
                rows.stream().map(SqlUsageRepositoryRollUpTest::describe).toList());
    }

    /**
     * MySQL and SQL Server compare client ids case-insensitively, so "App" and "app" are one row there. They
     * must sort next to each other, before "Zed", in every batch, or two batches holding "Zed" and either
     * spelling would lock that row and "Zed" in opposite orders.
     */
    @Test
    void clientIdsSortCaseInsensitivelyWithCaseAsTheTiebreak() {
        List<DailyUsage> rows = SqlUsageRepository.rollUp(List.of(
                event(1, "Zed", "SERIALIZE", DAY_START),
                event(1, "app", "SERIALIZE", DAY_START),
                event(1, "App", "SERIALIZE", DAY_START),
                event(1, "beta", "SERIALIZE", DAY_START)));

        Assertions.assertEquals(List.of("App", "app", "beta", "Zed"),
                rows.stream().map(row -> row.key().clientId()).toList());
    }

    /**
     * On a case-insensitive collation (App, SERIALIZE) and (app, SERIALIZE) are one row, and so are
     * (app, DESERIALIZE) and (App, DESERIALIZE). Batch A and batch B touch the same two rows, so both must
     * reach them in the same order, whichever spelling each batch uses; a case-sensitive client id
     * comparison ahead of the operation would put them in opposite orders.
     */
    @Test
    void batchesSpellingTheSameRowsDifferentlyReachThemInTheSameOrder() {
        List<String> batchA = foldedOrder(List.of(event(1, "App", "SERIALIZE", DAY_START),
                event(1, "app", "DESERIALIZE", DAY_START)));
        List<String> batchB = foldedOrder(List.of(event(1, "app", "SERIALIZE", DAY_START),
                event(1, "App", "DESERIALIZE", DAY_START)));

        Assertions.assertEquals(List.of("app|deserialize", "app|serialize"), batchA);
        Assertions.assertEquals(batchA, batchB);
    }

    /**
     * MySQL and SQL Server also ignore trailing spaces, so "app " is the same row as "app" and "App". Batch C
     * spells the client with a trailing space and batch D spells the operation with one; both must reach the
     * two shared rows in the same order as batches A and B.
     */
    @Test
    void trailingSpacesAreFoldedLikeCaseWhenOrderingABatch() {
        List<String> batchC = foldedOrder(List.of(event(1, "app ", "SERIALIZE", DAY_START),
                event(1, "App", "DESERIALIZE", DAY_START)));
        List<String> batchD = foldedOrder(List.of(event(1, "app", "SERIALIZE  ", DAY_START),
                event(1, "app ", "deserialize", DAY_START)));

        Assertions.assertEquals(List.of("app|deserialize", "app|serialize"), batchC);
        Assertions.assertEquals(batchC, batchD);
    }

    @Test
    void anEmptyBatchHasNoRows() {
        Assertions.assertEquals(0, SqlUsageRepository.rollUp(List.of()).size());
    }

    private static SchemaUsageEventDto event(long globalId, String clientId, String operation, long timestamp) {
        return SchemaUsageEventDto.builder().globalId(globalId).contentId(0).clientId(clientId)
                .operation(operation).eventTimestamp(timestamp).build();
    }

    /** The rows of a batch in write order, as a case-insensitive, space-padding database sees their keys. */
    private static List<String> foldedOrder(List<SchemaUsageEventDto> events) {
        return SqlUsageRepository.rollUp(events).stream().map(row -> (withoutTrailingSpaces(row.key().clientId())
                + "|" + withoutTrailingSpaces(row.key().operation())).toLowerCase(Locale.ROOT)).toList();
    }

    /** Strips trailing U+0020 only, as space padding does; other whitespace stays significant. */
    private static String withoutTrailingSpaces(String value) {
        return value.replaceAll(" +$", "");
    }

    private static String describe(DailyUsage row) {
        return row.key().globalId() + "|" + row.key().contentId() + "|" + row.key().clientId() + "|"
                + row.key().operation() + "|" + row.key().usageDay() + "|" + row.fetchCount() + "|"
                + row.firstEventTimestamp() + "|" + row.lastEventTimestamp();
    }
}
