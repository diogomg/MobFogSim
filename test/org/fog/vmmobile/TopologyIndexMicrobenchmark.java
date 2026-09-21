package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;
import org.fog.gui.core.Edge;
import org.fog.gui.core.Graph;
import org.fog.gui.core.Node;
import org.fog.gui.core.NodeType;

/** Reproducible microbenchmark for the Phase 2.3 lookup and snapshot costs. */
public final class TopologyIndexMicrobenchmark {
	private static final int DEVICE_COUNT = 2304;
	private static final int LOOKUP_OPERATIONS = 250000;
	private static final int SNAPSHOT_OPERATIONS = 250;
	private static volatile long blackhole;

	private TopologyIndexMicrobenchmark() {}

	public static void main(String[] arguments) {
		CloudSim.init(0, Calendar.getInstance(), false);
		SimulationTopology topology = topology();
		List<FogDevice> devices = topology.getServerCloudlets();
		Graph graph = graph();

		for (int index = 0; index < 10000; index++) {
			int selected = selectedIndex(index);
			consume(linearById(devices, devices.get(selected).getId()));
			consume(topology.getFogDeviceById(devices.get(selected).getId()));
			consume(linearByName(devices, name(selected)));
			consume(topology.getFogDeviceByName(name(selected)));
		}
		for (int index = 0; index < 10; index++) {
			consume(graph.snapshot().size());
		}

		long linearId = measure(new Runnable() {
			@Override
			public void run() {
				for (int index = 0; index < LOOKUP_OPERATIONS; index++) {
					int selected = selectedIndex(index);
					consume(linearById(devices, devices.get(selected).getId()));
				}
			}
		});
		long indexedId = measure(new Runnable() {
			@Override
			public void run() {
				for (int index = 0; index < LOOKUP_OPERATIONS; index++) {
					int selected = selectedIndex(index);
					consume(topology.getFogDeviceById(devices.get(selected).getId()));
				}
			}
		});
		long linearName = measure(new Runnable() {
			@Override
			public void run() {
				for (int index = 0; index < LOOKUP_OPERATIONS; index++) {
					consume(linearByName(devices, name(selectedIndex(index))));
				}
			}
		});
		long indexedName = measure(new Runnable() {
			@Override
			public void run() {
				for (int index = 0; index < LOOKUP_OPERATIONS; index++) {
					consume(topology.getFogDeviceByName(name(selectedIndex(index))));
				}
			}
		});
		long snapshots = measure(new Runnable() {
			@Override
			public void run() {
				for (int index = 0; index < SNAPSHOT_OPERATIONS; index++) {
					consume(graph.snapshot().size());
				}
			}
		});

		System.out.println("topology_microbenchmark_version=1");
		System.out.println("devices=" + DEVICE_COUNT);
		System.out.println("lookup_operations=" + LOOKUP_OPERATIONS);
		System.out.println("graph_snapshot_operations=" + SNAPSHOT_OPERATIONS);
		print("linear_id_lookup_ns_per_operation", linearId, LOOKUP_OPERATIONS);
		print("indexed_id_lookup_ns_per_operation", indexedId, LOOKUP_OPERATIONS);
		print("linear_name_lookup_ns_per_operation", linearName,
			LOOKUP_OPERATIONS);
		print("indexed_name_lookup_ns_per_operation", indexedName,
			LOOKUP_OPERATIONS);
		print("graph_snapshot_ns_per_operation", snapshots,
			SNAPSHOT_OPERATIONS);
		System.out.println("blackhole=" + blackhole);
	}

	private static SimulationTopology topology() {
		SimulationTopology result = new SimulationTopology();
		for (int index = 0; index < DEVICE_COUNT; index++) {
			result.serverCloudletRegistry().add(
				new FogDevice(name(index), 0, 0, index));
		}
		return result;
	}

	private static FogDevice linearById(List<FogDevice> devices, int id) {
		for (FogDevice device : devices) {
			if (device.getId() == id) {
				return device;
			}
		}
		return null;
	}

	private static FogDevice linearByName(List<FogDevice> devices, String name) {
		for (FogDevice device : devices) {
			if (device.getName().equals(name)) {
				return device;
			}
		}
		return null;
	}

	private static Graph graph() {
		Graph graph = new Graph();
		List<Node> nodes = new ArrayList<Node>(DEVICE_COUNT);
		for (int index = 0; index < DEVICE_COUNT; index++) {
			Node node = new Node("node-" + index, NodeType.VM);
			nodes.add(node);
			graph.addNode(node);
		}
		for (int index = 0; index < DEVICE_COUNT; index++) {
			for (int offset = 1; offset <= 4; offset++) {
				graph.addEdge(nodes.get(index),
					new Edge(nodes.get((index + offset) % DEVICE_COUNT)));
			}
		}
		return graph;
	}

	private static int selectedIndex(int operation) {
		return (operation * 31) % DEVICE_COUNT;
	}

	private static String name(int index) {
		return "server-" + index;
	}

	private static long measure(Runnable operation) {
		long start = System.nanoTime();
		operation.run();
		return System.nanoTime() - start;
	}

	private static void print(String name, long elapsed, int operations) {
		System.out.println(name + "=" + (elapsed / (double) operations));
	}

	private static void consume(Object value) {
		blackhole += value == null ? 0 : System.identityHashCode(value);
	}

	private static void consume(long value) {
		blackhole += value;
	}
}
