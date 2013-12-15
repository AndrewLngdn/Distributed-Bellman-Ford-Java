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

	public static void setupSockets(){
		//listening socket
		try {
			localSock = new DatagramSocket(localport);
		} catch (SocketException e){
			e.printStackTrace();
		}
	}

	public static void listenToSocket(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				while(execute){
					byte[] buffer = new byte[4096];
					DatagramPacket rpack = new DatagramPacket(buffer, buffer.length);
					
					System.out.println("waiting to recieve");
					try {
						localSock.receive(rpack);
						System.out.println("Received");
					} catch (IOException e){
						e.printStackTrace();
					}

					byte[] packet = new byte[rpack.getLength()];
					System.arraycopy(rpack.getData(), 0, packet, 0, rpack.getLength());
					System.out.println(new String(packet));
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
			Hashtable<String, String> neighbor 
				= new Hashtable<String, String>();

			neighbor.put("ip", args[i]);
			neighbor.put("port", args[i+1]);
			neighbor.put("weight", args[i+2]);
			neighborsInfo.add(neighbor);
		}

		// System.out.println("first ip: " + neighborsInfo.get(0).get("ip"));
		// System.out.println("first port: " + neighborsInfo.get(0).get("port"));
		// System.out.println("first weight: " + neighborsInfo.get(0).get("weight"));
		// System.out.println("second ip: " + neighborsInfo.get(1).get("ip"));
		// System.out.println("second port: " + neighborsInfo.get(1).get("port"));
		// System.out.println("second weight: " + neighborsInfo.get(1).get("weight"));
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
			sendPacket(neighborsInfo.get(0), "test 1 2 3".getBytes());
		} else 
		if (tokens.length == 2 && tokens[0].equals("LINKUP")){
			System.out.println("LINKUP " + tokens[1]);
		} else 
		if (tokens[0].equals("SHOWRT")){
			System.out.println("RouteTable");
		} else 
		if (tokens[0].equals("CLOSE")){
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
}
