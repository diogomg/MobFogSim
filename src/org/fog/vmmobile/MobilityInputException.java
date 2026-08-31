package org.fog.vmmobile;

/**
 * Reports invalid or unreadable mobility input with enough context to correct
 * the dataset configuration.
 */
public class MobilityInputException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public MobilityInputException(String message) {
		super(message);
	}

	public MobilityInputException(String message, Throwable cause) {
		super(message, cause);
	}
}
