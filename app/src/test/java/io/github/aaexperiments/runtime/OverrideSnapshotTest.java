package io.github.aaexperiments.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public final class OverrideSnapshotTest {
    @Test public void replacementIsImmutableAndAtomicForConcurrentReaders() throws Exception {
        OverrideSnapshot snapshot = new OverrideSnapshot();
        Map<String, String> first = Map.of("generation", "A", "left", "A", "right", "A");
        Map<String, String> second = Map.of("generation", "B", "left", "B", "right", "B");
        snapshot.replace(first);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean partial = new AtomicBoolean(false);
        CountDownLatch started = new CountDownLatch(4);
        Thread[] readers = new Thread[4];
        for (int i = 0; i < readers.length; i++) {
            readers[i] = new Thread(() -> {
                started.countDown();
                while (running.get()) {
                    Map<String, String> view = snapshot.view();
                    String generation = view.get("generation");
                    if (generation == null || !generation.equals(view.get("left")) || !generation.equals(view.get("right"))) {
                        partial.set(true);
                        return;
                    }
                }
            });
            readers[i].start();
        }
        started.await();
        for (int i = 0; i < 50_000; i++) snapshot.replace((i & 1) == 0 ? second : first);
        running.set(false);
        for (Thread reader : readers) reader.join();

        assertTrue("A reader observed a partially replaced snapshot", !partial.get());
        assertEquals("A", snapshot.get("generation"));
        try {
            snapshot.view().put("unexpected", "value");
            throw new AssertionError("Snapshot view must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }
}
