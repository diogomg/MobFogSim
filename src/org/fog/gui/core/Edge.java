package org.fog.gui.core;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * The model that represents an edge with two vertexes, for physical link and
 * virtual edge.
 */
public class Edge implements Serializable {
	private static final long serialVersionUID = -356975278987708987L;
	public enum EdgeType { GENERIC, PHYSICAL, VIRTUAL, APPLICATION }

	private Node dest = null;

	private double latency = 0.0;
	private String name = "";
	private long bandwidth = 0;
	private double tupleCpuLength = 0.0;
	private double tupleNetworkLength = 0.0;
	private String tupleType = "";
	private EdgeType edgeType = EdgeType.GENERIC;

	/**
	 * Constructor.
	 * 
	 * @param node
	 *        the node that belongs to the edge.
	 */
	public Edge(Node to) {
		setDestination(to);
	}

	/** physical topology link */
	public Edge(Node to, double latency) {
		setDestination(to);
		setLatency(latency);
		edgeType = EdgeType.PHYSICAL;
	}

	/** virtual virtual edge */
	public Edge(Node to, String name, long bw) {
		setDestination(to);
		setName(name);
		setBandwidth(bw);
		edgeType = EdgeType.VIRTUAL;
	}

	/** Application tuple edge. */
	public Edge(Node to, String tupleType, double tupleCpuLength,
		double tupleNetworkLength) {
		this(to, tupleType, tupleType, tupleCpuLength, tupleNetworkLength);
	}

	/** Application tuple edge with a display name and tuple type. */
	public Edge(Node to, String name, String tupleType, double tupleCpuLength,
		double tupleNetworkLength) {
		setDestination(to);
		setName(name);
		setTupleType(tupleType);
		setTupleCpuLength(tupleCpuLength);
		setTupleNetworkLength(tupleNetworkLength);
		edgeType = EdgeType.APPLICATION;
	}

	/** copy edge */
	public Edge(Node to, Map<String, Object> info) {
		setDestination(to);
		setInfo(info);
		Object storedType = info.get("edgeType");
		if (storedType != null) {
			try {
				edgeType = EdgeType.valueOf(storedType.toString());
			}
			catch (IllegalArgumentException error) {
				throw new IllegalArgumentException(
					"Unknown graph edge type: " + storedType, error);
			}
		}
		else if (info.containsKey("tupleCpuLength")
			|| info.containsKey("tupleNetworkLength")) {
			edgeType = EdgeType.APPLICATION;
		}
		else if (info.containsKey("latency")) {
			edgeType = EdgeType.PHYSICAL;
		}
		else if (info.containsKey("bandwidth") || info.containsKey("name")) {
			edgeType = EdgeType.VIRTUAL;
		}
	}

	private void setDestination(Node destination) {
		if (destination == null) {
			throw new IllegalArgumentException("Edge destination cannot be null");
		}
		this.dest = destination;
	}

	private void setName(String name) {
		this.name = name == null ? "" : name;
	}

	private void setBandwidth(long bandwidth) {
		if (bandwidth < 0L) {
			throw new IllegalArgumentException("Edge bandwidth cannot be negative");
		}
		this.bandwidth = bandwidth;
	}

	private void setLatency(double latency) {
		if (!Double.isFinite(latency) || latency < 0.0) {
			throw new IllegalArgumentException(
				"Edge latency must be finite and non-negative");
		}
		this.latency = latency;
	}

	private void setTupleCpuLength(double tupleCpuLength) {
		if (!Double.isFinite(tupleCpuLength) || tupleCpuLength < 0.0) {
			throw new IllegalArgumentException(
				"Tuple CPU length must be finite and non-negative");
		}
		this.tupleCpuLength = tupleCpuLength;
	}

	private void setTupleType(String tupleType) {
		if (tupleType == null || tupleType.trim().isEmpty()) {
			throw new IllegalArgumentException("Tuple type cannot be empty");
		}
		this.tupleType = tupleType;
	}

	private void setTupleNetworkLength(double tupleNetworkLength) {
		if (!Double.isFinite(tupleNetworkLength) || tupleNetworkLength < 0.0) {
			throw new IllegalArgumentException(
				"Tuple network length must be finite and non-negative");
		}
		this.tupleNetworkLength = tupleNetworkLength;
	}

	public Node getNode() {
		return dest;
	}

	public long getBandwidth() {
		return bandwidth;
	}

	public String getName() {
		return name;
	}

	public double getLatency() {
		return latency;
	}

	public double getTupleCpuLength() {
		return tupleCpuLength;
	}

	public String getTupleType() {
		return tupleType;
	}

	public double getTupleNetworkLength() {
		return tupleNetworkLength;
	}

	public EdgeType getEdgeType() {
		return edgeType;
	}

	public Map<String, Object> getInfo() {
		Map<String, Object> info = new HashMap<String, Object>();
		info.put("name", this.name);
		info.put("bandwidth", this.bandwidth);
		info.put("latency", this.latency);
		info.put("tupleCpuLength", this.tupleCpuLength);
		info.put("tupleNetworkLength", this.tupleNetworkLength);
		info.put("tupleType", this.tupleType);
		info.put("edgeType", this.edgeType.name());
		return info;
	}

	public void setInfo(Map<String, Object> info) {
		if (info == null) {
			throw new IllegalArgumentException("Edge information cannot be null");
		}
		if (info.get("name") != null) {
			setName((String) info.get("name"));
		}
		if (info.get("bandwidth") != null) {
			setBandwidth(((Number) info.get("bandwidth")).longValue());
		}
		if (info.get("latency") != null) {
			setLatency(((Number) info.get("latency")).doubleValue());
		}
		if (info.get("tupleCpuLength") != null) {
			setTupleCpuLength(
				((Number) info.get("tupleCpuLength")).doubleValue());
		}
		if (info.get("tupleType") != null) {
			setTupleType(info.get("tupleType").toString());
		}
		if (info.get("tupleNetworkLength") != null) {
			setTupleNetworkLength(
				((Number) info.get("tupleNetworkLength")).doubleValue());
		}
	}

	@Override
	public String toString() {
		return "Edge [dest=" + dest + "]";
	}

}
