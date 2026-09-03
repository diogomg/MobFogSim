package org.fog.vmmobile;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic, named random streams for one simulation run.
 *
 * <p>A component's sequence is independent of the order in which other named
 * streams are first requested. Requesting the same name returns the same
 * stream instance for the lifetime of the run.</p>
 */
public final class RandomStreams {
	private final long rootSeed;
	private final Map<String, Random> streams = new HashMap<String, Random>();

	public RandomStreams(long rootSeed) {
		this.rootSeed = rootSeed;
	}

	public synchronized Random get(String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new IllegalArgumentException("Random stream name cannot be blank");
		}
		Random stream = streams.get(name);
		if (stream == null) {
			stream = new Random(deriveSeed(rootSeed, name));
			streams.put(name, stream);
		}
		return stream;
	}

	static long deriveSeed(long seed, String name) {
		long value = seed ^ 0x9E3779B97F4A7C15L;
		for (int index = 0; index < name.length(); index++) {
			value ^= name.charAt(index);
			value *= 0x100000001B3L;
			value ^= value >>> 32;
		}
		value ^= value >>> 30;
		value *= 0xBF58476D1CE4E5B9L;
		value ^= value >>> 27;
		value *= 0x94D049BB133111EBL;
		return value ^ value >>> 31;
	}
}
