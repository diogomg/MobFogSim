package org.fog.gui.core;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.fog.utils.FogUtils;

/** Panel that displays a graph */
public class GraphView extends JPanel {

	private static final long serialVersionUID = 1L;

	private JPanel canvas;
	private Graph graph;

	private Image imgHost;
	private Image imgSensor;
	private Image imgSwitch;
	private Image imgAppModule;
	private Image imgActuator;
	private Image imgSensorModule;
	private Image imgActuatorModule;

	public GraphView(final Graph graph) {

		this.graph = graph;
		imgHost = Toolkit.getDefaultToolkit().getImage(
			this.getClass().getResource("/images/host.png"));
		imgSwitch = Toolkit.getDefaultToolkit().getImage(
			this.getClass().getResource("/images/disk.png"));
		imgAppModule = Toolkit.getDefaultToolkit().getImage(
			this.getClass().getResource("/images/module.png"));
		imgSensor = Toolkit.getDefaultToolkit().getImage(
			this.getClass().getResource("/images/sensor.png"));
		imgActuator = Toolkit.getDefaultToolkit().getImage(
			this.getClass().getResource("/images/actuator.png"));
		imgSensorModule = Toolkit.getDefaultToolkit().getImage(
			this.getClass().getResource("/images/sensorModule.png"));
		imgActuatorModule = Toolkit.getDefaultToolkit().getImage(
			this.getClass().getResource("/images/actuatorModule.png"));

		initComponents();
	}

	private Map<Node, List<Node>> createChildrenMap() {
		Map<Node, List<Node>> childrenMap = new HashMap<Node, List<Node>>();
		for (Node node : graph.getAdjacencyList().keySet()) {
			if (node.getType().equals("FOG_DEVICE") && !childrenMap.containsKey(node))
				childrenMap.put(node, new ArrayList<Node>());
			List<Edge> edgeList = graph.getAdjacencyList().get(node);

			for (Edge edge : edgeList) {
				Node neighbour = edge.getNode();
				if (node.getType().equals("SENSOR") || node.getType().equals("ACTUATOR")) {
					if (!childrenMap.containsKey(neighbour)) {
						childrenMap.put(neighbour, new ArrayList<Node>());
					}
					childrenMap.get(neighbour).add(node);
				} else if (neighbour.getType().equals("SENSOR")
					|| neighbour.getType().equals("ACTUATOR")) {
					if (!childrenMap.containsKey(node)) {
						childrenMap.put(node, new ArrayList<Node>());
					}
					childrenMap.get(node).add(neighbour);
				} else {
					Node child = (((FogDeviceGui) node).getLevel() > ((FogDeviceGui) neighbour)
						.getLevel()) ? node : neighbour;
					Node parent = (((FogDeviceGui) node).getLevel() < ((FogDeviceGui) neighbour)
						.getLevel()) ? node : neighbour;
					if (!childrenMap.containsKey(parent)) {
						childrenMap.put(parent, new ArrayList<Node>());
					}
					childrenMap.get(parent).add(child);
				}
			}
		}
		return childrenMap;
	}

	@SuppressWarnings("serial")
	private void initComponents() {

		canvas = new JPanel() {
			@Override
			public void paint(Graphics g) {

				if (graph.getAdjacencyList() == null) {
					return;
				}

				Map<Node, Coordinates> coordForNodes = new HashMap<Node, Coordinates>();

				int height = 40;

				FontMetrics f = g.getFontMetrics();
				int nodeHeight = Math.max(height, f.getHeight());
				int nodeWidth = nodeHeight;

				int maxLevel = -1, minLevel = FogUtils.MAX;
				Map<Integer, List<Node>> levelMap = new HashMap<Integer, List<Node>>();
				List<Node> endpoints = new ArrayList<Node>();
				for (Node node : graph.getAdjacencyList().keySet()) {
					if (node.getType().equals("FOG_DEVICE")) {
						int level = ((FogDeviceGui) node).getLevel();
						if (!levelMap.containsKey(level))
							levelMap.put(level, new ArrayList<Node>());
						levelMap.get(level).add(node);

						if (level > maxLevel)
							maxLevel = level;
						if (level < minLevel)
							minLevel = level;
					} else if (node.getType().equals("SENSOR") || node.getType().equals("ACTUATOR")) {
						endpoints.add(node);
					}
				}

				double yDist = canvas.getHeight() / (maxLevel - minLevel + 3);

				Map<Integer, List<PlaceHolder>> levelToPlaceHolderMap = new HashMap<Integer, List<PlaceHolder>>();

				int k = 1;
				for (int i = minLevel; i <= maxLevel; i++, k++) {
					double xDist = canvas.getWidth() / (levelMap.get(i).size() + 1);

					for (int j = 1; j <= levelMap.get(i).size(); j++) {
						int x = (int) xDist * j;
						int y = (int) yDist * k;
						if (!levelToPlaceHolderMap.containsKey(i))
							levelToPlaceHolderMap.put(i, new ArrayList<PlaceHolder>());
						levelToPlaceHolderMap.get(i).add(new PlaceHolder(x, y));
					}
				}

				List<PlaceHolder> endpointPlaceHolders = new ArrayList<PlaceHolder>();

				double xDist = canvas.getWidth() / (endpoints.size() + 1);
				for (int i = 0; i < endpoints.size(); i++) {
					Node node = endpoints.get(i);
					int x = (int) xDist * (i + 1);
					int y = (int) yDist * k;
					endpointPlaceHolders.add(new PlaceHolder(x, y));

					coordForNodes.put(node, new Coordinates(x, y));
					node.setCoordinate(new Coordinates(x, y));
				}

				for (Node node : graph.getAdjacencyList().keySet()) {
					if (node.getType().equals("FOG_DEVICE") || node.getType().equals("SENSOR")
						|| node.getType().equals("ACTUATOR"))
						continue;
				}

				coordForNodes = getCoordForNodes(levelToPlaceHolderMap, endpointPlaceHolders,
					levelMap, endpoints, minLevel, maxLevel);
				System.out.println("COORD MAP" + coordForNodes);

				Map<Node, List<Node>> drawnList = new HashMap<Node, List<Node>>();

				for (Entry<Node, Coordinates> entry : coordForNodes.entrySet()) {
					// first paint a single node for testing.
					g.setColor(Color.black);

					Coordinates wrapper = entry.getValue();
					String nodeName = entry.getKey().getName();
					switch (entry.getKey().getType()) {
					case "host":
						g.drawImage(imgHost, wrapper.getX() - nodeWidth / 2, wrapper.getY()
							- nodeHeight / 2, nodeWidth, nodeHeight, this);
						break;
					case "APP_MODULE":
						g.drawImage(imgAppModule, wrapper.getX() - nodeWidth / 2, wrapper.getY()
							- nodeHeight / 2, nodeWidth, nodeHeight, this);
						g.drawString(nodeName, wrapper.getX() - f.stringWidth(nodeName) / 2,
							wrapper.getY() + nodeHeight);
						break;
					case "core":
					case "edge":
						g.drawImage(imgSwitch, wrapper.getX() - nodeWidth / 2, wrapper.getY()
							- nodeHeight / 2, nodeWidth, nodeHeight, this);
						break;
					case "FOG_DEVICE":
						g.drawImage(imgHost, wrapper.getX() - nodeWidth / 2, wrapper.getY()
							- nodeHeight / 2, nodeWidth, nodeHeight, this);
						g.drawString(nodeName, wrapper.getX() - f.stringWidth(nodeName) / 2,
							wrapper.getY() + nodeHeight);
						break;
					case "SENSOR":
						g.drawImage(imgSensor, wrapper.getX() - nodeWidth / 2, wrapper.getY()
							- nodeHeight / 2, nodeWidth, nodeHeight, this);
						g.drawString(nodeName, wrapper.getX() - f.stringWidth(nodeName) / 2,
							wrapper.getY() + nodeHeight);
						break;
					case "ACTUATOR":
						g.drawImage(imgActuator, wrapper.getX() - nodeWidth / 2, wrapper.getY()
							- nodeHeight / 2, nodeWidth, nodeHeight, this);
						g.drawString(nodeName, wrapper.getX() - f.stringWidth(nodeName) / 2,
							wrapper.getY() + nodeHeight);
						break;
					case "SENSOR_MODULE":
						g.drawImage(imgSensorModule, wrapper.getX() - nodeWidth / 2, wrapper.getY()
							- nodeHeight / 2, nodeWidth, nodeHeight, this);
						g.drawString(nodeName, wrapper.getX() - f.stringWidth(nodeName) / 2,
							wrapper.getY() + nodeHeight);
						break;
					case "ACTUATOR_MODULE":
						g.drawImage(imgActuatorModule, wrapper.getX() - nodeWidth / 2,
							wrapper.getY() - nodeHeight / 2, nodeWidth, nodeHeight, this);
						g.drawString(nodeName, wrapper.getX() - f.stringWidth(nodeName) / 2,
							wrapper.getY() + nodeHeight);
						break;
					}
				}

				// draw edges first
				// TODO: we draw one edge two times at the moment because we
				// have an undirected graph. But this
				// shouldn`t matter because we have the same edge costs and no
				// one will see in. Perhaps refactor later.
				for (Entry<Node, List<Edge>> entry : graph.getAdjacencyList().entrySet()) {

					Coordinates startNode = coordForNodes.get(entry.getKey());
					System.out.println("Start Node : " + entry.getKey().getName());

					for (Edge edge : entry.getValue()) {
						Coordinates targetNode = coordForNodes.get(edge.getNode());
						System.out.println("Target Node : " + edge.getNode().getName());
						g.setColor(Color.RED);
						g.drawLine(startNode.getX(), startNode.getY(), targetNode.getX(),
							targetNode.getY());

						if (drawnList.containsKey(entry.getKey())) {
							drawnList.get(entry.getKey()).add(edge.getNode());
						} else {
							List<Node> nodes = new ArrayList<Node>();
							nodes.add(edge.getNode());
							drawnList.put(entry.getKey(), nodes);
						}
					}
				}
			}
		};
		JScrollPane scrollPane = new JScrollPane(canvas);
		setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
		add(scrollPane);
	}

	protected Map<Node, Coordinates> getCoordForNodes(
		Map<Integer, List<PlaceHolder>> levelToPlaceHolderMap,
		List<PlaceHolder> endpointPlaceHolders,
		Map<Integer, List<Node>> levelMap, List<Node> endpoints, int minLevel, int maxLevel) {
		Map<Node, Coordinates> coordForNodesMap = new HashMap<Node, Coordinates>();
		Map<Node, List<Node>> childrenMap = createChildrenMap();

		for (Node node : graph.getAdjacencyList().keySet())
			node.setPlaced(false);
		for (Node node : endpoints)
			node.setPlaced(false);

		if (maxLevel < 0)
			return new HashMap<Node, Coordinates>();

		int j = 0;
		for (PlaceHolder placeHolder : levelToPlaceHolderMap.get(minLevel)) {
			Node node = levelMap.get(minLevel).get(j);
			placeHolder.setNode(node);
			node.setCoordinate(placeHolder.getCoordinates());
			coordForNodesMap.put(node, node.getCoordinate());
			node.setPlaced(true);
			j++;
		}

		for (int level = minLevel + 1; level <= maxLevel; level++) {
			List<PlaceHolder> upperLevelNodes = levelToPlaceHolderMap.get(level - 1);
			List<Node> nodes = levelMap.get(level);
			int i = 0;
			for (PlaceHolder parentPH : upperLevelNodes) {
				List<Node> children = childrenMap.get(parentPH.getNode());
				for (Node child : children) {
					PlaceHolder childPlaceHolder = levelToPlaceHolderMap.get(level).get(i);
					childPlaceHolder.setOccupied(true);
					childPlaceHolder.setNode(child);
					child.setCoordinate(childPlaceHolder.getCoordinates());
					coordForNodesMap.put(child, child.getCoordinate());
					child.setPlaced(true);
					i++;
				}
			}
			for (Node node : nodes) {
				if (!node.isPlaced()) {
					PlaceHolder placeHolder = levelToPlaceHolderMap.get(level).get(i);
					placeHolder.setOccupied(true);
					placeHolder.setNode(node);
					node.setCoordinate(placeHolder.getCoordinates());
					coordForNodesMap.put(node, node.getCoordinate());
					node.setPlaced(true);
					i++;
				}
			}
		}
		int i = 0;
		for (PlaceHolder parentPH : levelToPlaceHolderMap.get(maxLevel)) {
			List<Node> children = childrenMap.get(parentPH.getNode());
			for (Node child : children) {
				PlaceHolder placeHolder = endpointPlaceHolders.get(i);
				placeHolder.setOccupied(true);
				placeHolder.setNode(child);
				child.setCoordinate(placeHolder.getCoordinates());
				coordForNodesMap.put(child, child.getCoordinate());
				child.setPlaced(true);
				i++;
			}
		}
		for (Node node : endpoints) {
			if (!node.isPlaced()) {
				PlaceHolder placeHolder = endpointPlaceHolders.get(i);
				placeHolder.setOccupied(true);
				placeHolder.setNode(node);
				node.setCoordinate(placeHolder.getCoordinates());
				coordForNodesMap.put(node, node.getCoordinate());
				node.setPlaced(true);
				i++;
			}
		}
		return coordForNodesMap;
	}

	public void setGraph(Graph newGraph) {
		this.graph = newGraph;
	}
}
