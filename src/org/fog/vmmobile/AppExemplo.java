//package org.fog.vmmobile;
//import java.util.ArrayList;
//import java.util.Calendar;
//import java.util.List;
//import java.util.Random;
//
//import org.cloudbus.cloudsim.Log;
//import org.cloudbus.cloudsim.core.CloudSim;
//import org.fog.entities.*;
//import org.fog.localization.*;
//import org.fog.vmmobile.constants.*;
//
//import sun.awt.windows.ThemeReader;
//public class AppExemplo {
//
//	/**
//	 * @param args
//	 */
//	public static void main(String[] args) {
//		// TODO Auto-generated method stub
//		/**********It's necessary to CloudSim.java for working correctly**********/
//		Log.disable();
//		int numUser = 1; // number of cloud users
//		Calendar calendar = Calendar.getInstance();
//		boolean traceFlag = false; // mean trace events
//		CloudSim.init(numUser, calendar, traceFlag);
//		/**************************************************************************/
//		
////		MobileDevice smartThing[] = new MobileDevice[MaxAndMin.MAX_SMART_THING];
////		FogDevice serverCloudlet[] = new FogDevice[MaxAndMin.MAX_SERVER_CLOUDLET];
////		ApDevice apDevice[] = new ApDevice[MaxAndMin.MAX_AP_DEVICE];
//		List<MobileDevice> smartThing = new ArrayList<>();
//		List<FogDevice> serverCloudlet = new ArrayList<>();
//		List<ApDevice> apDevice = new ArrayList<>();
//		
//		Coordinate coordDevices=new Coordinate(MaxAndMin.MAX_X,
//										 MaxAndMin.MAX_Y);
//		
//		init(smartThing,coordDevices);
//		init(serverCloudlet,coordDevices);
//		init(apDevice,coordDevices);
//		
//		for(int i = 0;i<MaxAndMin.MAX_X;i++)
//			for(int j = 0;j<MaxAndMin.MAX_Y;j++)
//				if(coordDevices.getPositions(i,j)!=null) 
//					count++;
//		
//		System.out.println("Name: "+ smartThing[50].getName() +
//				"\nPositions X: " + smartThing[50].getCoord().getCoordX() +
//				" Y: "+ smartThing[50].getCoord().getCoordY());
//		
////		System.out.println("Name: "+ serverCloudlet[8].getName() +
////				"\nPositions X: " + serverCloudlet[8].getCoord().getCoordX() +
////				" Y: "+ serverCloudlet[8].getCoord().getCoordY());
////	
////		System.out.println("TOTAL: "+ count);
//		
//		smartThing[50].getCoord().newCoordinate(smartThing[50],1);
//		System.out.println("Name: "+ smartThing[50].getName() +
//				"\nNEW Positions X: " + smartThing[50].getCoord().getCoordX() +
//				" Y: "+ smartThing[50].getCoord().getCoordY());
//		
//		
//		
//		double distance=Distances.checkDistance(apDevice[1].getCoord(), smartThing[10].getCoord());
//		System.out.println("DISTANCE BW AP AND SMARTTHING: " + distance);
//		
//		int test=DiscoverLocalization.discoverLocal(apDevice[1].getCoord(),smartThing[10].getCoord());
//		System.out.println("direction: " + test);
//
////		test=Distances.theClosest(apDevice, smartThing[30]);
//		
//		
////		System.out.println("The closest ap is: " + test);
////		test=Distances.theClosest(serverCloudlet, smartThing[30]);
////		System.out.println("The closest serverCloudlet is: " + test);
////		test=Distances.theClosest(serverCloudlet, apDevice[10]);
////		System.out.println("The closest serverCloudlet to Ap is: " + test);
////		smartThing[0].getCoord().setCoordX(0);
////		boolean test2=Coordinate.isWithinLimitPosition(smartThing[0].getCoord());
////		System.out.println("the device is within limits: "+ test2);
//
//
//
//		
//		
//	}
//	public static void init(List<MobileDevice> smartThing, Coordinate coordDevices){
//		
//		Random rand = new Random();
//		int coordX,coordY;		
//				
//		for(MobileDevice st: smartThing){//creation of the SmartThing Device
//
////			while(true){
////				coordX = rand.nextInt(MaxAndMin.MAX_X);
////				coordY = rand.nextInt(MaxAndMin.MAX_Y);
////				if(coordDevices.getPositions(coordX, coordY)==null){//verify if it is empty
////					smartThing[i]=new MobileDevice("SmartThing" + Integer.toString(i),
////							coordDevices, 
////							coordX, 
////							coordY,
////							i);
////				
////					break;
////				}
////				else{
////					System.out.println("POSITION ISN'T AVAILABLE... "+ coordX+ " " +coordY+" Reallocating..." );
////					
////				}
////			}
////					
////			smartThing[i].setSpeed((int) Math.random()*MaxAndMin.MAX_SPEED);// m/s
////			smartThing[i].setDirection(rand.nextInt(MaxAndMin.MAX_DIRECTION)); 
////		
//		}
//	}
//	
//public static void init(FogDevice serverCloudlet[], Coordinate coordDevices){
//		
//		Random rand = new Random();
//		int coordX,coordY;		
//				
//		for(int i = 0;i<serverCloudlet.length;i++){//creation of the SmartThing Device
//			while(true){
//				coordX = rand.nextInt(MaxAndMin.MAX_X);
//				coordY = rand.nextInt(MaxAndMin.MAX_Y);
//				if(coordDevices.getPositions(coordX, coordY)==null){//verify if it is empty
//					serverCloudlet[i]=new FogDevice("ServerCloudlet" + Integer.toString(i),
//							coordDevices, 
//							coordX, 
//							coordY,
//							i);
//				
//					break;
//				}
//				else{
//					System.out.println("POSITION ISN'T AVAILABLE... "+ coordX+ " " +coordY+" Reallocating..." );
//					
//				}
//			}
//			
//		}
//	}
//	
//	public static void init(ApDevice accessPoint[],Coordinate coordDevices){
//		
//		Random rand = new Random();
//		int coordX,coordY,choose;		
//				
//		for(int i = 0;i<accessPoint.length;i++){//creation of the SmartThing Device
//			while(true){
//				coordX = rand.nextInt(MaxAndMin.MAX_X);
//				coordY = rand.nextInt(MaxAndMin.MAX_Y);
//				if(coordDevices.getPositions(coordX, coordY)==null){//verify if it is empty
//					accessPoint[i]=new ApDevice("AccessPoint" + Integer.toString(i),
//							coordDevices, 
//							coordX, 
//							coordY,
//							i);
//								break;
//				}
//				else
//					System.out.println("POSITION ISN'T AVAILABLE... "+ coordX+ " " +coordY+" Reallocating..." );		
//			}
//		}
//	
//	}
//
//
//
//}
//
