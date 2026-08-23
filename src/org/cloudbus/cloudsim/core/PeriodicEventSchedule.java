/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for
 * Modeling and Simulation of Clouds Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html
 */

package org.cloudbus.cloudsim.core;

/**
 * Compact description of a periodic series of simulation events.
 *
 * <p>Only the next occurrence is retained. After it is dispatched, the
 * schedule advances by one period and is returned to the periodic queue.</p>
 */
final class PeriodicEventSchedule implements Comparable<PeriodicEventSchedule> {

	private final int source;
	private final int destination;
	private final int tag;
	private final Object data;
	private final double period;
	private final double endTimeExclusive;
	private final long registrationOrder;
	private double nextTime;

	PeriodicEventSchedule(int source, int destination, int tag, Object data,
		double firstTime, double period, double endTimeExclusive, long registrationOrder) {
		this.source = source;
		this.destination = destination;
		this.tag = tag;
		this.data = data;
		this.nextTime = firstTime;
		this.period = period;
		this.endTimeExclusive = endTimeExclusive;
		this.registrationOrder = registrationOrder;
	}

	double getNextTime() {
		return nextTime;
	}

	SimEvent createNextEvent() {
		return new SimEvent(SimEvent.SEND, nextTime, source, destination, tag, data);
	}

	boolean advance() {
		nextTime += period;
		return nextTime < endTimeExclusive;
	}

	@Override
	public int compareTo(PeriodicEventSchedule other) {
		int timeComparison = Double.compare(nextTime, other.nextTime);
		if (timeComparison != 0) {
			return timeComparison;
		}
		return Long.compare(registrationOrder, other.registrationOrder);
	}
}
