package org.fog.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.Tuple;

public class TimeKeeper {

	private static TimeKeeper instance;

	private long simulationStartTime;
	private int count;
	private Map<Integer, Double> emitTimes;
	private Map<Integer, Double> endTimes;
	private Map<Integer, List<Integer>> loopIdToTupleIds;
	private Map<Integer, Double> tupleIdToCpuStartTime;
	private Map<String, Double> tupleTypeToAverageCpuTime;
	private Map<String, Integer> tupleTypeToExecutedTupleCount;
	private Map<Integer, Double> maxLoopExecutionTime;

	private Map<Integer, Double> loopIdToCurrentAverage;
	private Map<Integer, Integer> loopIdToCurrentNum;

	public static TimeKeeper getInstance() {
		if (instance == null)
			instance = new TimeKeeper();
		return instance;
	}

	/** Installs the timing state owned by the current simulation context. */
	public static synchronized void setInstance(TimeKeeper timeKeeper) {
		instance = timeKeeper;
	}

	public int getUniqueId() {
		return count++;
	}

	public void tupleStartedExecution(Tuple tuple) {
		tupleIdToCpuStartTime.put(tuple.getCloudletId(), CloudSim.clock());
	}

	public void tupleEndedExecution(Tuple tuple) {
		Double startTime = tupleIdToCpuStartTime.remove(tuple.getCloudletId());
		if (startTime == null)
			return;
		double executionTime = CloudSim.clock() - startTime;

		if (!tupleTypeToAverageCpuTime.containsKey(tuple.getTupleType())) {
			tupleTypeToAverageCpuTime.put(tuple.getTupleType(), executionTime);
			tupleTypeToExecutedTupleCount.put(tuple.getTupleType(), 1);
		} else {
			double currentAverage = tupleTypeToAverageCpuTime.get(tuple.getTupleType());
			int currentCount = tupleTypeToExecutedTupleCount.get(tuple.getTupleType());
			tupleTypeToAverageCpuTime.put(tuple.getTupleType(),
				(currentAverage * currentCount + executionTime) / (currentCount + 1));
			tupleTypeToExecutedTupleCount.put(tuple.getTupleType(), currentCount + 1);
		}
	}

	/**
	 * Registers a loop for final reporting without retaining every tuple ID
	 * generated for that loop.
	 */
	public void registerLoop(int loopId) {
		if (!loopIdToTupleIds.containsKey(loopId)) {
			loopIdToTupleIds.put(loopId, java.util.Collections.<Integer>emptyList());
		}
	}

	/** Records the timestamp at which an end-to-end tuple was emitted. */
	public void recordEmission(int tupleId, double emissionTime) {
		if (!Double.isFinite(emissionTime)) {
			throw new IllegalArgumentException("Emission time must be finite");
		}
		emitTimes.put(tupleId, emissionTime);
	}

	public Double getEmissionTime(int tupleId) {
		return emitTimes.get(tupleId);
	}

	public Double consumeEmissionTime(int tupleId) {
		return emitTimes.remove(tupleId);
	}

	/** Initialises aggregate timing for a loop and reports whether it was new. */
	public boolean initialiseLoopTiming(int loopId) {
		if (loopIdToCurrentAverage.containsKey(loopId)) {
			return false;
		}
		loopIdToCurrentAverage.put(loopId, 0.0);
		loopIdToCurrentNum.put(loopId, 0);
		maxLoopExecutionTime.put(loopId, 0.0);
		return true;
	}

	/** Records one loop delay and reports whether it established a new maximum. */
	public boolean recordLoopDelay(int loopId, double delay) {
		if (!Double.isFinite(delay) || delay < 0.0) {
			throw new IllegalArgumentException(
				"Loop delay must be finite and non-negative");
		}
		initialiseLoopTiming(loopId);
		double currentAverage = loopIdToCurrentAverage.get(loopId);
		int currentCount = loopIdToCurrentNum.get(loopId);
		loopIdToCurrentAverage.put(loopId,
			(currentAverage * currentCount + delay) / (currentCount + 1));
		loopIdToCurrentNum.put(loopId, currentCount + 1);
		double previousMaximum = maxLoopExecutionTime.get(loopId);
		if (delay > previousMaximum) {
			maxLoopExecutionTime.put(loopId, delay);
			return true;
		}
		return false;
	}

	/** Explicit test/adapter boundary for a tuple CPU start timestamp. */
	public void recordTupleCpuStart(int tupleId, double startTime) {
		if (!Double.isFinite(startTime)) {
			throw new IllegalArgumentException("CPU start time must be finite");
		}
		tupleIdToCpuStartTime.put(tupleId, startTime);
	}

	/** Clears measurements while retaining the singleton identity. */
	public void resetMeasurements() {
		emitTimes.clear();
		endTimes.clear();
		loopIdToTupleIds.clear();
		tupleIdToCpuStartTime.clear();
		tupleTypeToAverageCpuTime.clear();
		tupleTypeToExecutedTupleCount.clear();
		maxLoopExecutionTime.clear();
		loopIdToCurrentAverage.clear();
		loopIdToCurrentNum.clear();
	}

	public Map<Integer, List<Integer>> loopIdToTupleIds() {
		return getInstance().getLoopIdToTupleIds();
	}

	public TimeKeeper() {
		count = 1;
		emitTimes = new HashMap<Integer, Double>();
		endTimes = new HashMap<Integer, Double>();
		loopIdToTupleIds = new HashMap<Integer, List<Integer>>();
		tupleTypeToAverageCpuTime = new HashMap<String, Double>();
		tupleTypeToExecutedTupleCount = new HashMap<String, Integer>();
		tupleIdToCpuStartTime = new HashMap<Integer, Double>();
		loopIdToCurrentAverage = new HashMap<Integer, Double>();
		loopIdToCurrentNum = new HashMap<Integer, Integer>();
		maxLoopExecutionTime = new HashMap<Integer, Double>();
	}

	public int getCount() {
		return count;
	}

	public void setCount(int count) {
		this.count = count;
	}

	public Map<Integer, Double> getEmitTimes() {
		return Collections.unmodifiableMap(emitTimes);
	}

	public void setEmitTimes(Map<Integer, Double> emitTimes) {
		this.emitTimes = copyMap(emitTimes, "Emission time map");
	}

	public Map<Integer, Double> getEndTimes() {
		return Collections.unmodifiableMap(endTimes);
	}

	public void setEndTimes(Map<Integer, Double> endTimes) {
		this.endTimes = copyMap(endTimes, "End time map");
	}

	public Map<Integer, List<Integer>> getLoopIdToTupleIds() {
		Map<Integer, List<Integer>> snapshot =
			new HashMap<Integer, List<Integer>>();
		for (Map.Entry<Integer, List<Integer>> entry
			: loopIdToTupleIds.entrySet()) {
			snapshot.put(entry.getKey(), Collections.unmodifiableList(
				new ArrayList<Integer>(entry.getValue())));
		}
		return Collections.unmodifiableMap(snapshot);
	}

	public void setLoopIdToTupleIds(Map<Integer, List<Integer>> loopIdToTupleIds) {
		if (loopIdToTupleIds == null) {
			throw new IllegalArgumentException("Loop tuple map cannot be null");
		}
		this.loopIdToTupleIds = new HashMap<Integer, List<Integer>>();
		for (Map.Entry<Integer, List<Integer>> entry
			: loopIdToTupleIds.entrySet()) {
			this.loopIdToTupleIds.put(entry.getKey(),
				new ArrayList<Integer>(entry.getValue()));
		}
	}

	public Map<String, Double> getTupleTypeToAverageCpuTime() {
		return Collections.unmodifiableMap(tupleTypeToAverageCpuTime);
	}

	public void setTupleTypeToAverageCpuTime(
		Map<String, Double> tupleTypeToAverageCpuTime) {
		this.tupleTypeToAverageCpuTime = copyMap(tupleTypeToAverageCpuTime,
			"Tuple CPU average map");
	}

	public Map<String, Integer> getTupleTypeToExecutedTupleCount() {
		return Collections.unmodifiableMap(tupleTypeToExecutedTupleCount);
	}

	public void setTupleTypeToExecutedTupleCount(
		Map<String, Integer> tupleTypeToExecutedTupleCount) {
		this.tupleTypeToExecutedTupleCount = copyMap(
			tupleTypeToExecutedTupleCount, "Tuple execution count map");
	}

	public Map<Integer, Double> getTupleIdToCpuStartTime() {
		return Collections.unmodifiableMap(tupleIdToCpuStartTime);
	}

	public void setTupleIdToCpuStartTime(Map<Integer, Double> tupleIdToCpuStartTime) {
		this.tupleIdToCpuStartTime = copyMap(tupleIdToCpuStartTime,
			"Tuple CPU start map");
	}

	public long getSimulationStartTime() {
		return simulationStartTime;
	}

	public void setSimulationStartTime(long simulationStartTime) {
		this.simulationStartTime = simulationStartTime;
	}

	public Map<Integer, Double> getLoopIdToCurrentAverage() {
		return Collections.unmodifiableMap(loopIdToCurrentAverage);
	}

	public void setLoopIdToCurrentAverage(Map<Integer, Double> loopIdToCurrentAverage) {
		this.loopIdToCurrentAverage = copyMap(loopIdToCurrentAverage,
			"Loop average map");
	}

	public Map<Integer, Integer> getLoopIdToCurrentNum() {
		return Collections.unmodifiableMap(loopIdToCurrentNum);
	}

	public void setLoopIdToCurrentNum(Map<Integer, Integer> loopIdToCurrentNum) {
		this.loopIdToCurrentNum = copyMap(loopIdToCurrentNum,
			"Loop count map");
	}

	public Map<Integer, Double> getMaxLoopExecutionTime() {
		return Collections.unmodifiableMap(maxLoopExecutionTime);
	}

	public void setMaxLoopExecutionTime(Map<Integer, Double> maxLoopExecutionTime) {
		this.maxLoopExecutionTime = copyMap(maxLoopExecutionTime,
			"Maximum loop execution map");
	}

	private static <K, V> Map<K, V> copyMap(Map<K, V> values,
		String description) {
		if (values == null) {
			throw new IllegalArgumentException(description + " cannot be null");
		}
		return new HashMap<K, V>(values);
	}

}
