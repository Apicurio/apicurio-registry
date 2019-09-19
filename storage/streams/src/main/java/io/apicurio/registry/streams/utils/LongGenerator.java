package io.apicurio.registry.streams.utils;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

@FunctionalInterface
public interface LongGenerator {

    long getNext();

    class HiLo implements LongGenerator {
        private final LongGenerator hiGenerator;
        private final AtomicLong hilo = new AtomicLong();
        private final Object lock = new Object();
        private final int loBits;
        private final long loMask;

        public HiLo(LongGenerator hiGenerator) {
            this(hiGenerator, 16);
        }

        public HiLo(LongGenerator hiGenerator, int loBits) {
            this.hiGenerator = Objects.requireNonNull(hiGenerator);
            if (loBits < 1 || loBits > 62) {
                throw new IllegalArgumentException("loBits must be between 1 and 62");
            }
            this.loBits = loBits;
            this.loMask = (1L << loBits) - 1;
        }

        @Override
        public long getNext() {
            // retry loop
            while (true) {
                // obtain current hilo value
                long hl = hilo.get();
                // check for overflow
                if ((hl & loMask) == 0L) {
                    // initial or overflowed lo value
                    synchronized (lock) {
                        // re-obtain under lock
                        hl = hilo.get();
                        // re-check overflow under lock
                        if ((hl & loMask) == 0L) {
                            // still at initial or overflowed lo value, but we now have lock
                            // obtain new hi value and integrate it
                            hl = (hiGenerator.getNext() << loBits) | (hl & loMask);
                            // set back incremented value with new hi part and return this one
                            // (we don't need or want to CAS here as only one thread is modifying
                            // hilo at this time since it contains initial or overflowed lo value)
                            hilo.set(hl + 1);
                            return hl;
                        }
                    }
                } else {
                    // not initial and not overflowed - this is fast-path
                    // try to CAS back incremented value
                    if (hilo.compareAndSet(hl, hl + 1)) {
                        // if CAS was successful, return this value
                        return hl;
                    }
                }
                // in any other case, we lost race with a concurrent thread and must retry
            }
        }
    }
}
