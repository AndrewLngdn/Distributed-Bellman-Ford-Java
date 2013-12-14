import java.io.*;
import java.net.*;
class Bfclient {
	int localport;
	int timeout; // seconds
	private BufferedReader input = null;
	boolean execute;

	public Bfclient(String[] args){
		execute = true;
		if (args.length < 2 || (args.length-2)%3 != 0){
			System.out.println("Incorrect arguments");
			System.out.println("java Bfclient localport timeout [ipaddress1 port1 weight1 ...]");
			System.exit(1);
		} else { 
			this.localport = Integer.parseInt(args[0]);
			this.timeout = Integer.parseInt(args[1]);
			System.out.println("localport: " + localport);
			System.out.println("timeout: " + timeout);
		}
	}

	public static void main(String[] args){
		Bfclient node = new Bfclient(args);
		node.listenForCommands();
	}

	private void listenForCommands(){
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
				this.interpretCommand(stdIn.readLine());
			} catch (IOException e){
				System.err.println(e);
			}
		}

		try {
			stdIn.close();
		} catch (IOException e){
			System.err.println(e);
		}
	}

	private void interpretCommand(String command){
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
			System.out.println("close it up!");
		} else {
			System.out.println("Incorrect command, use one of the following:");
			System.out.println("LINKDOWN [ip]");
			System.out.println("LINKUP [ip]");
			System.out.println("SHOWRT");
			System.out.println("CLOSE");
		}
	}
}
