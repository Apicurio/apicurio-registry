package io.apicurio.registry.kafka.utils;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.function.BiConsumer;

/**
 * Consumer seek options.
 */
public enum Seek {
    FROM_BEGINNING, FROM_END, FROM_CURRENT, TO_ABSOLUTE;

    public Offset offset(long offset) {
        return new Offset(offset);
    }

    static final Logger log = LoggerFactory.getLogger(Seek.class);

    public final class Offset implements BiConsumer<Consumer<?, ?>, TopicPartition> {

        private final long offset;

        Offset(long offset) {
            this.offset = offset;
        }

        public long offset() {
            return offset;
        }

        public Seek seek() {
            return Seek.this;
        }


        @Override
        public void accept(Consumer<?, ?> consumer, TopicPartition topicPartition) {
            Long absOffset = null;
            switch (seek()) {
                case FROM_BEGINNING:
                    consumer.seekToBeginning(Collections.singletonList(topicPartition));
                    log.info("seekToBeginning: {}", topicPartition);
                    if (offset != 0L) {
                        absOffset = Math.max(0L, consumer.position(topicPartition) + offset);
                    }
                    break;
                case FROM_END:
                    consumer.seekToEnd(Collections.singletonList(topicPartition));
                    log.info("seekToEnd: {}", topicPartition);
                    if (offset != 0L) {
                        absOffset = Math.max(0L, consumer.position(topicPartition) + offset);
                    }
                    break;
                case FROM_CURRENT:
                    if (offset != 0L) {
                        absOffset = Math.max(0L, consumer.position(topicPartition) + offset);
                    }
                    break;
                case TO_ABSOLUTE:
                    absOffset = offset;
                    break;
            }
            if (absOffset != null) {
                log.info("seek: {} to offset: {}", topicPartition, absOffset);
                consumer.seek(topicPartition, absOffset);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Offset offset1 = (Offset) o;
            return seek() == offset1.seek() &&
                   offset == offset1.offset;
        }

        @Override
        public int hashCode() {
            return seek().hashCode() * 31 + Long.hashCode(offset);
        }

        @Override
        public String toString() {
            return "Seek." + seek() + ".offset(" + offset + ")";
        }
    }
}
