import java.io.*;
import java.net.*;
import java.util.*;

import java.text.DateFormat;
import java.text.SimpleDateFormat;



class Bfclient {
	static int localport;
	static int timeout; // seconds
	static boolean execute;
	static long internalTimeoutTimer;
	static DatagramSocket localSock;
	private BufferedReader input = null;
	static Hashtable<InetSocketAddress, String> distanceVector = 
		new Hashtable<InetSocketAddress, String>();
	static ArrayList<InetSocketAddress> destinations = 
		new ArrayList<InetSocketAddress>();


	static Hashtable<InetSocketAddress, Hashtable<InetSocketAddress, String>> neighborsDV =
		new Hashtable<InetSocketAddress, Hashtable<InetSocketAddress, String>>();

    static Vector<Hashtable<String, String>> neighborsInfo 
            = new Vector<Hashtable<String, String>>();

	public static void main(String[] args){
		execute = true;

		interpretArgs(args);
		setupSockets();
		listenToSocket();
		dealWithTimeouts();
		listenForCommands();
	}

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

			neighbor.put("inet", neighborAddr.toString());
			neighborsInfo.add(neighbor);
			distanceVector.put(neighborAddr, args[i+1]+"-"+args[i+2]+"-"+neighborIP+":"+args[i+1]);
		}
		InetSocketAddress thisHost = new InetSocketAddress("localhost", localport);
		distanceVector.put(thisHost, localport+"-"+0+"-"+thisHost);
	}

	public static void resetTimer(){
		internalTimeoutTimer = System.currentTimeMillis();
	}

	public static Vector<Hashtable<String, String>> checkForNeighborTimeout(
			Vector<Hashtable<String, String>> neighborsInfo)
	{
		Iterator<Hashtable<String, String>> it = neighborsInfo.iterator();

		while(it.hasNext()){
			Hashtable<String, String> neighbor = it.next();
			long neighborTimeout = Long.parseLong(neighbor.get("timeout"));

			if (System.currentTimeMillis() - neighborTimeout > 3*timeout*1000){

				int port = Integer.parseInt(neighbor.get("port"));
				InetSocketAddress n = new InetSocketAddress(neighbor.get("ip"), port);
				String[] port_cost_link = distanceVector.get(n).split("-");
				distanceVector.put(n, port+ "-" + Double.MAX_VALUE + "-" + port_cost_link[2]);

				p("neighbor Timeout " + n);
			}
		}
		return neighborsInfo;
	}

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
					// System.out.println("received update from: " );
					//updateTimer();
					printRT(distanceVector);
					updateRT();
					// update neighbors
				}
			}
		});
		t.start();
	}

	public static void updateRT(){
		// Set<InetSocketAddress> destinations = distanceVector.keySet();
		// Iterator<Hashtable<InetSocketAddress, String>> it = distanceVector.iterator();
		// for each dest that i know about,
		// look through all the neighbors
		// my cost to them is minv{c(x,v) + Dv(y)}
		// and the link is through the minimum one
		ArrayList<InetSocketAddress> neighborsAddrs = new ArrayList<InetSocketAddress>();

		for (Hashtable<String, String> neighbor : neighborsInfo){
			int port = Integer.parseInt(neighbor.get("port"));
			InetSocketAddress n = new InetSocketAddress(neighbor.get("ip"), port);
			neighborsAddrs.add(n);
		}

		// ArrayList<InetSocketAddress> destinations = new ArrayList<InetSocketAddress>();

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
			// p(mincost);
			InetSocketAddress link = dest;
			String dport = "";

				
				InetSocketAddress localISA = new InetSocketAddress("localhost", localport);
				// p(dest.equals(localISA) + "!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				// p(localISA);
				// p(dest);
				// p("!!!!!!!!!!!!!!");

			if (dest.equals(localISA)){

				continue;
			} else {
				p("CHECKING COST TO " + dest);
			}
			for (InetSocketAddress neighbor : neighborsAddrs){
				p("trying to get " + neighbor);
				if (distanceVector.get(neighbor) == null)
					continue;
				String[] port_cost_link = distanceVector.get(neighbor).split("-");

				double nCost = Double.parseDouble(port_cost_link[1]);
				p("nCost to neighbor " + nCost);
				Hashtable<InetSocketAddress, String> n = neighborsDV.get(neighbor);

				if (n == null) {
					p("breaking, neighbor is null");
					continue;
				}

				String d_info = n.get(dest);
				if (d_info == null){
					p("breaking");
					continue;
				}
				String[] dest_port_cost_link = d_info.split("-");
				dport = dest_port_cost_link[0];
				double vCost = Double.parseDouble(dest_port_cost_link[1]);
				if (nCost + vCost < mincost){
					mincost = nCost+vCost;
					link = neighbor;
					p("settingminCost to " + mincost);
				}
			}
			p("setting distance to dest to " + mincost);
			p("dest: " + link);
			distanceVector.put(dest, dport + "-" + mincost + "-" + link);
		}
	}

	public static void dealWithTimeouts(){
		Thread t = new Thread(new Runnable(){
			public void run(){

				resetTimer();

				while(execute){

					neighborsInfo = checkForNeighborTimeout(neighborsInfo);

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
			resetTimer();
			return true;
		} else {
			return false;
		}
	}

	public static void updateNeighbors(){
		Set<Hashtable<InetSocketAddress, String>> neighbors = neighborsDV.keySet();
		for (InetSocketAddress neighborAddr : neighbors){
			// String ip_string = neighborAddr.getAddress();

			InetAddress ip = neighborAddr.getAddress();
			// try {
				// 
			// } catch (UnknownHostException e){
				// e.printStackTrace();
			// }

			int port = neighborAddr.getPort();

			// System.out.println("sending DV to " + ip + ":" + port);

			byte[] bytes = null;
			try {
				bytes = Serializer.serialize(distanceVector);
			} catch (IOException e){
				e.printStackTrace();
			}
			sendPacket(ip, port, bytes);
		}
	}

	@SuppressWarnings("unchecked") // java doesn't trust me deserializing
								   // distanceVectors from my other class
	public static Hashtable<InetSocketAddress, String> receiveUpdate() {
		byte[] buffer = new byte[4096];
		DatagramPacket rpack = new DatagramPacket(buffer, buffer.length);
		
		// System.out.println("waiting to recieve");
		InetSocketAddress senderAddr = null;
		try {
			localSock.receive(rpack);
			
			senderAddr = (InetSocketAddress)rpack.getSocketAddress();
			// System.out.println("Received RT from " + senderAddr);
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
				p("Neighbor attached!!!!!! ");
				String cost = rt.get(localISA).split("-")[1];
				distanceVector.put(senderAddr,rpack.getPort()+"-"+cost+"-"+senderAddr);
				Hashtable<String, String> neighbor 
					= new Hashtable<String, String>();
				neighbor.put("ip", senderAddr.getAddress().toString());
				neighbor.put("port", Integer.toString(rpack.getPort()));
				neighbor.put("timeout", String.valueOf(System.currentTimeMillis()));
				neighbor.put("initCost", cost.toString());
				neighbor.put("inet", senderAddr.toString());
				neighborsInfo.add(neighbor);
			}

			if (!destinations.contains(address)){
				destinations.add(address);

			}			
		}


		neighborsDV.put(senderAddr, rt);
		return rt;
	}


	public static void updateNeighborTimout(InetSocketAddress n){
		Iterator<Hashtable<String, String>> it = neighborsInfo.iterator();
		while (it.hasNext()){
			Hashtable<String, String> neighbor = it.next();
			int port = Integer.parseInt(neighbor.get("port"));
			InetSocketAddress nSock = new InetSocketAddress(neighbor.get("ip"), port);
			if (n.equals(nSock)){
				neighbor.put("timeout", String.valueOf(System.currentTimeMillis()));
			}
		}
	}

	public static void sendPacket(InetAddress remote_addr, int port, byte[] data){

		// System.out.println("sending packet to " + remote_addr);
		// System.out.println("at port " + port);

		DatagramPacket spacket = new DatagramPacket(data, data.length,
													remote_addr, port);
		try {
			localSock.send(spacket);
		} catch (IOException e){
			e.printStackTrace();
		}
	}


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
				interpretCommand(stdIn.readLine());
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

	private static void interpretCommand(String command){
		String[] tokens = command.split(" ");
		if (tokens.length == 2 && tokens[0].equals("LINKDOWN")){
			String[] ip_port = tokens[1].split(":");
			InetSocketAddress addr = new InetSocketAddress(ip_port[0], 
										Integer.parseInt(ip_port[1])
										);
			String[] port_cost_link = distanceVector.get(addr).split("-");
			String updatedDistance = ip_port[1]+"-"+Double.MAX_VALUE+"-"+port_cost_link[2];
			distanceVector.put(addr, updatedDistance);

		} else if (tokens.length == 2 && tokens[0].equals("LINKUP")){
			System.out.println("LINKUP " + tokens[1]);

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

	public static void printRT(Hashtable<InetSocketAddress, String> rt){
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Date date = new Date();
		// System.out.println(dateFormat.format(date));

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
		System.out.println(o);
	}
}
