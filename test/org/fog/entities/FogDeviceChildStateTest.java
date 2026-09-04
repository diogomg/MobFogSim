package org.fog.entities;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.junit.Before;
import org.junit.Test;

public class FogDeviceChildStateTest {

	@Before
	public void initializeCloudSim() {
		CloudSim.init(0, Calendar.getInstance(), false);
	}

	@Test
	public void removingAChildClearsAllChildSpecificState() {
		ChildAwareFogDevice parent = new ChildAwareFogDevice("parent");
		FogDevice child = new FogDevice("child", 0, 0, 1);

		parent.attachChild(child.getId(), 4.5);

		parent.detach(child.getId());

		assertFalse(parent.getChildrenIds().contains(child.getId()));
		assertFalse(parent.getChildToLatencyMap().containsKey(child.getId()));
		assertFalse(parent.getChildToOperatorsMap().containsKey(child.getId()));
	}

	private static final class ChildAwareFogDevice extends FogDevice {
		private ChildAwareFogDevice(String name) {
			super(name, 0, 0, 0);
			setChildrenIds(new ArrayList<Integer>());
			setChildToLatencyMap(new HashMap<Integer, Double>());
			setChildToOperatorsMap(new HashMap<Integer, List<String>>());
		}

		private void detach(int childId) {
			removeChild(childId);
		}
	}
}
