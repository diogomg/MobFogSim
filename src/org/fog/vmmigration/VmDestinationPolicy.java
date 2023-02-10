package org.fog.vmmigration;

/** Controls which types of nodes are eligible VM migration destinations. */
public final class VmDestinationPolicy {

	public static final int EDGE_SERVERS_ONLY = 0;
	public static final int END_DEVICES_ONLY = 1;
	public static final int HYBRID = 2;

	private static int policy = HYBRID;

	private VmDestinationPolicy() {
	}

	public static void configure(int destinationPolicy) {
		if (destinationPolicy < EDGE_SERVERS_ONLY || destinationPolicy > HYBRID) {
			throw new IllegalArgumentException(
				"VM destination policy must be 0 (edge), 1 (end device), or 2 (hybrid)");
		}
		policy = destinationPolicy;
	}

	public static boolean allowsEdgeServers() {
		return policy == EDGE_SERVERS_ONLY || policy == HYBRID;
	}

	public static boolean allowsEndDevices() {
		return policy == END_DEVICES_ONLY || policy == HYBRID;
	}
}
