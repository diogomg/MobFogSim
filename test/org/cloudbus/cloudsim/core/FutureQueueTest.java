package org.cloudbus.cloudsim.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Iterator;

import org.junit.Test;

public class FutureQueueTest {

	@Test
	public void sameTimeEventsRetainInsertionOrder() {
		FutureQueue queue = new FutureQueue();
		SimEvent first = event(10.0, 1);
		SimEvent second = event(10.0, 2);
		SimEvent third = event(10.0, 3);

		queue.addEvent(first);
		queue.addEvent(second);
		queue.addEvent(third);

		assertSame(first, queue.poll());
		assertSame(second, queue.poll());
		assertSame(third, queue.poll());
		assertNull(queue.poll());
	}

	@Test
	public void headInsertionsPrecedeOrdinaryEventsAndUseHeadOrder() {
		FutureQueue queue = new FutureQueue();
		SimEvent ordinaryFirst = event(10.0, 1);
		SimEvent ordinarySecond = event(10.0, 2);
		SimEvent priorityFirst = event(10.0, 3);
		SimEvent prioritySecond = event(10.0, 4);

		queue.addEvent(ordinaryFirst);
		queue.addEvent(ordinarySecond);
		queue.addEventFirst(priorityFirst);
		queue.addEventFirst(prioritySecond);

		assertSame(prioritySecond, queue.poll());
		assertSame(priorityFirst, queue.poll());
		assertSame(ordinaryFirst, queue.poll());
		assertSame(ordinarySecond, queue.poll());
	}

	@Test
	public void checkpointRestoreRecoversEventsAndSerialOrder() {
		FutureQueue queue = new FutureQueue();
		SimEvent retained = event(5.0, 1);
		queue.addEvent(retained);
		FutureQueue.Checkpoint checkpoint = queue.checkpoint();
		SimEvent discarded = event(1.0, 2);
		queue.addEvent(discarded);

		assertSame(discarded, queue.poll());

		queue.restore(checkpoint);
		SimEvent appended = event(5.0, 3);
		queue.addEvent(appended);

		assertEquals(2, queue.size());
		assertSame(retained, queue.poll());
		assertSame(appended, queue.poll());
	}

	@Test
	public void compatibilityIteratorIsSortedAndRemovesFromHeap() {
		FutureQueue queue = new FutureQueue();
		SimEvent later = event(20.0, 1);
		SimEvent earlier = event(10.0, 2);
		queue.addEvent(later);
		queue.addEvent(earlier);

		Iterator<SimEvent> iterator = queue.iterator();

		assertSame(earlier, iterator.next());
		iterator.remove();
		assertEquals(1, queue.size());
		assertSame(later, queue.peek());
	}

	private static SimEvent event(double time, int tag) {
		return new SimEvent(SimEvent.SEND, time, 1, 2, tag, null);
	}
}
