import java.io.*;
import java.net.*;
import java.util.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;



class Bfclient {
	static int localport;
	static int timeout; // seconds
	static boolean execute; // for thread loops
	static long internalTimeoutTimer; // for neighbor times
	static DatagramSocket localSock;	// socket 
	private BufferedReader input = null;
	static Hashtable<InetSocketAddress, String> distanceVector =  // this is the node's distance vector
		new Hashtable<InetSocketAddress, String>();
	static ArrayList<InetSocketAddress> destinations =  // destinations for BF algo
		new ArrayList<InetSocketAddress>();
	static Hashtable<InetSocketAddress, Double> neighborCost = // list of original costs to neighbors
		new Hashtable<InetSocketAddress, Double>();
	static Hashtable<InetSocketAddress, Boolean> neighborLinkDown = // linkdown keeper-tracker
		new Hashtable<InetSocketAddress, Boolean>();

	//holds the DV of our neighbors
	static Hashtable<InetSocketAddress, Hashtable<InetSocketAddress, String>> neighborsDV =
		new Hashtable<InetSocketAddress, Hashtable<InetSocketAddress, String>>();

    static ArrayList<Hashtable<String, String>> neighborsInfo 
            = new ArrayList<Hashtable<String, String>>();

	public static void main(String[] args){
		execute = true;

		interpretArgs(args);
		setupSockets();
		listenToSocket();
		dealWithTimeouts();
		listenForCommands();
	}

	// adds neighbors to Data Structures above
	public static void interpretArgs(String[] args){
		if (args.length < 2 || (args.length-2)%3 != 0){
			System.out.println("Incorrect arguments");
			System.out.println("java Bfclient localport timeout [ipaddress1 port1 weight1 ...]");
			System.exit(1);
		} else { 
			localport = Integer.parseInt(args[0]);
			timeout = Integer.parseInt(args[1]);
			System.out.println("localport: " + localport);
			System.out.println("timeout: " + timeout);
		}

		// fillout neighbor info
		for (int i = 2; i < args.length; i += 3){
			Hashtable<String, String> neighbor 
				= new Hashtable<String, String>();

			neighbor.put("ip", args[i]);
			neighbor.put("port", args[i+1]);
			neighbor.put("timeout", String.valueOf(System.currentTimeMillis()));
			neighbor.put("initCost", args[i+2]);

			InetAddress neighborIP = null;
			try {
				neighborIP = InetAddress.getByName(args[i]);
			} catch (UnknownHostException e){
				e.printStackTrace();
			}
			InetSocketAddress neighborAddr = 
				new InetSocketAddress(neighborIP, Integer.parseInt(args[i+1]));
			neighborCost.put(neighborAddr, Double.parseDouble(args[i+2]));

			neighbor.put("inet", neighborAddr.toString());
			synchronized(neighborsInfo){
				neighborsInfo.add(neighbor);

			}
			Hashtable<InetSocketAddress, String> temp = new Hashtable<InetSocketAddress, String>();

			synchronized(neighborsDV){
				neighborsDV.put(neighborAddr, temp);
			}

			distanceVector.put(neighborAddr, args[i+1]+"-"+args[i+2]+"-"+neighborIP+":"+args[i+1]);

		}
		InetSocketAddress thisHost = new InetSocketAddress("localhost", localport);
		distanceVector.put(thisHost, localport+"-"+0+"-"+thisHost);
	}


	// Sees if a neighbor has timed out
	public static void checkForNeighborTimeout()
	{
		synchronized(neighborsInfo) {
			Iterator<Hashtable<String, String>> it = neighborsInfo.iterator();

			while(it.hasNext()){
				Hashtable<String, String> neighbor = it.next();
				long neighborTimeout = Long.parseLong(neighbor.get("timeout"));

				int port = Integer.parseInt(neighbor.get("port"));
				String ip = neighbor.get("ip");
				if (ip.split("/").length == 1){
					ip = ip.split("/")[0];
				} else {
					ip = ip.split("/")[1];
				}
				InetAddress addr = null;

				try {
					addr = InetAddress.getByName(ip);
				}catch (UnknownHostException e){
					e.printStackTrace();
				}

				InetSocketAddress n = new InetSocketAddress(addr, port);

				if (distanceVector.get(n) == null){
					return;
				}
				String[] port_cost_link = distanceVector.get(n).split("-");

				if (System.currentTimeMillis() - neighborTimeout > 3*timeout*1000){
					distanceVector.put(n, n.getPort()+ "-" + Double.MAX_VALUE + "-" + n);
				}
			}
		}
	}
	static int count = -1;

	public static void setupSockets(){
		//listening socket
		try {
			localSock = new DatagramSocket(localport);
		} catch (SocketException e){
			e.printStackTrace();
			System.out.println("couldn't bind to port, choose again");
			execute = false;
			System.exit(1);
		}
	}

	public static void listenToSocket(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				while(execute){

					Hashtable<InetSocketAddress, String> neighborRT = receiveUpdate();
					updateRT();
				}
			}
		});
		t.start();
	}

	// BelmanFord Algorithm
	public static void updateRT(){
		// for each dest that i know about,
		// look through all the neighbors
		// my cost to them is minv{c(x,v) + Dv(y)}
		// and the link is through the minimum one
		synchronized(neighborsDV){

			ArrayList<InetSocketAddress> neighborsAddrs = new ArrayList<InetSocketAddress>();
			Set<InetSocketAddress> nDVaddrs = neighborsDV.keySet();

			for (InetSocketAddress neighbor : nDVaddrs){
				String n = neighbor.toString();
				neighborsAddrs.add(neighbor);
			}


			for (InetSocketAddress neighbor : neighborsAddrs){
				Set<InetSocketAddress> neighborsNeighbors = distanceVector.keySet();
				for (InetSocketAddress d : neighborsNeighbors)
				if (!destinations.contains(d)){
					destinations.add(d);
				}
			}

			if (destinations.size() == 1)
				return;

			Iterator<InetSocketAddress> it = neighborsAddrs.iterator();

			for (InetSocketAddress dest : destinations){
				double mincost = Double.MAX_VALUE;
				InetSocketAddress link = dest;
				String dport = "";

					
				InetSocketAddress localISA = new InetSocketAddress("localhost", localport);

				if (dest.equals(localISA)){

					continue;
				} else {
					p("CHECKING COST TO " + dest);
				}
				for (InetSocketAddress neighbor : neighborsAddrs){
					p("trying to get " + dest + "through " + neighbor);
					if (distanceVector.get(neighbor) == null){
						p("neighbor isn't in distnace vector");
						continue;
					}
					Hashtable<InetSocketAddress, String> n = neighborsDV.get(neighbor);
					String[] port_cost_link = distanceVector.get(neighbor).split("-");
					double RTcost = Double.parseDouble(port_cost_link[1]);
					if (RTcost == Double.MAX_VALUE){
						continue;
						// nCost = Double.MAX_VALUE;
					}
					double nCost = neighborCost.get(neighbor);
					p("nCost to " + neighbor  + " is " + nCost);


					if (n == null) {
						p("breaking, neighbor is null");
						continue;
					}

					String d_info = n.get(dest);
					p("DESTINATION IS " + d_info + " SHOULD BE " + dest);
					if (d_info == null){
						p("breaking, d_info is null");
						continue;
					}
					String[] dest_port_cost_link = d_info.split("-");
					dport = dest_port_cost_link[0];
					p("cost through neighbor " + neighbor + " is " +dest_port_cost_link[1]);
					double vCost = Double.parseDouble(dest_port_cost_link[1]);

					if (nCost + vCost < mincost){
						mincost = nCost+vCost;
						link = neighbor;
						p("settingminCost to " + mincost);
					}
				}
				p("adding to DV for dest : " + dest + dport + "-" + mincost + "-" + link);
				distanceVector.put(dest, dport + "-" + mincost + "-" + link);
			}
		}
	}

	// Thread for timeouts
	public static void dealWithTimeouts(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				updateNeighbors();

				internalTimeoutTimer = System.currentTimeMillis();
				

				while(execute){

					checkForNeighborTimeout();

					if (internalTimeoutOccured()){
						updateNeighbors();
					}
				}
			}
		});
		t.start();
	}

	public static boolean internalTimeoutOccured(){
		
		if (System.currentTimeMillis() - internalTimeoutTimer > timeout*1000){
			// System.out.println("internal timeout");
			internalTimeoutTimer = System.currentTimeMillis();
			return true;
		} else {
			return false;
		}
	}

	// send RT to neighbors 
	public static void updateNeighbors(){
		synchronized(neighborsDV){

			Set<InetSocketAddress> neighbors = neighborsDV.keySet();

			for (InetSocketAddress neighborAddr : neighbors){


				InetAddress ip = neighborAddr.getAddress();

				int port = neighborAddr.getPort();
				String[] sCost = distanceVector.get(neighborAddr).split("-");
				double cost = Double.parseDouble(sCost[1]);

				byte[] bytes = null;
				try {
					bytes = Serializer.serialize(distanceVector);
				} catch (IOException e){
					e.printStackTrace();
				}
				sendPacket(ip, port, bytes);
			}
		}
	}


	// recieve update and deserialze DV
	@SuppressWarnings("unchecked") // java doesn't trust me deserializing
								   // distanceVectors from my other class
	public static Hashtable<InetSocketAddress, String> receiveUpdate() {
		byte[] buffer = new byte[4096];
		DatagramPacket rpack = new DatagramPacket(buffer, buffer.length);
		
		InetSocketAddress senderAddr = null;
		try {

			localSock.receive(rpack);
			
			senderAddr = (InetSocketAddress)rpack.getSocketAddress();
			p("Received RT from " + senderAddr);
			if (neighborLinkDown.containsKey(senderAddr)){
				return null;
			}
			updateNeighborTimout(senderAddr);

		} catch (IOException e){
			e.printStackTrace();
		}

		byte[] packet = new byte[rpack.getLength()];
		System.arraycopy(rpack.getData(), 0, packet, 0, rpack.getLength());

		Hashtable<InetSocketAddress, String> rt = null;
		try {
			rt = (Hashtable<InetSocketAddress, String>)Serializer.deserialize(packet);
		} catch (Exception e){
			e.printStackTrace();
		}

		InetSocketAddress localISA = new InetSocketAddress("localhost", localport);


		Set<InetSocketAddress> dests = rt.keySet();
		for (InetSocketAddress address : dests){
			if (address.equals(localISA)){
				p("localISA " + localISA);
				// p("");

				String cost = rt.get(localISA).split("-")[1];
				if (!distanceVector.contains(senderAddr)){
					p("SENDER ADDRESS key : " + senderAddr);

					updateNeighborTimout(senderAddr);
					distanceVector.put(senderAddr,rpack.getPort()+"-"+cost+"-"+senderAddr);
					
				} else {
					p("didn't add found neighbor");
				}
				Hashtable<String, String> neighbor 
					= new Hashtable<String, String>();
				neighbor.put("ip", senderAddr.getAddress().toString());
				neighbor.put("port", Integer.toString(rpack.getPort()));
				neighbor.put("timeout", String.valueOf(System.currentTimeMillis()));
				neighbor.put("initCost", cost.toString());
				neighbor.put("inet", senderAddr.toString());
				synchronized(neighborsInfo){
					Set<InetSocketAddress> addresses = neighborCost.keySet();
					Iterator<InetSocketAddress> it = addresses.iterator();
					boolean containsFlag = false;
					while (it.hasNext()){
						InetSocketAddress nei = it.next();
						if (nei.equals(senderAddr)){
							containsFlag = true;
							// System.exit(1);
						}
					}
					if (containsFlag == false){
						neighborCost.put(senderAddr, Double.parseDouble(cost));
					} 
					addNeighborToInfo(neighbor);
				}
			}
			p("adding destination " + address);
			if (!destinations.contains(address)){
				destinations.add(address);

			}			
		}
		synchronized(neighborsDV){
			p("putting " + senderAddr + "in neighborsDV ");

			neighborsDV.put(senderAddr, rt);
			return rt;

		}

	}

	// add neighbor to neighborInfo
	public static void addNeighborToInfo(Hashtable<String, String> neighbor){
		boolean containsFlag = false;
		for (Hashtable<String, String> n:neighborsInfo){
			if (n.get("inet").equals(neighbor.get("inet"))){
				containsFlag = true;
			}
		}
		if (containsFlag == false){
			p("ignoring duplicate");
			neighborsInfo.add(neighbor);
		}

	}
	static InetSocketAddress temp;


	// update neighbor timeout if we get an RT from them
	public static void updateNeighborTimout(InetSocketAddress n){
		synchronized(neighborsInfo){
			Iterator<Hashtable<String, String>> it = neighborsInfo.iterator();
			while (it.hasNext()){
				Hashtable<String, String> neighbor = it.next();
				int port = Integer.parseInt(neighbor.get("port"));
				String ip = "";
				if (neighbor.get("ip").split("/").length == 1){
					ip = neighbor.get("ip").split("/")[0];
				} else {
					ip = neighbor.get("ip").split("/")[1];
				}
				InetSocketAddress nSock = new InetSocketAddress(ip, port);
				if (n.equals(nSock)){
					p("updating timeout for " + n);
					neighbor.put("timeout", String.valueOf(System.currentTimeMillis()));
				}
			}
		}
	}

	// Send a packet
	public static void sendPacket(InetAddress remote_addr, int port, byte[] data){

		DatagramPacket spacket = new DatagramPacket(data, data.length,
													remote_addr, port);
		try {
			localSock.send(spacket);
		} catch (IOException e){
			e.printStackTrace();
		}
	}

	// Look for commands 
	public static void listenForCommands(){
		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
		execute = true;
		System.out.println(">----Commands----<");
		System.out.println("LINKDOWN [ip]");
		System.out.println("LINKUP [ip]");
		System.out.println("SHOWRT");
		System.out.println("CLOSE");
		System.out.println(">-----------------<");

		while(execute){
			try {
				// synchronized(distanceVector){
					interpretCommand(stdIn.readLine());
				// }
			} catch (IOException e){
				System.err.println(e);
			}
		}

		try {
			stdIn.close();
		} catch (IOException e){
			System.err.println(e);
		}
		System.exit(0);
	}

	// Interpret input
	private static void interpretCommand(String command){
		String[] tokens = command.split(" ");
		if (tokens.length == 2 && tokens[0].equals("LINKDOWN")){
			String[] ip_port = tokens[1].split(":");
			InetSocketAddress addr = new InetSocketAddress(ip_port[0], 
										Integer.parseInt(ip_port[1])
										);
			neighborLinkDown.put(addr, true);
			System.out.println("Taking down link to " + addr);

			if (distanceVector.get(addr) == null){
				System.out.println("incorrect ip:port");
				return;
			}
			String[] port_cost_link = distanceVector.get(addr).split("-");
			String updatedDistance = ip_port[1]+"-"+Double.MAX_VALUE+"-"+port_cost_link[2];
			distanceVector.put(addr, updatedDistance);
			System.out.println("New RT");
			printRT(distanceVector);

		} else if (tokens.length == 2 && tokens[0].equals("LINKUP")){
			String[] ip_port = tokens[1].split(":");
			InetSocketAddress addr = new InetSocketAddress(ip_port[0], 
										Integer.parseInt(ip_port[1])
										);
			neighborLinkDown.put(addr, false);
			System.out.println("Restoring link to " + addr);
			if (distanceVector.get(addr) == null){
				System.out.println("incorrect ip:port");
				return;
			}

			double oldCost = neighborCost.get(addr);
			System.out.println(oldCost);
			// System.exit(0);
			String[] port_cost_link = distanceVector.get(addr).split("-");
			String updatedDistance = ip_port[1]+"-"+oldCost+"-"+port_cost_link[2];
			System.out.println(distanceVector.remove(addr));
			System.out.println(updatedDistance);
			distanceVector.put(addr, updatedDistance);
			System.out.println("New RT");
			printRT(distanceVector);

		} else if (tokens[0].equals("SHOWRT")){
			printRT(distanceVector);

		} else if (tokens[0].equals("CLOSE")){
			System.out.println("closing!");
			execute = false;

		} else {
			System.out.println("Incorrect command, use one of the following:");
			System.out.println("LINKDOWN [ip:port]");
			System.out.println("LINKUP [ip:port]");
			System.out.println("SHOWRT");
			System.out.println("CLOSE");
		}
	}

	// Print our routeTable
	public static void printRT(Hashtable<InetSocketAddress, String> rt){
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Date date = new Date();

		System.out.println("<---Route Table--->");
		System.out.println("Time: " + dateFormat.format(date) + " Distance vector list is:");
		for (InetSocketAddress addr : rt.keySet()){
			String[] port_cost_link = rt.get(addr).split("-");
			System.out.print("Destination = " + addr + ", ");
			System.out.print("Cost = " + port_cost_link[1] + ", ");
			System.out.println("Link = " + port_cost_link[2]);
		}
	}

	public static void p(Object o){
		// System.out.println(o);
	}
}
