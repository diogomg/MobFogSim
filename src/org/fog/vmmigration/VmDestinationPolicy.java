package org.fog.vmmigration;

/** Controls which types of nodes are eligible VM migration destinations. */
public final class VmDestinationPolicy {

	public static final int EDGE_SERVERS_ONLY = 0;
	public static final int END_DEVICES_ONLY = 1;
	public static final int HYBRID = 2;

	public enum Destination {
		EDGE_SERVERS(EDGE_SERVERS_ONLY),
		END_DEVICES(END_DEVICES_ONLY),
		HYBRID(VmDestinationPolicy.HYBRID);

		private final int legacyValue;

		Destination(int legacyValue) {
			this.legacyValue = legacyValue;
		}

		public int legacyValue() {
			return legacyValue;
		}

		public static Destination fromLegacy(int value) {
			for (Destination destination : values()) {
				if (destination.legacyValue == value) {
					return destination;
				}
			}
			throw new IllegalArgumentException(
				"VM destination policy must be 0 (edge), 1 (end device), or 2 (hybrid)");
		}
	}

	private static Destination policy = Destination.HYBRID;

	private VmDestinationPolicy() {
	}

	public static void configure(int destinationPolicy) {
		configure(Destination.fromLegacy(destinationPolicy));
	}

	public static void configure(Destination destinationPolicy) {
		if (destinationPolicy == null) {
			throw new IllegalArgumentException("VM destination policy cannot be null");
		}
		policy = destinationPolicy;
	}

	public static Destination getDestination() {
		return policy;
	}

	public static boolean allowsEdgeServers() {
		return policy == Destination.EDGE_SERVERS
			|| policy == Destination.HYBRID;
	}

	public static boolean allowsEndDevices() {
		return policy == Destination.END_DEVICES
			|| policy == Destination.HYBRID;
	}
}
