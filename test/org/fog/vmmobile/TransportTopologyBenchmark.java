package org.fog.vmmobile;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.NetworkTopology;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.FogDevice;

/** Standalone scale probe for the Phase 2.4 logical transport closure. */
public final class TransportTopologyBenchmark {
	private static final int QUERY_COUNT = 250000;
	private static volatile long blackhole;
	private static Object retained;

	private TransportTopologyBenchmark() {}

	public static void main(String[] arguments) {
		if (arguments.length != 1) {
			throw new IllegalArgumentException("Expected one server count");
		}
		int serverCount = Integer.parseInt(arguments[0]);
		if (serverCount < 2) {
			throw new IllegalArgumentException("Server count must be at least two");
		}
		Log.disable();
		CloudSim.init(0, Calendar.getInstance(), false);
		NetworkTopology.reset();
		List<FogDevice> servers = servers(serverCount);

		forceGc();
		long baseMemory = usedMemory();
		long buildStarted = System.nanoTime();
		new TopologyService().createTransportNetwork(servers, 61.0,
			new Random(290538L));
		long buildNanos = System.nanoTime() - buildStarted;

		long indexStarted = System.nanoTime();
		blackhole += NetworkTopology.hasDirectLink(servers.get(0).getId(),
			servers.get(1).getId()) ? 1L : 0L;
		long indexNanos = System.nanoTime() - indexStarted;
		long queryStarted = System.nanoTime();
		for (int operation = 0; operation < QUERY_COUNT; operation++) {
			FogDevice source = servers.get(operation % serverCount);
			FogDevice destination = servers.get(
				(operation * 31 + 1) % serverCount);
			if (source == destination) {
				destination = servers.get((destination.getMyId() + 1) % serverCount);
			}
			if (NetworkTopology.hasDirectLink(source.getId(), destination.getId())) {
				blackhole++;
			}
		}
		long queryNanos = System.nanoTime() - queryStarted;
		forceGc();
		long canonicalMemory = usedMemory() - baseMemory;

		long duplicateStarted = System.nanoTime();
		List<Map<FogDevice, Double>> legacyPeerMaps =
			legacyPeerMaps(servers);
		long duplicateBuildNanos = System.nanoTime() - duplicateStarted;
		long legacyQueryStarted = System.nanoTime();
		for (int operation = 0; operation < QUERY_COUNT; operation++) {
			int source = operation % serverCount;
			int destination = (operation * 31 + 1) % serverCount;
			if (source == destination) {
				destination = (destination + 1) % serverCount;
			}
			Double bandwidth = legacyPeerMaps.get(source).get(
				servers.get(destination));
			blackhole += bandwidth == null ? 0L : bandwidth.longValue();
		}
		long legacyQueryNanos = System.nanoTime() - legacyQueryStarted;
		retained = legacyPeerMaps;
		forceGc();
		long duplicatedMemory = usedMemory() - baseMemory - canonicalMemory;

		System.out.println("transport_topology_benchmark_version=1");
		System.out.println("servers=" + serverCount);
		System.out.println("logical_links="
			+ NetworkTopology.getNumberOfLinks());
		System.out.println("directed_routes="
			+ ((long) serverCount * (serverCount - 1L)));
		System.out.println("canonical_build_milliseconds="
			+ nanosToMillis(buildNanos));
		System.out.println("direct_index_build_milliseconds="
			+ nanosToMillis(indexNanos));
		System.out.println("canonical_retained_bytes=" + canonicalMemory);
		System.out.println("canonical_query_ns_per_operation="
			+ queryNanos / (double) QUERY_COUNT);
		System.out.println("removed_duplicate_map_build_milliseconds="
			+ nanosToMillis(duplicateBuildNanos));
		System.out.println("removed_duplicate_map_retained_bytes="
			+ duplicatedMemory);
		System.out.println("legacy_map_query_ns_per_operation="
			+ legacyQueryNanos / (double) QUERY_COUNT);
		System.out.println("blackhole=" + blackhole);
	}

	private static List<FogDevice> servers(int count) {
		int width = (int) Math.ceil(Math.sqrt(count));
		List<FogDevice> result = new ArrayList<FogDevice>(count);
		for (int index = 0; index < count; index++) {
			FogDevice server = new FogDevice("server-" + index,
				index % width, index / width, index);
			server.setUplinkBandwidth(1000.0 + index % 17);
			server.setDownlinkBandwidth(1000.0 + index % 13);
			result.add(server);
		}
		return result;
	}

	private static List<Map<FogDevice, Double>> legacyPeerMaps(
		List<FogDevice> servers) {
		List<Map<FogDevice, Double>> result =
			new ArrayList<Map<FogDevice, Double>>(servers.size());
		for (FogDevice source : servers) {
			Map<FogDevice, Double> peers = new HashMap<FogDevice, Double>();
			for (FogDevice destination : servers) {
				if (source != destination) {
					peers.put(destination, Math.min(source.getUplinkBandwidth(),
						destination.getDownlinkBandwidth()));
				}
			}
			result.add(peers);
		}
		return result;
	}

	private static void forceGc() {
		for (int pass = 0; pass < 3; pass++) {
			System.gc();
		}
	}

	private static long usedMemory() {
		Runtime runtime = Runtime.getRuntime();
		return runtime.totalMemory() - runtime.freeMemory();
	}

	private static double nanosToMillis(long nanos) {
		return nanos / 1000000.0;
	}
}
