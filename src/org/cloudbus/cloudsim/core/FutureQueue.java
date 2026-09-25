/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for
 * Modeling and Simulation of Clouds Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html Copyright (c) 2009-2012, The University
 * of Melbourne, Australia
 */

package org.cloudbus.cloudsim.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * This class implements the future event queue used by {@link Simulation}. The
 * hot path uses a {@link PriorityQueue}; sorted snapshot iterators retain the
 * ordering and removal behavior expected by compatibility callers.
 * 
 * @author Marcos Dias de Assuncao
 * @since CloudSim Toolkit 1.0
 * @see Simulation
 * @see java.util.PriorityQueue
 */
public class FutureQueue {

	/** Opaque snapshot used to undo events appended by a failed transaction. */
	static final class Checkpoint {
		private final List<SimEvent> events;
		private final long serial;
		private final long firstSerial;

		private Checkpoint(List<SimEvent> events, long serial, long firstSerial) {
			this.events = events;
			this.serial = serial;
			this.firstSerial = firstSerial;
		}
	}

	/** Binary heap of events ordered by time and stable serial. */
	private final PriorityQueue<SimEvent> priorityQueue =
		new PriorityQueue<SimEvent>();

	/** Serial assigned to ordinary tail insertions. */
	private long serial;

	/** Decreasing serial assigned to head insertions. */
	private long firstSerial = -1L;

	/**
	 * Add a new event to the queue. Adding a new event to the queue preserves
	 * the temporal order of the events in the queue.
	 * 
	 * @param newEvent
	 *        The event to be put in the queue.
	 */
	public void addEvent(SimEvent newEvent) {
		newEvent.setSerial(serial++);
		priorityQueue.add(newEvent);
	}

	/**
	 * Add a new event to the head of the queue.
	 * 
	 * @param newEvent
	 *        The event to be put in the queue.
	 */
	public void addEventFirst(SimEvent newEvent) {
		newEvent.setSerial(firstSerial--);
		priorityQueue.add(newEvent);
	}

	/**
	 * Tracks an event already serialised by another future queue without
	 * mutating its ordering key.
	 */
	void addEventReference(SimEvent event) {
		priorityQueue.add(event);
	}

	/** Returns the earliest event without removing it. */
	public SimEvent peek() {
		return priorityQueue.peek();
	}

	/** Removes and returns the earliest event. */
	public SimEvent poll() {
		return priorityQueue.poll();
	}

	/**
	 * Returns an iterator to the queue.
	 * 
	 * @return the iterator
	 */
	public Iterator<SimEvent> iterator() {
		final List<SimEvent> snapshot =
			new ArrayList<SimEvent>(priorityQueue);
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
				priorityQueue.remove(current);
				current = null;
			}
		};
	}

	/** Returns the heap iterator for internal scans that do not require ordering. */
	Iterator<SimEvent> unorderedIterator() {
		return priorityQueue.iterator();
	}

	/**
	 * Returns the size of this event queue.
	 * 
	 * @return the size
	 */
	public int size() {
		return priorityQueue.size();
	}

	/**
	 * Removes the event from the queue.
	 * 
	 * @param event
	 *        the event
	 * @return true, if successful
	 */
	public boolean remove(SimEvent event) {
		return priorityQueue.remove(event);
	}

	/**
	 * Removes all the events from the queue.
	 * 
	 * @param events
	 *        the events
	 * @return true, if successful
	 */
	public boolean removeAll(Collection<SimEvent> events) {
		return priorityQueue.removeAll(events);
	}

	/**
	 * Clears the queue.
	 */
	public void clear() {
		priorityQueue.clear();
	}

	Checkpoint checkpoint() {
		return new Checkpoint(new ArrayList<SimEvent>(priorityQueue),
			serial, firstSerial);
	}

	void restore(Checkpoint checkpoint) {
		if (checkpoint == null) {
			throw new IllegalArgumentException("Future-queue checkpoint cannot be null");
		}
		priorityQueue.clear();
		priorityQueue.addAll(checkpoint.events);
		serial = checkpoint.serial;
		firstSerial = checkpoint.firstSerial;
	}

}
