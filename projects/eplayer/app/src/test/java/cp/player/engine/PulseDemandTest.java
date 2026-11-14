package cp.player.engine;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class PulseDemandTest {
    @Test public void hiddenArtworkDoesNotRequestAnalysisAndVisibleReadersKeepItAlive() {
        PulseDemand gate = new PulseDemand();
        assertFalse(gate.tryMeasure(0));
        gate.request(100);
        assertTrue(gate.tryMeasure(100));
        assertFalse(gate.tryMeasure(132));
        assertTrue(gate.tryMeasure(133));
        gate.request(1000);
        assertTrue(gate.tryMeasure(1601));
        assertFalse(gate.tryMeasure(2501));
        gate.request(3000);
        assertTrue(gate.tryMeasure(3000));
    }
    @Test public void concurrentProducersDoNotDuplicateTheSameFrameMeasurement() throws Exception {
        PulseDemand gate = new PulseDemand(); gate.request(100);
        AtomicInteger grants = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(8), start = new CountDownLatch(1), done = new CountDownLatch(8);
        for (int i = 0; i < 8; i++) new Thread(() -> {
            ready.countDown();
            try { start.await(); if (gate.tryMeasure(100)) grants.incrementAndGet(); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            finally { done.countDown(); }
        }).start();
        ready.await(); start.countDown(); done.await();
        assertEquals(1, grants.get());
        assertTrue(gate.tryMeasure(133));
    }
}
