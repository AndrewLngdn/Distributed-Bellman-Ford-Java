import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;



class Bfclient {
	static int localport;
	static int timeout; // seconds
	static boolean execute; // for thread loops
	static long internalTimeoutTimer; // for neighbor times
	static DatagramSocket localSock;	// socket 
	private BufferedReader input = null;
	public static Node selfNode;
	public static Integer lockToken = 1;

	public static ArrayList<InetSocketAddress> destinations = new ArrayList<InetSocketAddress>();
	public static ConcurrentHashMap<InetSocketAddress, Node> neighbors = new ConcurrentHashMap<InetSocketAddress, Node>();
	public static ConcurrentHashMap<InetSocketAddress, Node> distanceVector = new ConcurrentHashMap<InetSocketAddress, Node>();
	public static ConcurrentHashMap<Node, ConcurrentHashMap<InetSocketAddress, Node>> neighborsDVs = 
		new ConcurrentHashMap<Node, ConcurrentHashMap<InetSocketAddress, Node>>();


	public static void main(String[] args){
		execute = true;
		InetSocketAddress a = new InetSocketAddress("127.0.0.1", 6666);
		InetSocketAddress b = new InetSocketAddress("/127.0.0.1", 6666);
		// p(a.equals(b));
		// p(a.equals(b));
		// p(a.equals(b));
		// p(new InetSocketAddress("127.0.0.1", 6666));


		interpretArgs(args);
		setupSockets();
		listenToSocket();
		// dealWithTimeouts();
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

		InetSocketAddress myAddress = new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0]));
		selfNode = new Node(myAddress, 0, myAddress);

		// fillout neighbor info
		// add neighbors to neighbors list, distance vector, and 


		synchronized (lockToken){
			distanceVector.put(myAddress, selfNode);
			for (int i = 2; i < args.length; i += 3){
				if (args[i].equals("localhost")){
					args[i] = "127.0.0.1";
				}
				int port = Integer.parseInt(args[i+1]);
				InetSocketAddress neighborAddr = new InetSocketAddress(args[i], port);

				Node neighbor = new Node(neighborAddr, Double.parseDouble(args[i+2]), neighborAddr);
				// watch out, i think these are both the same object
				neighbors.put(neighborAddr, neighbor);
				distanceVector.put(neighborAddr, neighbor);
				destinations.add(neighborAddr);
			}
		}
	}


	// Sees if a neighbor has timed out
	public static void checkForNeighborTimeout()
	{
		// synchronized(neighborsInfo) {
		// 	Iterator<Hashtable<String, String>> it = neighborsInfo.iterator();

		// 	while(it.hasNext()){
		// 		Hashtable<String, String> neighbor = it.next();
		// 		long neighborTimeout = Long.parseLong(neighbor.get("timeout"));

		// 		int port = Integer.parseInt(neighbor.get("port"));
		// 		String ip = neighbor.get("ip");
		// 		if (ip.split("/").length == 1){
		// 			ip = ip.split("/")[0];
		// 		} else {
		// 			ip = ip.split("/")[1];
		// 		}
		// 		InetAddress addr = null;

		// 		try {
		// 			addr = InetAddress.getByName(ip);
		// 		}catch (UnknownHostException e){
		// 			e.printStackTrace();
		// 		}

		// 		InetSocketAddress n = new InetSocketAddress(addr, port);

		// 		if (distanceVector.get(n) == null){
		// 			return;
		// 		}
		// 		String[] port_cost_link = distanceVector.get(n).split("-");

		// 		if (System.currentTimeMillis() - neighborTimeout > 3*timeout*1000){
		// 			distanceVector.put(n, n.getPort()+ "-" + Double.MAX_VALUE + "-" + n);
		// 		}
		// 	}
		// }
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
				updateNeighbors();
				while(execute){
					receiveUpdate();
					updateRT();
				}
			}
		});
		t.start();
	}

	// BelmanFord Algorithm
	synchronized public static void updateRT(){
		// for each dest that i know about,
		// look through all the neighbors
		// my cost to them is minv{c(x,v) + Dv(y)}
		// and the link is through the minimum one

		for (InetSocketAddress dAddr : destinations){
			double minCost = Double.MAX_VALUE;
			Node link;

			if (dAddr.equals(selfNode.addr)){
				p("skipping distance to self");
				continue;
			}

			for (Node neighbor : neighbors.values()){
				double costToNeighbor = neighbor.cost;
				ConcurrentHashMap<InetSocketAddress, Node> neighborsDV = neighbor.dv;
				Node destination = neighborsDV.get(dAddr);
				p("trying to get to " + dAddr + " through " + neighbor);

				if (destination == null){
					p("neighbor's dv didn't contain my destination. continuing");
					continue;
				}
				double costToDestination = destination.cost;
				// p("cost to " + destination + " = " + costToDestination);
				// p("total cost == " + (costToNeighbor + costToDestination));
				

			}
		}
	}

	// Thread for timeouts
	public static void dealWithTimeouts(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				// updateNeighbors();

				internalTimeoutTimer = System.currentTimeMillis();
				

				while(execute){

					// checkForNeighborTimeout();

					if (internalTimeoutOccured()){
						// updateNeighbors();
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
	synchronized public static void updateNeighbors(){
		String dvString = stringifyDV();
		for (Node n : neighbors.values()){
			sendPacket(n.addr, dvString);
		}
	}

	public static String stringifyDV(){
		String dvString = "";
		synchronized (lockToken){
			for (Node a : distanceVector.values()){
				dvString += a.toShortString() + "\n";
			}
		}
		return dvString;
	}



	// recieve update and deserialze DV

	public static void receiveUpdate() {
		byte[] buffer = new byte[4096];
		DatagramPacket rpack = new DatagramPacket(buffer, buffer.length);
		
		InetSocketAddress senderAddr = null;
		try {
			localSock.receive(rpack);		
			senderAddr = (InetSocketAddress)rpack.getSocketAddress();
			p("Received RT from " + senderAddr);

		} catch (IOException e){
			e.printStackTrace();
		}

		byte[] packet = new byte[rpack.getLength()];
		System.arraycopy(rpack.getData(), 0, packet, 0, rpack.getLength());

		String[] neighborDVlines = (new String(packet)).split("\n");

		InetSocketAddress cleanedAddr = 
			new InetSocketAddress(getPureIP(senderAddr), senderAddr.getPort());

		ConcurrentHashMap<InetSocketAddress, Node> neighborDV = 
			new ConcurrentHashMap<InetSocketAddress, Node>();

		synchronized (lockToken) {
			double cost = 0.0;

			for (String s : neighborDVlines){

				Node dvEntry = new Node(s);
				// p("received DV " + dvEntry);
				neighborDV.put(dvEntry.addr, dvEntry);

				if (dvEntry.equals(selfNode)){
					cost = dvEntry.cost;
				}

				if (!destinations.contains(dvEntry.addr)){
					destinations.add(dvEntry.addr);
				}
			}
			Node sendingNode;

			if ((sendingNode = neighbors.get(cleanedAddr)) == null){
				sendingNode = new Node(cleanedAddr, cost, cleanedAddr);
				p("adding node to neighbors");
				neighbors.put(cleanedAddr, sendingNode);
			}

			sendingNode.dv = neighborDV;
			distanceVector.put(cleanedAddr, sendingNode);

			// p("this RT should be the same as the neighbors");
			// sendingNode.printRT();

		}

	}


	public static String getPureIP(InetSocketAddress p){
		return p.toString().split("/")[1].split(":")[0];
	}


	// add neighbor to neighborInfo
	public static void addNeighborToInfo(Hashtable<String, String> neighbor){
		// boolean containsFlag = false;
		// for (Hashtable<String, String> n:neighborsInfo){
		// 	if (n.get("inet").equals(neighbor.get("inet"))){
		// 		containsFlag = true;
		// 	}
		// }
		// if (containsFlag == false){
		// 	p("ignoring duplicate");
		// 	neighborsInfo.add(neighbor);
		// }

	}


	// update neighbor timeout if we get an RT from them
	public static void updateNeighborTimout(InetSocketAddress n){
		// synchronized(neighborsInfo){
		// 	Iterator<Hashtable<String, String>> it = neighborsInfo.iterator();
		// 	while (it.hasNext()){
		// 		Hashtable<String, String> neighbor = it.next();
		// 		int port = Integer.parseInt(neighbor.get("port"));
		// 		String ip = "";
		// 		if (neighbor.get("ip").split("/").length == 1){
		// 			ip = neighbor.get("ip").split("/")[0];
		// 		} else {
		// 			ip = neighbor.get("ip").split("/")[1];
		// 		}
		// 		InetSocketAddress nSock = new InetSocketAddress(ip, port);
		// 		if (n.equals(nSock)){
		// 			p("updating timeout for " + n);
		// 			neighbor.put("timeout", String.valueOf(System.currentTimeMillis()));
		// 		}
		// 	}
		// }
	}

	// Send a packet
	public static void sendPacket(InetSocketAddress remote_addr, String s){
		byte[] data = s.getBytes();
		try {
			DatagramPacket spacket = new DatagramPacket(data, data.length, remote_addr);
			localSock.send(spacket);

		} catch (Exception e){
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


		} else if (tokens.length == 2 && tokens[0].equals("LINKUP")){
			

		} else if (tokens[0].equals("SHOWRT")){
			printRT();

		} else if (tokens[0].equals("CLOSE")){
			System.out.println("closing!");
			execute = false;

		} else if (tokens[0].equals("UP")){
			p("here");
			updateNeighbors();

		} else {
			System.out.println("Incorrect command, use one of the following:");
			System.out.println("LINKDOWN [ip:port]");
			System.out.println("LINKUP [ip:port]");
			System.out.println("SHOWRT");
			System.out.println("CLOSE");
		}
	}

	// Print our routeTable
	public static void printRT(){
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Date date = new Date();

		System.out.println("<---Route Table--->");
		System.out.println("Time: " + dateFormat.format(date) + " Distance vector list is:");

		synchronized(distanceVector){
			for (Node n : distanceVector.values()){
				System.out.println(n);
			}	
		}
	}

	public static void p(Object o){
		System.out.println(o);
	}
}
