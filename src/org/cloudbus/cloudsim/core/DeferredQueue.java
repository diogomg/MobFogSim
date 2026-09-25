/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for
 * Modeling and Simulation of Clouds Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html Copyright (c) 2009-2012, The University
 * of Melbourne, Australia
 */

package org.cloudbus.cloudsim.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.cloudbus.cloudsim.core.predicates.Predicate;

/**
 * This class implements the deferred event queue used by {@link Simulation}.
 * Events are indexed by destination so an entity does not scan events intended
 * for every other entity.
 * 
 * @author Marcos Dias de Assuncao
 * @since CloudSim Toolkit 1.0
 * @see Simulation
 * @see SimEvent
 */
public class DeferredQueue {

	/** Events indexed directly by dense CloudSim entity ID. */
	private final List<ArrayDeque<SimEvent>> eventsByDestination =
		new ArrayList<ArrayDeque<SimEvent>>();

	/** Number of queued events across every destination. */
	private int size;

	/** Stable deferred insertion order used by the compatibility iterator. */
	private long serial;

	/** Prepares a retained queue for a newly registered dense entity ID. */
	void registerDestination(int destination) {
		queueFor(destination, true);
	}

	/** Clears events for an entity while retaining its reusable queue. */
	void clearDestination(int destination) {
		ArrayDeque<SimEvent> destinationEvents = queueFor(destination, false);
		if (destinationEvents != null) {
			size -= destinationEvents.size();
			destinationEvents.clear();
		}
	}

	private ArrayDeque<SimEvent> queueFor(int destination, boolean create) {
		if (destination < 0) {
			throw new IllegalArgumentException(
				"Deferred-event destination cannot be negative: " + destination);
		}
		if (!create && destination >= eventsByDestination.size()) {
			return null;
		}
		while (eventsByDestination.size() <= destination) {
			eventsByDestination.add(null);
		}
		ArrayDeque<SimEvent> destinationEvents =
			eventsByDestination.get(destination);
		if (destinationEvents == null && create) {
			destinationEvents = new ArrayDeque<SimEvent>();
			eventsByDestination.set(destination, destinationEvents);
		}
		return destinationEvents;
	}

	/**
	 * Adds a new event to the queue. Adding a new event to the queue preserves
	 * the temporal order of the events.
	 * 
	 * @param newEvent
	 *        The event to be added to the queue.
	 */
	public void addEvent(SimEvent newEvent) {
		int destination = newEvent.getDestination();
		ArrayDeque<SimEvent> destinationEvents = queueFor(destination, true);

		// Events normally reach the deferred queue in nondecreasing clock order.
		// Keep a fallback for direct callers that add an earlier event.
		newEvent.setSerial(serial++);
		if (!destinationEvents.isEmpty()
			&& destinationEvents.peekLast().eventTime() > newEvent.eventTime()) {
			List<SimEvent> orderedEvents = new ArrayList<SimEvent>(destinationEvents);
			int index = 0;
			while (index < orderedEvents.size()
				&& orderedEvents.get(index).eventTime() <= newEvent.eventTime()) {
				index++;
			}
			orderedEvents.add(index, newEvent);
			destinationEvents.clear();
			destinationEvents.addAll(orderedEvents);
		} else {
			destinationEvents.addLast(newEvent);
		}
		size++;
	}

	/** Counts events for one destination that match the predicate. */
	public int count(int destination, Predicate predicate) {
		ArrayDeque<SimEvent> destinationEvents = queueFor(destination, false);
		if (destinationEvents == null) {
			return 0;
		}
		if (predicate == CloudSim.SIM_ANY) {
			return destinationEvents.size();
		}

		int count = 0;
		for (SimEvent event : destinationEvents) {
			if (predicate.match(event)) {
				count++;
			}
		}
		return count;
	}

	/** Removes and returns the first matching event for one destination. */
	public SimEvent poll(int destination, Predicate predicate) {
		ArrayDeque<SimEvent> destinationEvents = queueFor(destination, false);
		if (destinationEvents == null) {
			return null;
		}

		if (predicate == CloudSim.SIM_ANY) {
			SimEvent event = destinationEvents.pollFirst();
			if (event != null) {
				size--;
			}
			return event;
		}

		Iterator<SimEvent> iterator = destinationEvents.iterator();
		while (iterator.hasNext()) {
			SimEvent event = iterator.next();
			if (predicate.match(event)) {
				iterator.remove();
				size--;
				return event;
			}
		}
		return null;
	}

	/** Returns the first matching event for one destination without removing it. */
	public SimEvent find(int destination, Predicate predicate) {
		ArrayDeque<SimEvent> destinationEvents = queueFor(destination, false);
		if (destinationEvents != null) {
			if (predicate == CloudSim.SIM_ANY) {
				return destinationEvents.peekFirst();
			}
			for (SimEvent event : destinationEvents) {
				if (predicate.match(event)) {
					return event;
				}
			}
		}
		return null;
	}

	/**
	 * Returns an iterator to the events in the queue.
	 * 
	 * @return the iterator
	 */
	public Iterator<SimEvent> iterator() {
		final List<SimEvent> snapshot = new ArrayList<SimEvent>();
		for (ArrayDeque<SimEvent> destinationEvents : eventsByDestination) {
			if (destinationEvents != null) {
				snapshot.addAll(destinationEvents);
			}
		}
		Collections.sort(snapshot);

		final Iterator<SimEvent> snapshotIterator = snapshot.iterator();
		return new Iterator<SimEvent>() {
			private SimEvent current;

			@Override
			public boolean hasNext() {
				return snapshotIterator.hasNext();
			}

			@Override
			public SimEvent next() {
				current = snapshotIterator.next();
				return current;
			}

			@Override
			public void remove() {
				if (current == null) {
					throw new IllegalStateException();
				}
				removeEntry(current);
				current = null;
			}
		};
	}

	private void removeEntry(SimEvent event) {
		int destination = event.getDestination();
		ArrayDeque<SimEvent> destinationEvents = queueFor(destination, false);
		if (destinationEvents != null && destinationEvents.remove(event)) {
			size--;
		}
	}

	/**
	 * Returns the size of this event queue.
	 * 
	 * @return the number of events in the queue.
	 */
	public int size() {
		return size;
	}

	/**
	 * Clears the queue.
	 */
	public void clear() {
		eventsByDestination.clear();
		size = 0;
	}

}
