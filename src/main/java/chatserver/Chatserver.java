package chatserver;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;


import util.Config;

public class Chatserver implements IChatserverCli, Runnable {

	private String componentName;
	private Config config;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	
	private String lastMessage = "No message received !";

	 private HashMap<String,String> users;
	 private HashMap<String,Socket> loggedInUsers = new HashMap<String,Socket>();
	 private HashMap<String,String> privateMessageUsers = new HashMap<String,String>();

     private UDPServer udpServer;
     private TCPServer tcpServer;
     
     BufferedReader inFromUser;
     

     
     
     public static ArrayList<Socket> socList = new ArrayList<Socket>();
     
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
	public Chatserver(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;

		Config userConfig = new Config("user");
        users = new HashMap<>();
        Set<String> userKeys= userConfig.listKeys();

        for (String key : userKeys) {
                int posUserNameEnd = key.indexOf(".password");
                String username = key.substring(0, posUserNameEnd);
                String password = userConfig.getString(key);
                
                users.put(username, password);
        }

	}

	@Override
	public void run() {
		try {
		try {
			udpServer = new UDPServer(config.getInt("udp.port"));
			Thread udp_thread = new Thread(udpServer);
			udp_thread.start();
			
			
			tcpServer = new TCPServer(config.getInt("tcp.port"));
			Thread tcp_thread = new Thread(tcpServer);
			tcp_thread.start();
		
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		//Read server commands (!exit, !users)
		while (true) {
				inFromUser = new BufferedReader(new InputStreamReader(this.userRequestStream));
				String sentence = inFromUser.readLine();
				if (sentence == null) return;
				
				if (sentence.equals("!users")) {
					this.write(this.users());
					continue;
				}
				
				if (sentence.equals("!exit")) {
					this.write("Shutting down " + this.componentName);
					this.exit();
					break;
				}
				
				this.write("Unknown command!");
		}
		} catch (IOException e) {
			//e.printStackTrace();
		}
	}
	
	private String listOnlineUsers() throws IOException {
		String ret = "Online users: \n";
		
		SortedSet<String> users_sorted = new TreeSet<String>(loggedInUsers.keySet());
		for (String user : users_sorted) {
		    ret += "* " + user + "\n";
		}
		
		return ret;
	}
	
	@Override
	public String users() throws IOException {
		String ret = "";
		SortedSet<String> users_sorted = new TreeSet<String>(users.keySet());
		for (String user : users_sorted) {
			if (loggedInUsers.containsKey(user)) {
				ret += user + " online\n";
			} else {
				ret += user + " offline\n";
			}
		}
		return ret;
	}

	@Override
	public String exit() throws IOException {
		inFromUser.close();
		
		udpServer.exit();
		tcpServer.exit();
		
		return null;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Chatserver}
	 *            component
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		Chatserver chatserver = new Chatserver(args[0],
				new Config("chatserver"), System.in, System.out);
		chatserver.run();
	}
	
	private void write(String text) {
		userResponseStream.println("SERVER > " + text);
		userResponseStream.flush();
	}
	
	private boolean isValidLogin(String username, String password) {
		if (users.get(username) == null) return false;
		if (users.get(username).equals(password)) return true;
		return false;
	}
	

class UDPServer implements Runnable {
        private DatagramSocket datagramSocket;
        
        public UDPServer(int port) throws SocketException {
                this.datagramSocket = new DatagramSocket(port);
        }

        @Override
        public void run() {
                while(true){
                        byte[] buf = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        try {
                                datagramSocket.receive(packet);
                                String received = new String(packet.getData()).trim();
                                if (received == null) return;
                                
                                if (received.equals("!list")) {
                                	this.send(Chatserver.this.listOnlineUsers(), packet.getAddress(), packet.getPort());
                                } else {
                                	this.send("UNKNOWN UDP COMMAND", packet.getAddress(), packet.getPort());
                                }
                                
                                
                        } catch (IOException e) {
                                e.printStackTrace();
                        }
                }
        }

        private void send(String reply, InetAddress ip, int port) throws IOException {
        	//System.out.println("SEVER- REPLYING VIA UDP to client - " + reply);
        	byte[] sendData = new byte[1024];
        	
        	sendData = reply.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, port);
            datagramSocket.send(sendPacket);
        }
        
        public void exit() {
        	datagramSocket.disconnect();
        	datagramSocket.close();
        }
}


class TCPServer implements Runnable {

    private ServerSocket ss = null;
    private Socket incoming = null;
    private int port;

    public TCPServer(int port) {
    	this.port = port;
    }
    
    public void run() {

        try {
            ss = new ServerSocket(port);

            while (true) {
                incoming = ss.accept();
                socList.add(incoming);
                new Thread(new HandleClient(incoming)).start();
            }

        } catch (IOException e) {

        } finally {

            try {
                ss.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    
    public void exit() {
    	try {
    	ss.close();
    	for (Socket sock : socList) {
				sock.close();
    	}
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    class HandleClient implements Runnable {

        InputStream is = null;
        DataOutputStream outToClient = null;
        InputStreamReader isr = null;
        BufferedReader br = null;
        PrintWriter pw = null;
        boolean isDone = false;
        String username = "";
        
        boolean isLoggedIn = false;

        Socket clientsocket = null;

        public HandleClient(Socket socket) throws IOException {

            this.clientsocket = socket;
            outToClient = new DataOutputStream(clientsocket.getOutputStream());
        }

        
        private void login() throws IOException {
            while (true) {
            	String command =  br.readLine().trim();
            	
                if (command.indexOf("!login") == 0) {
                	String[] parts = command.split(" ");
                	username = parts[1];
                	if (Chatserver.this.isValidLogin(username, parts[2])) {
                		this.isLoggedIn = true;
                		this.sendToClient("Successfully logged in.");
                		loggedInUsers.put(username, clientsocket);
                		break;
                	} else {
                		this.sendToClient("Wrong username or password.");
                	}
                } else {
                	this.sendToClient("You are not logged in. Please use !login first.");
                }
            }
        }
        
        
        @Override
        public void run() {
            try {
                is = clientsocket.getInputStream();
                isr = new InputStreamReader(is);
                br = new BufferedReader(isr);


                this.login();
                    
                while (true) {
                    	String command =  br.readLine().trim();
                    	if (command == null) return;
                    	
                    	if (command.indexOf("!send ") == 0) {
                    		command = command.replace("!send ", "");
                    		new Thread(new GlobalChatMessage(command, username)).start();
                    		continue;
                    	}
                    	
                    	if (command.indexOf("!register ") == 0) {
                    		String[] parts = command.split(" ");
                    		privateMessageUsers.put(username, parts[1]);
                    		continue;
                    	}
                    	
                    	if (command.indexOf("!lookup ") == 0) {
                    		String[] parts = command.split(" ");
                    		if (privateMessageUsers.containsKey(parts[1])) {
                    			this.sendToClient(privateMessageUsers.get(parts[1]));
                    		} else {
                    			this.sendToClient("No entry exits for this user.");
                    		}
                    
                    		continue;
                    	}
                    	
                    	if (command.indexOf("!lookupSilent ") == 0) {
                    		String[] parts = command.split(" ");
                    		if (privateMessageUsers.containsKey(parts[1])) {
                    			this.sendToClient("!lookupResult " + privateMessageUsers.get(parts[1]));
                    		} else {
                    			this.sendToClient("!lookupResult error");
                    		}
                    
                    		continue;
                    	}
                    	
                    	if (command.equals("!lastMsg")) {
                    		this.sendToClient(lastMessage);
                    		continue;
                    	}
                    	
                    	if (command.equals("!logout")) {
                    		this.sendToClient("Successfully logged out.");
                    		loggedInUsers.remove(username);
                    		
                    		this.login();
                    		continue;
                    	}
                    	
                    	if (command.equals("!exit")) {
                    		loggedInUsers.remove(username);
                    		br.close();
                            clientsocket.close();
                            socList.remove(incoming);
                            continue;
                    	}
                    	
                    	this.sendToClient("Invalid command!");
                }
                
            } catch (IOException e) {
            	//e.printStackTrace();
            }
        }
        
        private void sendToClient(String msg) throws IOException {
        	outToClient.writeBytes(msg+"\n");
        }
        

    }

    class GlobalChatMessage implements Runnable {
    	private String message;
    	private String sender;

        public GlobalChatMessage(String s, String sender) {
        	lastMessage = sender + ": " + s;
            this.message = s;
            this.sender = sender;
        }

        @Override
        public void run() {

            for (Entry<String, Socket> user : loggedInUsers.entrySet()) {
            	
            	//Do not send message to sender
            	if (user.getKey() != null && user.getKey().equals(sender)) continue;
                
                    try {
                    	new DataOutputStream(user.getValue().getOutputStream()).writeBytes(sender + ": " + message + "\n");
                    } catch (IOException e) {
                        System.out.println("Its in Catch");
                    }
            }
        }
    }
}

}
