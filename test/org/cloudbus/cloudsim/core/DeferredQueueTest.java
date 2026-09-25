package org.cloudbus.cloudsim.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Iterator;

import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.junit.Test;

public class DeferredQueueTest {

	@Test
	public void destinationQueueOrdersEarlierDirectInsertions() {
		DeferredQueue queue = new DeferredQueue();
		queue.registerDestination(3);
		SimEvent later = event(2.0, 3, 1);
		SimEvent earlier = event(1.0, 3, 2);

		queue.addEvent(later);
		queue.addEvent(earlier);

		assertEquals(2, queue.size());
		assertSame(earlier, queue.poll(3, CloudSim.SIM_ANY));
		assertSame(later, queue.poll(3, CloudSim.SIM_ANY));
		assertNull(queue.poll(3, CloudSim.SIM_ANY));
		assertEquals(0, queue.size());
	}

	@Test
	public void matchingPollPreservesNonMatchingEvents() {
		DeferredQueue queue = new DeferredQueue();
		SimEvent first = event(1.0, 2, 10);
		SimEvent matching = event(2.0, 2, 20);
		queue.addEvent(first);
		queue.addEvent(matching);

		assertSame(matching, queue.poll(2, new PredicateType(20)));
		assertSame(first, queue.find(2, CloudSim.SIM_ANY));
		assertEquals(1, queue.size());
	}

	@Test
	public void clearingDestinationAllowsItsRetainedQueueToBeReused() {
		DeferredQueue queue = new DeferredQueue();
		queue.registerDestination(4);
		queue.addEvent(event(1.0, 4, 1));
		queue.addEvent(event(2.0, 4, 2));

		queue.clearDestination(4);

		assertEquals(0, queue.size());
		assertNull(queue.find(4, CloudSim.SIM_ANY));

		SimEvent replacement = event(3.0, 4, 3);
		queue.addEvent(replacement);

		assertEquals(1, queue.size());
		assertSame(replacement, queue.poll(4, CloudSim.SIM_ANY));
	}

	@Test
	public void compatibilityIteratorSortsAcrossDestinationsAndRemoves() {
		DeferredQueue queue = new DeferredQueue();
		SimEvent later = event(2.0, 1, 1);
		SimEvent earlier = event(1.0, 5, 2);
		queue.addEvent(later);
		queue.addEvent(earlier);

		Iterator<SimEvent> iterator = queue.iterator();

		assertSame(earlier, iterator.next());
		iterator.remove();
		assertEquals(1, queue.size());
		assertNull(queue.find(5, CloudSim.SIM_ANY));
		assertSame(later, queue.find(1, CloudSim.SIM_ANY));
	}

	private static SimEvent event(double time, int destination, int tag) {
		return new SimEvent(SimEvent.SEND, time, 0, destination, tag, null);
	}
}
