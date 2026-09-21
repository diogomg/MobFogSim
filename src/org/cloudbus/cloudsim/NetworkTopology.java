/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for
 * Modeling and Simulation of Clouds Licence: GPL -
 * http://www.gnu.org/copyleft/gpl.html Copyright (c) 2009-2012, The University
 * of Melbourne, Australia
 */

package org.cloudbus.cloudsim;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.network.DelayMatrix_Float;
import org.cloudbus.cloudsim.network.GraphReaderBrite;
import org.cloudbus.cloudsim.network.TopologicalGraph;
import org.cloudbus.cloudsim.network.TopologicalLink;
import org.cloudbus.cloudsim.network.TopologicalNode;

/**
 * NetworkTopology is a class that implements network layer in CloudSim. It
 * reads a BRITE file and generates a topological network from it. Information
 * of this network is used to simulate latency in network traffic of CloudSim.
 * <p>
 * The topology file may contain more nodes the the number of entities in the
 * simulation. It allows for users to increase the scale of the simulation
 * without changing the topology file. Nevertheless, each CloudSim entity must
 * be mapped to one (and only one) BRITE node to allow proper work of the
 * network simulation. Each BRITE node can be mapped to only one entity at a
 * time.
 *
 * <p>This class is also the canonical owner of MobFogSim's logical transport
 * closure. Direct-link membership is indexed separately from the lazily
 * generated all-pairs delay matrix so connectivity checks remain inexpensive.</p>
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class NetworkTopology {
	private static final int MAX_ARRAY_INDEXED_ENTITY_ID = 1000000;

	protected static int nextIdx = 0;

	private static boolean networkEnabled = false;

	protected static DelayMatrix_Float delayMatrix = null;

	protected static double[][] bwMatrix = null;
	private static List<BitSet> directLinks = new ArrayList<BitSet>();

	protected static TopologicalGraph graph = null;

	protected static Map<Integer, Integer> map = null;
	private static int[] topologyNodeByEntityId = new int[0];

	private static boolean matricesDirty = false;

	/** Clears topology state so a later simulation in this JVM starts cleanly. */
	public static synchronized void reset() {
		nextIdx = 0;
		networkEnabled = false;
		delayMatrix = null;
		bwMatrix = null;
		directLinks = new ArrayList<BitSet>();
		graph = null;
		map = null;
		topologyNodeByEntityId = new int[0];
		matricesDirty = false;
	}

	/**
	 * Creates the network topology if file exists and if file can be
	 * succesfully parsed. File is written in the BRITE format and contains
	 * topologycal information on simulation entities.
	 * 
	 * @param fileName
	 *        name of the BRITE file
	 * @pre fileName != null
	 * @post $none
	 */
	public static void buildNetworkTopology(String fileName) {
		Log.printLine("Topology file: " + fileName);

		// try to find the file
		GraphReaderBrite reader = new GraphReaderBrite();

		try {
			graph = reader.readGraphFile(fileName);
			map = new HashMap<Integer, Integer>();
			topologyNodeByEntityId = new int[0];
			directLinks = createDirectLinkIndex(graph, false);
			nextIdx = graph.getNumberOfNodes();
			generateMatrices();
		} catch (IOException e) {
			// problem with the file. Does not simulate network
			Log.printLine("Problem in processing BRITE file. Network simulation is disabled. Error: "
				+ e.getMessage());
		}

	}

	/**
	 * Generates the matrices used internally to set latency and bandwidth
	 * between elements
	 */
	private static void generateMatrices() {
		// creates the delay matrix
		delayMatrix = new DelayMatrix_Float(graph, false);

		// creates the bandwidth matrix
		bwMatrix = createBwMatrix(graph, false);

		networkEnabled = true;
		matricesDirty = false;
	}

	private static void ensureMatrices() {
		if (matricesDirty && graph != null) {
			generateMatrices();
		}
	}

	/**
	 * Adds a new link in the network topology
	 * 
	 * @param srcId
	 *        ID of the link's source
	 * @param destId
	 *        ID of the link's destination
	 * @param bw
	 *        Link's bandwidth
	 * @param lat
	 *        link's latency
	 * @pre srcId > 0
	 * @pre destId > 0
	 * @post $none
	 */
	public static void addLink(int srcId, int destId, double bw, double lat) {

		if (graph == null) {
			graph = new TopologicalGraph();
			directLinks = new ArrayList<BitSet>();
		}

		if (map == null) {
			map = new HashMap<Integer, Integer>();
		}

		// maybe add the nodes
		if (!map.containsKey(srcId)) {
			graph.addNode(new TopologicalNode(nextIdx));
			registerMapping(srcId, nextIdx);
			ensureDirectLinkNode(nextIdx);
			nextIdx++;
		}

		if (!map.containsKey(destId)) {
			graph.addNode(new TopologicalNode(nextIdx));
			registerMapping(destId, nextIdx);
			ensureDirectLinkNode(nextIdx);
			nextIdx++;
		}

		// generate a new link
		int sourceNode = map.get(srcId).intValue();
		int destinationNode = map.get(destId).intValue();
		graph.addLink(new TopologicalLink(sourceNode, destinationNode,
			(float) lat, (float) bw));
		directLinks.get(sourceNode).set(destinationNode);
		directLinks.get(destinationNode).set(sourceNode);

		matricesDirty = true;
		networkEnabled = true;

	}

	private static void ensureDirectLinkNode(int topologyNodeId) {
		while (directLinks.size() <= topologyNodeId) {
			directLinks.add(new BitSet());
		}
	}

	private static void registerMapping(int entityId, int topologyNodeId) {
		map.put(entityId, topologyNodeId);
		if (entityId < 0 || entityId > MAX_ARRAY_INDEXED_ENTITY_ID) {
			return;
		}
		if (entityId >= topologyNodeByEntityId.length) {
			int previousLength = topologyNodeByEntityId.length;
			int expandedLength = Math.min(MAX_ARRAY_INDEXED_ENTITY_ID + 1,
				Math.max(entityId + 1, Math.max(16, previousLength * 2)));
			topologyNodeByEntityId = Arrays.copyOf(topologyNodeByEntityId,
				expandedLength);
			Arrays.fill(topologyNodeByEntityId, previousLength, expandedLength, -1);
		}
		topologyNodeByEntityId[entityId] = topologyNodeId;
	}

	private static int mappedTopologyNode(int entityId) {
		if (entityId >= 0 && entityId < topologyNodeByEntityId.length) {
			int topologyNode = topologyNodeByEntityId[entityId];
			if (topologyNode >= 0) {
				return topologyNode;
			}
		}
		Integer topologyNode = map == null ? null : map.get(entityId);
		return topologyNode == null ? -1 : topologyNode.intValue();
	}

	/**
	 * Creates the matrix containiing the available bandiwdth beteen two nodes
	 * 
	 * @param graph
	 *        topological graph describing the topology
	 * @param directed
	 *        true if the graph is directed; false otherwise
	 * @return the bandwidth graph
	 */
	private static double[][] createBwMatrix(TopologicalGraph graph, boolean directed) {
		int nodes = graph.getNumberOfNodes();

		double[][] mtx = new double[nodes][nodes];

		// cleanup matrix
		for (int i = 0; i < nodes; i++) {
			for (int j = 0; j < nodes; j++) {
				mtx[i][j] = 0.0;
			}
		}

		Iterator<TopologicalLink> iter = graph.getLinkIterator();
		while (iter.hasNext()) {
			TopologicalLink edge = iter.next();

			mtx[edge.getSrcNodeID()][edge.getDestNodeID()] = edge.getLinkBw();

			if (!directed) {
				mtx[edge.getDestNodeID()][edge.getSrcNodeID()] = edge.getLinkBw();
			}
		}

		return mtx;
	}

	private static List<BitSet> createDirectLinkIndex(TopologicalGraph graph,
		boolean directed) {
		List<BitSet> links = new ArrayList<BitSet>(graph.getNumberOfNodes());
		for (int node = 0; node < graph.getNumberOfNodes(); node++) {
			links.add(new BitSet());
		}
		Iterator<TopologicalLink> iterator = graph.getLinkIterator();
		while (iterator.hasNext()) {
			TopologicalLink edge = iterator.next();
			links.get(edge.getSrcNodeID()).set(edge.getDestNodeID());
			if (!directed) {
				links.get(edge.getDestNodeID()).set(edge.getSrcNodeID());
			}
		}
		return links;
	}

	/**
	 * Maps a CloudSim entity to a node in the network topology
	 * 
	 * @param cloudSimEntityID
	 *        ID of the entity being mapped
	 * @param briteID
	 *        ID of the BRITE node that corresponds to the CloudSim entity
	 * @pre cloudSimEntityID >= 0
	 * @pre briteID >= 0
	 * @post $none
	 */
	public static void mapNode(int cloudSimEntityID, int briteID) {
		if (networkEnabled) {
			try {
				// this CloudSim entity was already mapped?
				if (!map.containsKey(cloudSimEntityID)) {
					if (!map.containsValue(briteID)) { // this BRITE node was
														// already mapped?
						registerMapping(cloudSimEntityID, briteID);
					} else {
						Log.printLine("Error in network mapping. BRITE node " + briteID
							+ " already in use.");
					}
				} else {
					Log.printLine("Error in network mapping. CloudSim entity " + cloudSimEntityID
						+ " already mapped.");
				}
			} catch (Exception e) {
				Log.printLine("Error in network mapping. CloudSim node " + cloudSimEntityID
					+ " not mapped to BRITE node " + briteID + ".");
			}
		}
	}

	/**
	 * Unmaps a previously mapped CloudSim entity to a node in the network
	 * topology
	 * 
	 * @param cloudSimEntityID
	 *        ID of the entity being unmapped
	 * @pre cloudSimEntityID >= 0
	 * @post $none
	 */
	public static void unmapNode(int cloudSimEntityID) {
		if (networkEnabled) {
			try {
				map.remove(cloudSimEntityID);
				if (cloudSimEntityID >= 0
					&& cloudSimEntityID < topologyNodeByEntityId.length) {
					topologyNodeByEntityId[cloudSimEntityID] = -1;
				}
			} catch (Exception e) {
				Log.printLine("Error in network unmapping. CloudSim node: " + cloudSimEntityID);
			}
		}
	}

	/**
	 * Calculates the delay between two nodes
	 * 
	 * @param srcID
	 *        ID of the source node
	 * @param destID
	 *        ID of the destination node
	 * @return communication delay between the two nodes
	 * @pre srcID >= 0
	 * @pre destID >= 0
	 * @post $none
	 */
	public static double getDelay(int srcID, int destID) {
		ensureMatrices();
		if (networkEnabled) {
			try {
				// add the network latency
				int source = mappedTopologyNode(srcID);
				int destination = mappedTopologyNode(destID);
				if (source < 0 || destination < 0) {
					return 0.0;
				}
				double delay = delayMatrix.getDelay(source, destination);

				return delay;
			} catch (Exception e) {
				// in case of error, just keep running and return 0.0
			}
		}
		return 0.0;
	}

	/** Returns whether the canonical topology contains this direct logical link. */
	public static boolean hasDirectLink(int srcID, int destID) {
		if (!networkEnabled || map == null) {
			return false;
		}
		int source = mappedTopologyNode(srcID);
		int destination = mappedTopologyNode(destID);
		return source >= 0 && destination >= 0 && source < directLinks.size()
			&& directLinks.get(source).get(destination);
	}

	/**
	 * Counts directed logical links whose endpoints both belong to the supplied
	 * entity set. Each registered undirected link contributes two routes.
	 */
	public static long getDirectedLinkCountAmong(Collection<Integer> entityIds) {
		if (entityIds == null) {
			throw new IllegalArgumentException("Entity ID collection cannot be null");
		}
		if (graph == null || map == null || entityIds.isEmpty()) {
			return 0L;
		}
		Set<Integer> topologyNodeIds = new HashSet<Integer>();
		for (Integer entityId : entityIds) {
			if (entityId == null) {
				throw new IllegalArgumentException(
					"Entity ID collection cannot contain null entries");
			}
			Integer topologyNodeId = map.get(entityId);
			if (topologyNodeId != null) {
				topologyNodeIds.add(topologyNodeId);
			}
		}
		long routes = 0L;
		Iterator<TopologicalLink> links = graph.getLinkIterator();
		while (links.hasNext()) {
			TopologicalLink link = links.next();
			if (topologyNodeIds.contains(link.getSrcNodeID())
				&& topologyNodeIds.contains(link.getDestNodeID())) {
				routes += 2L;
			}
		}
		return routes;
	}

	/**
	 * This method returns true if network simulation is working. If there were
	 * some problem during creation of network (e.g., during parsing of BRITE
	 * file) that does not allow a proper simulation of the network, this method
	 * returns false.
	 * 
	 * @return $true if network simulation is ok. $false otherwise
	 * @pre $none
	 * @post $none
	 */
	public static boolean isNetworkEnabled() {
		return networkEnabled;
	}

	/** Returns the number of nodes in the currently registered topology. */
	public static int getNumberOfNodes() {
		return graph == null ? 0 : graph.getNumberOfNodes();
	}

	/** Returns the number of links in the currently registered topology. */
	public static int getNumberOfLinks() {
		return graph == null ? 0 : graph.getNumberOfLinks();
	}

}
