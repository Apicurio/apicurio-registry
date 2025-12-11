package io.apicurio.registry.benchmark;

import com.google.protobuf.Descriptors;

import java.util.Optional;

/**
 * Benchmark comparing:
 * - OLD: Apicurio protobuf-schema-utilities v3.1.2 (Wire-based)
 * - NEW: Apicurio protobuf-schema-utilities current (protobuf4j-based)
 *
 * Both produce FileDescriptor from schema string - equivalent operations.
 *
 * Run with: {@code mvn package exec:java}
 */
public class ProtobufParsingBenchmark {

    private static final int WARMUP_ITERATIONS = 50;
    private static final int MEASURED_ITERATIONS = 500;

    private boolean oldAvailable = false;
    private String oldFailureReason = null;

    private static final String SIMPLE_SCHEMA = """
            syntax = "proto3";
            package example;

            message UUID {
                int64 msb = 1;
                int64 lsb = 2;
            }
            """;

    private static final String COMPLEX_SCHEMA = """
            syntax = "proto3";
            package example;

            message Order {
                string order_id = 1;
                string customer_id = 2;
                OrderStatus status = 3;
                repeated OrderItem items = 4;
                ShippingInfo shipping = 5;
                PaymentInfo payment = 6;

                message OrderItem {
                    string product_id = 1;
                    string product_name = 2;
                    int32 quantity = 3;
                    Money unit_price = 4;
                    Money total_price = 5;
                    repeated string tags = 6;
                }
            }

            message Money {
                string currency_code = 1;
                int64 units = 2;
                int32 nanos = 3;
            }

            message ShippingInfo {
                string address_line1 = 1;
                string address_line2 = 2;
                string city = 3;
                string state = 4;
                string postal_code = 5;
                string country = 6;
                ShippingMethod method = 7;
            }

            message PaymentInfo {
                string payment_method_id = 1;
                PaymentType type = 2;
                Money amount = 3;
                string transaction_id = 4;
            }

            enum OrderStatus {
                ORDER_STATUS_UNSPECIFIED = 0;
                PENDING = 1;
                CONFIRMED = 2;
                PROCESSING = 3;
                SHIPPED = 4;
                DELIVERED = 5;
                CANCELLED = 6;
            }

            enum ShippingMethod {
                SHIPPING_METHOD_UNSPECIFIED = 0;
                STANDARD = 1;
                EXPRESS = 2;
                OVERNIGHT = 3;
            }

            enum PaymentType {
                PAYMENT_TYPE_UNSPECIFIED = 0;
                CREDIT_CARD = 1;
                DEBIT_CARD = 2;
                PAYPAL = 3;
                BANK_TRANSFER = 4;
            }
            """;

    public static void main(String[] args) {
        System.out.println("=".repeat(80));
        System.out.println("APICURIO PROTOBUF SCHEMA UTILITIES BENCHMARK");
        System.out.println("Old (Wire-based v3.1.2) vs New (protobuf4j-based)");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("This is an APPLES-TO-APPLES comparison:");
        System.out.println();
        System.out.println("  OLD (Wire):       FileDescriptorUtils.protoFileToFileDescriptor() v3.1.2");
        System.out.println("  NEW (protobuf4j): FileDescriptorUtils.protoFileToFileDescriptor() current");
        System.out.println();
        System.out.println("Both produce FileDescriptor from schema string - equivalent operations.");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  - Warmup iterations:  " + WARMUP_ITERATIONS);
        System.out.println("  - Measured iterations: " + MEASURED_ITERATIONS);
        System.out.println();

        ProtobufParsingBenchmark benchmark = new ProtobufParsingBenchmark();

        System.out.println("Checking availability of implementations...");
        benchmark.checkAvailability();
        System.out.println();

        System.out.println("Warming up...");
        benchmark.warmup();
        System.out.println("Warmup complete.\n");

        System.out.println("=".repeat(80));
        benchmark.runBenchmark("Simple (UUID)", SIMPLE_SCHEMA);

        System.out.println("=".repeat(80));
        benchmark.runBenchmark("Complex (Order system)", COMPLEX_SCHEMA);

        System.out.println("=".repeat(80));
        System.out.println("\nBenchmark complete!");
    }

    private void checkAvailability() {
        // Check if old library is available
        try {
            parseWithOld("check.proto", SIMPLE_SCHEMA);
            oldAvailable = true;
            System.out.println("  OLD (Wire v3.1.2): Available");
        } catch (Throwable e) {
            oldAvailable = false;
            // Get root cause
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            oldFailureReason = root.getClass().getSimpleName() + ": " + root.getMessage();
            System.out.println("  OLD (Wire v3.1.2): NOT AVAILABLE - " + oldFailureReason);
        }

        // Check if new library is available
        try {
            parseWithNew("check.proto", SIMPLE_SCHEMA);
            System.out.println("  NEW (protobuf4j):  Available");
        } catch (Throwable e) {
            System.out.println("  NEW (protobuf4j):  NOT AVAILABLE - " + e.getMessage());
            throw new RuntimeException("New implementation must be available", e);
        }
    }

    private void warmup() {
        if (oldAvailable) {
            for (int i = 0; i < 5; i++) {
                try {
                    parseWithOld("warmup.proto", SIMPLE_SCHEMA);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        for (int i = 0; i < 5; i++) {
            try {
                parseWithNew("warmup.proto", SIMPLE_SCHEMA);
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void runBenchmark(String schemaName, String schemaContent) {
        System.out.println("\nBenchmarking: " + schemaName);
        System.out.println("-".repeat(60));

        String fileName = schemaName.toLowerCase().replaceAll("[^a-z0-9]", "_") + ".proto";

        long oldTotalNanos = 0;

        // ========== OLD (Wire-based) ==========
        if (oldAvailable) {
            System.out.print("  OLD (Wire):       Warming up... ");
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                try {
                    parseWithOld(fileName, schemaContent);
                } catch (Exception e) {
                    // ignore
                }
            }
            System.out.println("done");

            System.out.print("  OLD (Wire):       Measuring " + MEASURED_ITERATIONS + " iterations... ");
            long oldStartTime = System.nanoTime();
            for (int i = 0; i < MEASURED_ITERATIONS; i++) {
                try {
                    parseWithOld(fileName, schemaContent);
                } catch (Exception e) {
                    // ignore
                }
            }
            long oldEndTime = System.nanoTime();
            oldTotalNanos = oldEndTime - oldStartTime;
            System.out.println("done");
        } else {
            System.out.println("  OLD (Wire):       SKIPPED - " + oldFailureReason);
        }

        // ========== NEW (protobuf4j-based) ==========
        System.out.print("  NEW (protobuf4j): Warming up... ");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                parseWithNew(fileName, schemaContent);
            } catch (Exception e) {
                // ignore
            }
        }
        System.out.println("done");

        System.out.print("  NEW (protobuf4j): Measuring " + MEASURED_ITERATIONS + " iterations... ");
        long newStartTime = System.nanoTime();
        for (int i = 0; i < MEASURED_ITERATIONS; i++) {
            try {
                parseWithNew(fileName, schemaContent);
            } catch (Exception e) {
                // ignore
            }
        }
        long newEndTime = System.nanoTime();
        long newTotalNanos = newEndTime - newStartTime;
        System.out.println("done");

        printResults(schemaName, oldTotalNanos, newTotalNanos);
    }

    /**
     * Parse schema using OLD Apicurio protobuf-schema-utilities (Wire-based, v3.1.2).
     * Uses the shaded classes relocated to io.apicurio.registry.old.
     */
    private Descriptors.FileDescriptor parseWithOld(String fileName, String schemaContent) throws Exception {
        return io.apicurio.registry.old.utils.protobuf.schema.FileDescriptorUtils
                .protoFileToFileDescriptor(schemaContent, fileName, Optional.empty());
    }

    /**
     * Parse schema using NEW Apicurio protobuf-schema-utilities (protobuf4j-based).
     */
    private Descriptors.FileDescriptor parseWithNew(String fileName, String schemaContent) throws Exception {
        return io.apicurio.registry.utils.protobuf.schema.FileDescriptorUtils
                .protoFileToFileDescriptor(schemaContent, fileName, Optional.empty());
    }

    private void printResults(String schemaName, long oldTotalNanos, long newTotalNanos) {
        System.out.println();
        System.out.println("  RESULTS for " + schemaName + ":");
        System.out.println("  " + "-".repeat(70));

        double newTotalMs = newTotalNanos / 1_000_000.0;
        double newAvgMs = newTotalMs / MEASURED_ITERATIONS;
        double newOpsPerSec = MEASURED_ITERATIONS / (newTotalMs / 1000.0);

        System.out.println(String.format("  | %-18s | %-15s | %-18s |", "Implementation", "Per Operation", MEASURED_ITERATIONS + " Operations"));
        System.out.println("  |" + "-".repeat(20) + "|" + "-".repeat(17) + "|" + "-".repeat(20) + "|");

        if (oldAvailable && oldTotalNanos > 0) {
            double oldTotalMs = oldTotalNanos / 1_000_000.0;
            double oldAvgMs = oldTotalMs / MEASURED_ITERATIONS;
            double oldOpsPerSec = MEASURED_ITERATIONS / (oldTotalMs / 1000.0);

            System.out.println(String.format("  | %-18s | %-15s | %-18s |", "OLD (Wire v3.1.2)", String.format("%.3f ms", oldAvgMs), String.format("%.0f ms", oldTotalMs)));
            System.out.println(String.format("  | %-18s | %-15s | %-18s |", "NEW (protobuf4j)", String.format("%.3f ms", newAvgMs), String.format("%.0f ms", newTotalMs)));

            System.out.println();

            double ratio = oldTotalNanos / (double) newTotalNanos;
            if (ratio > 1.0) {
                System.out.println(String.format("  NEW (protobuf4j) is %.1fx FASTER than OLD (Wire)", ratio));
            } else {
                System.out.println(String.format("  OLD (Wire) is %.1fx faster than NEW (protobuf4j)", 1.0 / ratio));
            }

            System.out.println();
            System.out.println("  Operations per second:");
            System.out.println(String.format("    - OLD (Wire):       ~%.0f ops/sec", oldOpsPerSec));
            System.out.println(String.format("    - NEW (protobuf4j): ~%.0f ops/sec", newOpsPerSec));
        } else {
            System.out.println(String.format("  | %-18s | %-15s | %-18s |", "OLD (Wire v3.1.2)", "N/A", "N/A"));
            System.out.println(String.format("  | %-18s | %-15s | %-18s |", "NEW (protobuf4j)", String.format("%.3f ms", newAvgMs), String.format("%.0f ms", newTotalMs)));

            System.out.println();
            System.out.println("  NOTE: Old (Wire) library not available for comparison.");
            System.out.println("  Run ProtobufBackwardIT for full apples-to-apples comparison.");

            System.out.println();
            System.out.println("  Operations per second (NEW only):");
            System.out.println(String.format("    - NEW (protobuf4j): ~%.0f ops/sec", newOpsPerSec));
        }
        System.out.println();
    }
}
