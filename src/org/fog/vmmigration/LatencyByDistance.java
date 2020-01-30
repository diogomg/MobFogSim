package org.fog.vmmigration;

import org.fog.entities.FogDevice;
import org.fog.entities.MobileDevice;
import org.fog.localization.Distances;

public class LatencyByDistance {

	private static double latencyConnection(FogDevice sc1, FogDevice sc2) {
		double distance = Distances.checkDistance(sc1.getCoord(), sc2.getCoord());
		double latency = distance * 0.01;
		return latency;
	}

	public static double latencyConnection(FogDevice sc, MobileDevice st) {
		double distance = Distances.checkDistance(st.getCoord(), st.getSourceAp().getCoord());
		double latency1 = latencyConnection(st.getSourceAp().getServerCloudlet(),
			st.getVmLocalServerCloudlet());// bw source and vmLocal
		double latency2 = distance * 0.001; 
		return latency1 + latency2;
	}
}
