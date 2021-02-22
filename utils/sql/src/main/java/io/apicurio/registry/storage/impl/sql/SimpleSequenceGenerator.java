package io.apicurio.registry.storage.impl.sql;


import com.google.cloud.spanner.Key;
import com.google.cloud.spanner.Mutation;
import com.google.cloud.spanner.Struct;
import com.google.cloud.spanner.TransactionContext;
import java.util.Collections;
import java.util.NoSuchElementException;

/** Sequence generator that reads and increments the sequence in a transaction. */
public class SimpleSequenceGenerator {

    private final String sequenceName;

    public SimpleSequenceGenerator(String sequenceName) {
        this.sequenceName = sequenceName;
    }

    // [START getNext]
    /**
     * Returns the next value from this sequence.
     *
     * <p>Should only be called once per transaction.
     */
    long getNext(TransactionContext txn) {
        Struct result =
                txn.readRow(
                        "sequences_table", Key.of(sequenceName), Collections.singletonList("next_value"));
        if (result == null) {
            throw new NoSuchElementException(
                    "Sequence " + sequenceName + " not found in table " + "sequences_table");
        }
        long value = result.getLong(0);
        txn.buffer(
                Mutation.newUpdateBuilder("sequences_table")
                        .set("name")
                        .to(sequenceName)
                        .set("next_value")
                        .to(value + 1)
                        .build());
        return value;
    }
    // [END getNext]
}