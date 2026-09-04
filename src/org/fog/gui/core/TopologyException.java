package org.fog.gui.core;

/** Describes a topology file that could not be read or validated. */
public final class TopologyException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;

	public TopologyException(String message) {
		super(message);
	}

	public TopologyException(String message, Throwable cause) {
		super(message, cause);
	}
}
