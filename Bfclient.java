import java.io.*;
import java.net.*;
class Bfclient {
	static int localport;
	static int timeout; // seconds
	static boolean execute;
	private BufferedReader input = null;

	public static void main(String[] args){
		execute = true;
		
		interpretArgs(args);
		listenToSocket();
		dealWithTimeouts();
		listenForCommands();
	}

	public static void listenToSocket(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				int count = 0;
				while(execute){
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
			System.out.println("linkdown " + tokens[1]);
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
