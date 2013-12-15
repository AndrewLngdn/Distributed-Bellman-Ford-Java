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
				// it.remove();

				int port = Integer.parseInt(neighbor.get("port"));
				InetSocketAddress n = new InetSocketAddress(neighbor.get("ip"), port);
				String[] port_cost_link = distanceVector.get(n).split("-");
				distanceVector.put(n, port+"-inf-"+port_cost_link[2]);
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

					Hashtable<InetSocketAddress, String> updatedRT = receiveUpdate();
					System.out.println("received update from: " );
					//updateTimer();
					printRT(updatedRT);
					//listen for updates from neighbor
					// update RT
					// update neighbors
				}
			}
		});
		t.start();
	}

	public static void dealWithTimeouts(){
		Thread t = new Thread(new Runnable(){
			public void run(){

				resetTimer();

				while(execute){

					neighborsInfo = checkForNeighborTimeout(neighborsInfo);

					if (internalTimeoutOccured()){
						System.out.println("we've timedout");
						updateNeighbors();
					}

					// if timeout,
					// update your neighbors
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
		for (Hashtable<String, String> neighbor : neighborsInfo){
			String ip_string = neighbor.get("ip");

			InetAddress ip = null;
			try {
				ip = InetAddress.getByName(ip_string);
			} catch (UnknownHostException e){
				e.printStackTrace();
			}

			int port = Integer.parseInt(neighbor.get("port"));

			System.out.println("sending DV to " + ip + ":" + port);

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
		
		System.out.println("waiting to recieve");
		try {
			localSock.receive(rpack);
			
			InetSocketAddress senderAddr = (InetSocketAddress)rpack.getSocketAddress();
			System.out.println("Received RT from " + senderAddr);
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

		System.out.println("sending packet to " + remote_addr);
		System.out.println("at port " + port);

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
			updateNeighbors();

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
