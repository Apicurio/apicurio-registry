package io.apicurio.registry.kafka.utils;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Simple consumer actions.
 */
public interface ConsumerActions<K, V> {

    /**
     * Submit an action to be performed in the consumer thread and return a {@link CompletableFuture}
     * that will be completed with the action outcome when executed.
     *
     * @param consumerAction the action to perform in the consumer thread, taking the {@link Consumer} parameter
     *                       and returning some arbitrary result
     * @param <R>            the return type of the action
     * @return a {@link CompletableFuture} completed with action result in consumer thread
     * @throws IllegalStateException if this method is invoked from the consumer thread
     */
    <R> CompletableFuture<R> submit(Function<? super Consumer<K, V>, ? extends R> consumerAction);

    // Lifecycle

    default void start() {};
    default boolean isRunning() {return true;};
    default void stop() {};

    /**
     * Implementation of actions that allow dynamic assignment of topic/partitions.
     *
     * @param <K>
     * @param <V>
     */
    interface DynamicAssignment<K, V> extends ConsumerActions<K, V> {
        /**
         * Dynamically add given topic partition with initial seek offset to the set of assigned topic partitions.
         * If the container already has this topic/partition assigned, just seek is performed to the
         * desired offset.
         *
         * @param tp         topic partition
         * @param seekOffset a specification for initial seek
         */
        default CompletableFuture<Void> addTopicPartition(TopicPartition tp, Seek.Offset seekOffset) {
            Objects.requireNonNull(tp);
            Objects.requireNonNull(seekOffset);

            Logger log = LoggerFactory.getLogger(DynamicAssignment.class);
            log.info("Adding: topic-partition: {} with {}", tp, seekOffset);

            return submit(consumer -> {

                Set<TopicPartition> oldTps = consumer.assignment();
                Set<TopicPartition> newTps = new HashSet<>(oldTps);
                newTps.add(tp);

                if (!oldTps.equals(newTps)) {
                    log.info("Reassigning topic-partition(s): {} -> {}", oldTps, newTps);
                    consumer.assign(newTps);
                }

                seekOffset.accept(consumer, tp);

                return null;
            });
        }

        /**
         * Dynamically remove given topic partition from the set of assigned topic partitions.
         *
         * @param tp topic partition to remove
         */
        default CompletableFuture<Void> removeTopicParition(TopicPartition tp) {
            Objects.requireNonNull(tp);

            Logger log = LoggerFactory.getLogger(DynamicAssignment.class);
            log.info("Removing topic-partition: {}", tp);

            return submit(consumer -> {

                Set<TopicPartition> oldTps = consumer.assignment();
                Set<TopicPartition> newTps = new HashSet<>(oldTps);
                newTps.remove(tp);

                if (!oldTps.equals(newTps)) {
                    log.info("Reassigning topic-partition(s): {} -> {}", oldTps, newTps);
                    consumer.assign(newTps);
                }

                return null;
            });
        }
    }
}
