package org.fog.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.PhysicalTopology;
import org.fog.entities.Sensor;
import org.fog.gui.core.ActuatorGui;
import org.fog.gui.core.Bridge;
import org.fog.gui.core.Edge;
import org.fog.gui.core.FogDeviceGui;
import org.fog.gui.core.Graph;
import org.fog.gui.core.Node;
import org.fog.gui.core.NodeType;
import org.fog.gui.core.SensorGui;
import org.fog.gui.core.TopologyException;
import org.fog.gui.core.TopologyType;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;

/** Builds simulator entities from a fully validated physical topology graph. */
public final class JsonToTopology {

	private JsonToTopology() {
	}

	public static PhysicalTopology getPhysicalTopology(int userId, String appId,
		String physicalTopologyFile) throws Exception {
		Graph graph = Bridge.jsonToGraph(physicalTopologyFile,
			TopologyType.PHYSICAL);
		List<FogDevice> fogDevices = new ArrayList<FogDevice>();
		List<Sensor> sensors = new ArrayList<Sensor>();
		List<Actuator> actuators = new ArrayList<Actuator>();
		Map<String, FogDevice> fogByName = new HashMap<String, FogDevice>();
		Map<String, Sensor> sensorByName = new HashMap<String, Sensor>();
		Map<String, Actuator> actuatorByName = new HashMap<String, Actuator>();

		for (Node node : graph.getAdjacencyList().keySet()) {
			if (node.getNodeType() == NodeType.FOG_DEVICE) {
				FogDeviceGui definition = (FogDeviceGui) node;
				FogDevice device = createFogDevice(definition);
				device.setParentId(-1);
				fogDevices.add(device);
				fogByName.put(node.getName(), device);
			}
			else if (node.getNodeType() == NodeType.SENSOR) {
				SensorGui definition = (SensorGui) node;
				Sensor sensor = new Sensor(node.getName(),
					definition.getSensorType(), userId, appId,
					definition.getDistribution());
				sensors.add(sensor);
				sensorByName.put(node.getName(), sensor);
			}
			else if (node.getNodeType() == NodeType.ACTUATOR) {
				ActuatorGui definition = (ActuatorGui) node;
				Actuator actuator = new Actuator(node.getName(), userId, appId,
					definition.getActuatorType());
				actuators.add(actuator);
				actuatorByName.put(node.getName(), actuator);
			}
			else {
				throw new TopologyException("Physical simulator topology "
					+ physicalTopologyFile + " cannot instantiate node '"
					+ node.getName() + "' of type " + node.getNodeType());
			}
		}

		for (Map.Entry<Node, List<Edge>> entry
			: graph.getAdjacencyList().entrySet()) {
			for (Edge edge : entry.getValue()) {
				connectEntities(entry.getKey().getName(), edge.getNode().getName(),
					edge.getLatency(), fogByName, sensorByName, actuatorByName,
					physicalTopologyFile);
			}
		}

		PhysicalTopology physicalTopology = new PhysicalTopology();
		physicalTopology.setFogDevices(fogDevices);
		physicalTopology.setActuators(actuators);
		physicalTopology.setSensors(sensors);
		return physicalTopology;
	}

	private static FogDevice createFogDevice(FogDeviceGui definition)
		throws Exception {
		List<Pe> peList = new ArrayList<Pe>();
		peList.add(new Pe(0, new PeProvisionerOverbooking(
			definition.getMips())));

		long storage = 1000000;
		int bandwidth = 10000;
		PowerHost host = new PowerHost(FogUtils.generateEntityId(),
			new RamProvisionerSimple(definition.getRam()),
			new BwProvisionerOverbooking(bandwidth), storage, peList,
			new StreamOperatorScheduler(peList),
			new PowerModelLinear(107.339, 83.4333));
		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);
		LinkedList<Storage> storageList = new LinkedList<Storage>();
		FogDeviceCharacteristics characteristics =
			new FogDeviceCharacteristics("x86", "Linux", "Xen", host, 10.0,
				3.0, 0.05, 0.001, 0.0);
		FogDevice device = new FogDevice(definition.getName(), characteristics,
			new AppModuleAllocationPolicy(hostList), storageList, 10,
			definition.getUpBw(), definition.getDownBw(), 0,
			definition.getRatePerMips());
		device.setLevel(definition.getLevel());
		return device;
	}

	private static void connectEntities(String sourceName,
		String destinationName, double latency,
		Map<String, FogDevice> fogByName, Map<String, Sensor> sensorByName,
		Map<String, Actuator> actuatorByName, String topologyFile) {
		FogDevice sourceFog = fogByName.get(sourceName);
		FogDevice destinationFog = fogByName.get(destinationName);
		Sensor sourceSensor = sensorByName.get(sourceName);
		Sensor destinationSensor = sensorByName.get(destinationName);
		Actuator sourceActuator = actuatorByName.get(sourceName);
		Actuator destinationActuator = actuatorByName.get(destinationName);

		if (sourceFog != null && destinationFog != null) {
			FogDevice southern = sourceFog.getLevel() > destinationFog.getLevel()
				? sourceFog : destinationFog;
			FogDevice northern = southern == sourceFog
				? destinationFog : sourceFog;
			if (sourceFog.getLevel() == destinationFog.getLevel()) {
				throw invalidLink(topologyFile, sourceName, destinationName,
					"fog devices at the same level have no parent direction");
			}
			southern.setUplinkLatency(latency);
			southern.setParentId(northern.getId());
			return;
		}
		if (sourceFog != null && destinationSensor != null) {
			connectSensor(destinationSensor, sourceFog, latency);
			return;
		}
		if (sourceSensor != null && destinationFog != null) {
			connectSensor(sourceSensor, destinationFog, latency);
			return;
		}
		if (sourceFog != null && destinationActuator != null) {
			connectActuator(destinationActuator, sourceFog, latency);
			return;
		}
		if (sourceActuator != null && destinationFog != null) {
			connectActuator(sourceActuator, destinationFog, latency);
			return;
		}
		throw invalidLink(topologyFile, sourceName, destinationName,
			"only fog-to-fog and fog-to-peripheral links are supported");
	}

	private static void connectSensor(Sensor sensor, FogDevice fogDevice,
		double latency) {
		sensor.setLatency(latency);
		sensor.setGatewayDeviceId(fogDevice.getId());
	}

	private static void connectActuator(Actuator actuator, FogDevice fogDevice,
		double latency) {
		actuator.setLatency(latency);
		actuator.setGatewayDeviceId(fogDevice.getId());
	}

	private static TopologyException invalidLink(String topologyFile,
		String source, String destination, String reason) {
		return new TopologyException("Physical simulator topology "
			+ topologyFile + " has invalid link '" + source + "' -> '"
			+ destination + "': " + reason);
	}
}
