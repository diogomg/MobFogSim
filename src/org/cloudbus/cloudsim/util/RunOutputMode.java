package org.cloudbus.cloudsim.util;

import java.util.Locale;

/** Controls which simulation records are written below the run output root. */
public enum RunOutputMode {
	SUMMARY,
	FULL,
	NONE;

	public static RunOutputMode parse(String value) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Output mode cannot be empty");
		}
		try {
			return valueOf(value.trim().toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException error) {
			throw new IllegalArgumentException(
				"Output mode must be summary, full, or none", error);
		}
	}

	public boolean includesSummary() {
		return this != NONE;
	}

	public boolean includesDetailedRecords() {
		return this == FULL;
	}
}
