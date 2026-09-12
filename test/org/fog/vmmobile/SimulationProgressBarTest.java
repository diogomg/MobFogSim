package org.fog.vmmobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

public class SimulationProgressBarTest {

	@Test
	public void rendersOnlyIncreasingPercentagesAndCompletesOnOneLine() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		SimulationProgressBar progress = new SimulationProgressBar(
			new PrintStream(bytes));

		progress.start(1000.0);
		int initialLength = bytes.size();
		progress.update(9.0);
		assertEquals(initialLength, bytes.size());
		progress.update(250.0);
		int quarterLength = bytes.size();
		progress.update(249.0);
		assertEquals(quarterLength, bytes.size());
		progress.complete();

		String output = bytes.toString();
		assertTrue(output.contains("]   0%"));
		assertTrue(output.contains("]  25%"));
		assertTrue(output.contains("] 100%"));
		assertTrue(output.endsWith(System.lineSeparator()));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNonPositiveFinalSimulationTime() {
		new SimulationProgressBar(new PrintStream(new ByteArrayOutputStream()))
			.start(0.0);
	}
}
