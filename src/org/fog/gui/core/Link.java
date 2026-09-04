package org.fog.gui.core;

import java.io.Serializable;
import java.util.Map;

/**
 * The model that represents an edge with two vertexes, for physical link and
 * virtual edge.
 */
public class Link extends Edge implements Serializable {
	private static final long serialVersionUID = -356975278987708987L;

	/**
	 * Constructor.
	 * 
	 * @param node
	 *        the node that belongs to the edge.
	 */
	public Link(Node to) {
		super(to);
	}

	/** physical topology link */
	public Link(Node to, double latency) {
		super(to, latency);
	}

	/** virtual virtual edge */
	public Link(Node to, String name, long bw) {
		super(to, name, bw);
	}

	/** copy edge */
	public Link(Node to, Map<String, Object> info) {
		super(to, info);
	}

}
