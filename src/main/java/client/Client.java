package client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;

import org.bouncycastle.util.encoders.Base64;

import util.Config;
import util.Keys;

public class Client implements IClientCli, Runnable {

	private String componentName;
	private Config config;
	private Mac hMac;
	private InputStream userRequestStream;
	private PrintStream userResponseStream;
	
	private UDPServer udpServer;
	private Thread udpThread;
	
	private TCPClient tcpClient;
	private Thread tcpThread;
	
	private String username;
	
	private String lastPrivateMessageLookup = "error";
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
	public Client(String componentName, Config config,
			InputStream userRequestStream, PrintStream userResponseStream) {
		this.componentName = componentName;
		this.config = config;
		this.userRequestStream = userRequestStream;
		this.userResponseStream = userResponseStream;	
		try {
			Key secretKey = Keys.readSecretKey(new File("keys/hmac.key"));
			hMac = Mac.getInstance(secretKey.getAlgorithm());
			hMac.init(secretKey);
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		}

		// TODO
	}

	@Override
	public void run() {
		try {
			udpServer = new UDPServer(config.getString("chatserver.host"), config.getInt("chatserver.udp.port"));
			udpThread = new Thread(udpServer);
			udpThread.start();
			
			
			tcpClient = new TCPClient(config.getString("chatserver.host"), config.getInt("chatserver.tcp.port"));
			tcpThread = new Thread(tcpClient);
			tcpThread.start();
			
			while (true) {
				BufferedReader inFromUser = new BufferedReader(new InputStreamReader(this.userRequestStream));
					String sentence = inFromUser.readLine();
					if (sentence.equals("!list")) {
						this.list();
						continue;
					}
					
					if (sentence.equals("!lastMsg")) {
						this.lastMsg();
						continue;
					}
					
					if (sentence.equals("!logout")) {
						this.logout();
						continue;
					}
					
					if (sentence.indexOf("!login ") == 0) {
						String[] parts = sentence.split(" ");
						
						this.login(parts[1], parts[2]);
						continue;
					}
					
					if (sentence.indexOf("!send ") == 0) {
						this.send(sentence);
						continue;
					}
					
					if (sentence.indexOf("!register ") == 0) {
						String[] parts = sentence.split(" ");
						
						this.register(parts[1]);
						continue;
					}
					
					if (sentence.indexOf("!lookup ") == 0) {
						String[] parts = sentence.split(" ");
						
						this.lookup(parts[1]);
						continue;
					}
					
					if (sentence.indexOf("!msg ") == 0) {
						String[] parts = sentence.split(" ");
						
						//Handle spaces
						sentence = sentence.replace("!msg " + parts[1] + " ", "");
						
						this.msg(parts[1], sentence);
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
	public String login(String username, String password) throws IOException {
		this.username = username;
		tcpClient.send("!login" + " " + username + " " + password);
		return null;
	}

	@Override
	public String logout() throws IOException {
		tcpClient.send("!logout");
		return null;
	}

	@Override
	public String send(String message) throws IOException {
		tcpClient.send(message);
		return null;
	}

	@Override
	public String list() throws IOException {
		udpServer.send("!list");
		return null;
	}

	@Override
	public String msg(String username, String message) throws IOException {
		
		//TODO Lookup
		tcpClient.send("!lookupSilent " + username);
		
		//Wait for result
		try {
			Thread.sleep(200);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		if (lastPrivateMessageLookup.equals("error")) {
			this.write("Wrong username or user not reachable.");
			return null;
		}
		
		//this.write("Message lookup" + lastPrivateMessageLookup);
		String[] parts = lastPrivateMessageLookup.split(":");
		
		new Thread(new TCPPrivateMessageSender(parts[0], Integer.parseInt(parts[1]), message)).start();
		
		return null;
	}

	@Override
	public String lookup(String username) throws IOException {
		tcpClient.send("!lookup " + username);
		return null;
	}

	@Override
	public String register(String privateAddress) throws IOException {
		tcpClient.send("!register " + privateAddress);
		String[] parts = privateAddress.split(":");
		new Thread(new TCPPrivateMessageListener(Integer.parseInt(parts[1]))).start();
		
		return null;
	}
	
	@Override
	public String lastMsg() throws IOException {
		tcpClient.send("!lastMsg");
		return null;
	}

	@Override
	public String exit() throws IOException {
		this.write("Exiting Client " + this.componentName);
		
		tcpClient.send("!exit");
		
		
		udpServer.exit();
		tcpClient.exit();
		
		udpThread.interrupt();
		tcpThread.interrupt();
		
		return null;
	}

	/**
	 * @param args
	 *            the first argument is the name of the {@link Client} component
	 */
	public static void main(String[] args) {
		Client client = new Client(args[0], new Config("client"), System.in,
				System.out);
		client.run();
	}
	
	//Writes text to userResponseStream
	private void write(String text) {
		userResponseStream.println("CLIENT " + componentName + " > " + text);
		userResponseStream.flush();
	}

	// --- Commands needed for Lab 2. Please note that you do not have to
	// implement them for the first submission. ---

	@Override
	public String authenticate(String username) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}
	
	class UDPServer implements Runnable {
		private DatagramSocket udpSocket;
		private String ip;
		private int port;
		
        public UDPServer(String ip, int port) throws SocketException {
                udpSocket = new DatagramSocket();
                this.ip = ip;
                this.port = port;
        }

        @Override
        public void run() {
                while(true) {
                        byte[] buf = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        try {
                        		udpSocket.receive(packet);
                                String received = new String(packet.getData()).trim();

                                Client.this.write(received);
                        } catch (IOException e) {
                                //e.printStackTrace();
                        }
                }
        }

        public void send(String reply) throws IOException {
        	InetAddress ip_addr = InetAddress.getByName(ip);
        	
        	byte[] sendData = new byte[1024];
        	
        	sendData = reply.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip_addr, port);
            udpSocket.send(sendPacket);
        }
        
        public void exit() {
        	udpSocket.disconnect();
        	udpSocket.close();
        }
	}
	
	class TCPClient implements Runnable {
		private Socket clientSocket;
		private DataOutputStream outToServer;
		private String ip;
		private int port;
		
		
		
		public TCPClient(String ip, int port) {
			
			this.ip = ip;
            this.port = port;
		}
	 
		
		public void run() {
			try {
				clientSocket = new Socket(ip, port);
				outToServer = new DataOutputStream(clientSocket.getOutputStream());
				BufferedReader inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			
				while (true) {
					String received = inFromServer.readLine();
		
					if (received == null) return;
					//Handle lookup results differently
					if (received.indexOf("!lookupResult ") == 0) {
						received = received.replace("!lookupResult ", "");
						lastPrivateMessageLookup = received;
					} else {
						Client.this.write(received);
					}
					
				}
			} catch (IOException e) {

				//e.printStackTrace();
			}
	 }

		public void send(String msg) throws IOException {
			outToServer.writeBytes(msg+"\n");
			
		}
		
		public void exit() {
			try {
				outToServer.close();
				clientSocket.close();				
			} catch (IOException e) {

			}
		}
	}
	
	
	class TCPPrivateMessageListener implements Runnable {
		    private ServerSocket ss = null;
		    private Socket incoming = null;
		    private int port;

		    public TCPPrivateMessageListener(int port) {
		    	this.port = port;
		    }
		    
		    public void run() {

		        try {
		            ss = new ServerSocket(port);

		            while (true) {
		                incoming = ss.accept();
		                new Thread(new HandleClient(incoming)).start();
		            }

		        } catch (IOException e) {
		            e.printStackTrace();
		        } finally {
		            try {
		                ss.close();
		            } catch (IOException e) {
		                e.printStackTrace();
		            }
		        }

		    }

		    class HandleClient implements Runnable {

		        InputStream is = null;
		        DataOutputStream outToClient = null;
		        InputStreamReader isr = null;
		        BufferedReader br = null;

		        Socket clientsocket = null;

		        public HandleClient(Socket socket) throws IOException {
		            this.clientsocket = socket;
		            outToClient = new DataOutputStream(clientsocket.getOutputStream());
		        }

		        @Override
		        public void run() {
		            try {
		                is = clientsocket.getInputStream();
		                isr = new InputStreamReader(is);
		                br = new BufferedReader(isr);

		                String message = br.readLine().trim();
		                String[] parts = message.split(" !msg ");
		                byte[] hashReceived = Base64.decode(parts[0]);
		                hMac.update(parts[1].getBytes());
		                byte[] hashComputed = hMac.doFinal();
		                boolean validHash = MessageDigest.isEqual(hashReceived, hashComputed);
		                if(validHash){
		                	Client.this.write("Valid Hash");
		                	this.sendToClient(parts[0] + " !ack " + parts[1]);
		                }else{
		                	Client.this.write("Invalid Hash");
		                	this.sendToClient(parts[0] + " !tampered " + parts[1]);
		                }
		                
		                Client.this.write(parts[1]);
		                
		                
		                
		                br.close();
	                    clientsocket.close();
	                    
		            } catch (IOException e) {
		            	e.printStackTrace();
		            } finally {

		                try {
		                    br.close();
		                    clientsocket.close();

		                } catch (IOException e) {
		                	e.printStackTrace();
		                }
		            }
		        }
		        
		        private void sendToClient(String msg) throws IOException {
		        	outToClient.writeBytes(msg+"\n");
		        }
		    }
		}
	
	
	class TCPPrivateMessageSender implements Runnable {
		private Socket clientSocket;
		private DataOutputStream outToServer;
		private BufferedReader inFromServer;
		private String ip;
		private int port;
		String pm;
		
		
		
		public TCPPrivateMessageSender(String ip, int port, String pm) {
			this.pm = pm;
			this.ip = ip;
            this.port = port;
		}
	 
		
		public void run() {
	  
			try {
				clientSocket = new Socket(ip, port);
				outToServer = new DataOutputStream(clientSocket.getOutputStream());
				inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				
				hMac.update(pm.getBytes());
				byte[] hash = hMac.doFinal();
				byte[] base64hash = Base64.encode(hash);
				String msg = new String(base64hash) + " !msg " + pm;
				this.send(msg);
								
				String received = inFromServer.readLine();
				String[] parts;
				if(received.contains("!tampered")){
					parts = received.split(" !tampered ");
					Client.this.write("The original message has been tampered with.");
				}else{
					parts = received.split(" !ack ");
					byte[] hashReceived = Base64.decode(parts[0]);
	                hMac.update(parts[1].getBytes());
	                byte[] hashComputed = hMac.doFinal();
	                boolean validHash = MessageDigest.isEqual(hashReceived, hashComputed);
	                if(!validHash){
	                	Client.this.write("The return message has been tampered with.");
	                }
	                Client.this.write(parts[1]);
				}
				
				clientSocket.close();
				outToServer.close();
				inFromServer.close();
				
			} catch (IOException e) {
				e.printStackTrace();
			}
	 }

		public void send(String msg) throws IOException {
			outToServer.writeBytes(msg+"\n");
		}
	}

}
