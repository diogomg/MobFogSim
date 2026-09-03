package org.fog.vmmobile.policy;

/** Describes a validated association-list mutation. */
public enum MembershipAction {
	ADD(0),
	REMOVE(1);

	private final int legacyValue;

	MembershipAction(int legacyValue) {
		this.legacyValue = legacyValue;
	}

	public int legacyValue() {
		return legacyValue;
	}

	public static MembershipAction fromLegacy(int value) {
		for (MembershipAction action : values()) {
			if (action.legacyValue == value) {
				return action;
			}
		}
		throw new IllegalArgumentException("Membership action must be 0 (add) or 1 (remove)");
	}
}
