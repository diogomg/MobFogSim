package org.fog.vmmigration;

/**
 * Immutable outcome of opening a connection for one migration request.
 */
final class ConnectionPreparationResult {
	private final boolean connected;
	private final double delay;

	ConnectionPreparationResult(boolean connected, double delay) {
		this.connected = connected;
		this.delay = delay;
	}

	boolean isConnected() {
		return connected;
	}

	double getDelay() {
		return delay;
	}
}
