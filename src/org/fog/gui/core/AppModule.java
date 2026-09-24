package org.fog.gui.core;

/**
 * The model that represents virtual machine node for the graph.
 */
public class AppModule extends Node {
	private static final long serialVersionUID = 804858850147477656L;

	public AppModule() {}

	public AppModule(String name) {
		this(NodeId.create(), name);
	}

	AppModule(NodeId nodeId, String name) {
		super(nodeId, name, NodeType.APP_MODULE);
	}

	@Override
	public String toString() {
		return "Node []";
	}

}
