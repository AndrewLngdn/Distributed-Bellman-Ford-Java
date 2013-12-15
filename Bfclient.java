import java.io.*;
import java.net.*;
import java.util.Hashtable;
import java.util.ArrayList;

class Bfclient {
	static int localport;
	static int timeout; // seconds
	static boolean execute;
	static DatagramSocket localSock;
	private BufferedReader input = null;
	static Hashtable<InetAddress, String> distanceVector = 
		new Hashtable<InetAddress, String>();
	static Hashtable<InetAddress, Hashtable<InetAddress, String>> neighborsDV =
		new Hashtable<InetAddress, Hashtable<InetAddress, String>>();

	public static void main(String[] args){
		execute = true;

		interpretArgs(args);
		setupSockets();
		listenToSocket();
		dealWithTimeouts();
		listenForCommands();
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

	@SuppressWarnings("unchecked") // java doesn't trust me deserializing
								   // distanceVectors from my other class
	public static Hashtable<InetAddress, String> receiveUpdate() {
		byte[] buffer = new byte[4096];
		DatagramPacket rpack = new DatagramPacket(buffer, buffer.length);
		
		System.out.println("waiting to recieve");
		try {
			localSock.receive(rpack);
			System.out.println("Received RT from ");
		} catch (IOException e){
			e.printStackTrace();
		}

		byte[] packet = new byte[rpack.getLength()];
		System.arraycopy(rpack.getData(), 0, packet, 0, rpack.getLength());

		Hashtable<InetAddress, String> rt = null;
		try {
			rt = (Hashtable<InetAddress, String>)Serializer.deserialize(packet);
		} catch (Exception e){
			e.printStackTrace();
		}
		return rt;
	}

	public static void listenToSocket(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				while(execute){

					Hashtable<InetAddress, String> updatedRT = receiveUpdate();

					System.out.println();
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
				int count = 0;
				while(execute){
					// if timeout,
					// send copy of distance vector to neighbors
				}
			}
		});
		t.start();
	}

	public static void sendPacket(Hashtable<String, String> neighbor, byte[] data){
		InetAddress remote_addr = null;
		try {
			remote_addr = InetAddress.getByName(neighbor.get("ip"));
		} catch (UnknownHostException e){
			e.printStackTrace();
		}

		int port = Integer.parseInt(neighbor.get("port"));
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
			// Hashtable<String, String> neighbor 
				// = new Hashtable<String, String>();
			InetAddress neighborIP = null;
			try {
				neighborIP = InetAddress.getByName(args[i]);
			} catch (UnknownHostException e){
				e.printStackTrace();
			}

			distanceVector.put(neighborIP, args[i+1]+"-"+args[i+2]+"-"+neighborIP+":"+args[i+1]);
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
			// sendPacket(neighborsInfo.get(0), "test 1 2 3".getBytes());

		} else if (tokens.length == 2 && tokens[0].equals("LINKUP")){
			System.out.println("LINKUP " + tokens[1]);

		} else if (tokens[0].equals("SHOWRT")){
			printRT();

		} else if (tokens[0].equals("CLOSE")){
			System.out.println("closing!");
			execute = false;

		} else {
			System.out.println("Incorrect command, use one of the following:");
			System.out.println("LINKDOWN [ip]");
			System.out.println("LINKUP [ip]");
			System.out.println("SHOWRT");
			System.out.println("CLOSE");
		}
	}

	public static void printRT(){
		System.out.println("<---Route Table--->");
		for (InetAddress ip : distanceVector.keySet()){
			String[] port_cost_link = distanceVector.get(ip).split("-");
			System.out.print("Destination = " + ip+ ":" +port_cost_link[0] + ", ");
			System.out.print("Cost = " + port_cost_link[1] + ", ");
			System.out.println("Link = " + port_cost_link[2]);
		}
	}
}
