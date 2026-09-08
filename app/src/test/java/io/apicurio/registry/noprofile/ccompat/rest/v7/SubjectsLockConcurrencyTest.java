package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.ccompat.rest.v7.impl.SubjectsResourceImpl;
import io.apicurio.registry.model.GA;
import com.google.common.util.concurrent.Striped;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.assertSame;

class SubjectsLockConcurrencyTest {

    @SuppressWarnings("unchecked")
    @Test
    void testActiveLockIsNeverReplacedByGC() throws Exception {
        SubjectsResourceImpl resource = new SubjectsResourceImpl();
        Field field = SubjectsResourceImpl.class.getDeclaredField("subjectLocks");
        field.setAccessible(true);
        Striped<Lock> subjectLocks = (Striped<Lock>) field.get(resource);

        GA ga = new GA("test-group", "test-subject");

        // Thread A obtains the lock and keeps a strong reference to it
        Lock lockA = subjectLocks.get(ga);
        lockA.lock();

        try {
            // Trigger JVM garbage collection
            System.gc();

            // Thread B requests the lock for the same subject
            Lock lockB = subjectLocks.get(ga);

            // Assert they are the exact same lock object instance
            assertSame(lockA, lockB, "Active lock must not be replaced/recreated while in use");
        } finally {
            lockA.unlock();
        }
    }
}
