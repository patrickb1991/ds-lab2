package nameserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.MissingResourceException;
import java.util.SortedSet;
import java.util.TreeSet;

import nameserver.exceptions.AlreadyRegisteredException;
import nameserver.exceptions.InvalidDomainException;
import nameserver.exceptions.UnknownUsernameException;
import util.Config;

/**
 * Please note that this class is not needed for Lab 1, but will later be used
 * in Lab 2. Hence, you do not have to implement it for the first submission.
 */
public class Nameserver implements INameserver, INameserverCli, Runnable {

	private static final long serialVersionUID = 1L;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	
	private String rootServerID;
	
	private Registry rmiReg;
	
	private String domain = null;
	
	private HashMap<String, INameserver> sub_servers;
	
	private HashMap<String, String> users;
	
	/**
	 * @param componentName
	 *            the name of the component - represented in the prompt
	 * @param config
	 *            the configuration to use
	 * @param userRequestStream
	 *            the input stream to read user input from
	 * @param userResponseStream
	 *            the output stream to write the console output to
	 */
	public Nameserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		rootServerID = config.getString("root_id");
		sub_servers = new HashMap<String, INameserver>();
		users = new HashMap<String, String>();
	}

	@Override
	public void run() {
		try {
			domain = config.getString("domain");
		} catch (MissingResourceException e) {
			domain = null;
		}
		
		if (domain == null) {
			this.write("Starting rootserver...");
			try {
				rmiReg = LocateRegistry.createRegistry(config.getInt("registry.port"));
				
				INameserver stub_ns = (INameserver) UnicastRemoteObject.exportObject(this, 0);
				rmiReg.rebind(rootServerID, stub_ns);
				
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		} else {
			this.write("Starting nameserver for domain '" + domain + "'...");
			try {
				rmiReg = LocateRegistry.getRegistry(config.getString("registry.host"), config.getInt("registry.port"));
				INameserver root = (INameserver) rmiReg.lookup(rootServerID);
				
				INameserver stub_ns = (INameserver) UnicastRemoteObject.exportObject(this, 0);
				
				root.registerNameserver(domain + ".", stub_ns, stub_ns);
				
			} catch (RemoteException | NotBoundException | AlreadyRegisteredException | InvalidDomainException e) {
				e.printStackTrace();
			}
			
			
		}
		
		//Read name-server commands
				try {
				while (true) {
					BufferedReader inFromUser = new BufferedReader(new InputStreamReader(this.userRequestStream));
						String sentence = inFromUser.readLine();
						if (sentence == null) return;
						
						if (sentence.equals("!nameservers")) {
							this.write(this.nameservers());
							continue;
						}
						
						if (sentence.equals("!addresses")) {
							this.write(this.addresses());
							continue;
						}
						
						if (sentence.equals("!exit")) {
							this.exit();
							break;
						}
						
						this.write("Unknown command!");
				}
				} catch (IOException e) {
					e.printStackTrace();
				}
		 
	}

	@Override
	public String nameservers() throws IOException {
		String ret = "\n";
		int id = 1;
		SortedSet<String> servers_sorted = new TreeSet<String>(sub_servers.keySet());
		for (String server : servers_sorted) {
				ret += id + ". " + server + "\n";
				id++;
		}
		return ret;
	}

	@Override
	public String addresses() throws IOException {
		String ret = "\n";
		int id = 1;
		SortedSet<String> users_sorted = new TreeSet<String>(users.keySet());
		for (String user : users_sorted) {
				ret += id + ". " + user + " " + users.get(user) + "\n";
				id++;
		}
		return ret;
	}

	@Override
	public String exit() throws IOException {
		UnicastRemoteObject.unexportObject(this, true);
		try {
			//Rootserver should clean registry.
			if (domain == null) {
				rmiReg.unbind(rootServerID);
				UnicastRemoteObject.unexportObject(rmiReg, true);
			}
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
		
		return "Nameserver exiting...";
	}
	
	private void write(String msg) {
		if (domain != null) userResponseStream.println("NAMESERVER " + domain + " > "  + msg);
		if (domain == null) userResponseStream.println("ROOTSERVER > "  + msg);
	}

	@Override
	public void registerUser(String username, String address)
			throws RemoteException, AlreadyRegisteredException,
			InvalidDomainException {
		
		
		String[] zones = username.split("\\.");
		
		String top_domain = zones[zones.length-1];
		
		if (zones.length == 0) {
			throw new InvalidDomainException("Invalid Domain Exception");
		} else if (zones.length == 1) {
			//Register user on this server
			if (users.containsKey(username)) users.remove(username);
			users.put(zones[0], address);
            this.write("Registered username '"+ top_domain + "' with address: " + address);
		} else if (zones.length > 1) {
			if (!sub_servers.containsKey(top_domain)) {
				throw new InvalidDomainException("Error when registering user '" + username + "'. There does not exist a name server for " + top_domain + ".");
			} else {
				String subdomain = "";
				for (int i = 0; i <= zones.length-2; i++) subdomain += zones[i]+".";
				
				this.write("Registration of username '" + username + "' Forwarding request to sub-nameserver.");
				sub_servers.get(top_domain).registerUser(subdomain, address);
			}
		}
	}

	@Override
	public INameserverForChatserver getNameserver(String zone)
		throws RemoteException, InvalidDomainException {
		this.write("Nameserver request for zone '" + zone + "'");
		if (!sub_servers.containsKey(zone)) {
			throw new InvalidDomainException("Requested zone " + zone + " is not registered on this nameserver.");
		} else {
			return sub_servers.get(zone);
		}
	}

	@Override
	public String lookup(String username) throws RemoteException, UnknownUsernameException {
		this.write("Username lookup request for '" + username + "'");
		if (!users.containsKey(username)) { 
			throw new UnknownUsernameException("Lookup for '" + username + "' failed. No such user registered on this nameserver.");
		} else {
			return users.get(username);
		}
	}

	@Override
	public void registerNameserver(String domain, INameserver nameserver,
			INameserverForChatserver nameserverForChatserver)
			throws RemoteException, AlreadyRegisteredException,
			InvalidDomainException {
		
		//domain = "vienna.at.com.de";
		String[] zones = domain.split("\\.");
		
		String top_domain = zones[zones.length-1];
		
		if (zones.length == 0) {
			throw new InvalidDomainException("Invalid Domain Exception");
		} else if (zones.length == 1) {
			//Register zone on this server
			if(sub_servers.containsKey(top_domain)) throw new AlreadyRegisteredException("Domain " + top_domain + " is already registered.");
			sub_servers.put(top_domain, nameserver);
            this.write("Registered domain '" + top_domain + "'.");
		} else if (zones.length > 1) {
			//Register zone on sub-server
			if (!sub_servers.containsKey(top_domain)) {
				throw new InvalidDomainException("Error when registering '" + domain + "'. There does not exist a name server for " + top_domain + " yet.");
			} else {
				String subdomain = "";
				for (int i = 0; i <= zones.length-2; i++) subdomain += zones[i] + ".";
				
				this.write("Registration of domain '" + subdomain + "'. Forwarding request to sub-nameserver.");
				sub_servers.get(top_domain).registerNameserver(subdomain, nameserver, nameserver);
			}
		}
	}
	
	/**
	 * @param args
	 *            the first argument is the name of the {@link Nameserver}
	 *            component
	 */
	public static void main(String[] args) {
		Nameserver nameserver = new Nameserver(args[0], new Config(args[0]),
				System.in, System.out);
		nameserver.run();
	}

}
