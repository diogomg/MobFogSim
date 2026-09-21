package org.fog.vmmobile;

/**
 * Reports a contextual, fail-fast error while constructing or registering one
 * simulation aggregate.
 */
public final class SimulationBuildException extends IllegalStateException {
	private static final long serialVersionUID = 1L;

	public SimulationBuildException(String message) {
		super(message);
	}

	public SimulationBuildException(String message, Throwable cause) {
		super(message, cause);
	}
}
