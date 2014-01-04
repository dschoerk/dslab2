package your;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;

import management.ProxyManagement;
import message.AliveMessage;
import message.Response;
import message.request.DownloadFileRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.UploadRequest;
import message.response.DownloadFileResponse;
import message.response.MessageIntegrityErrorResponse;
import message.response.FileServerInfoResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.MessageResponse;
import message.response.UserInfoResponse;
import model.DownloadTicket;
import model.FileServerInfo;
import model.UserInfo;
import networkio.Channel;
import networkio.HMACChannel;
import networkio.HMACWrapped;
import networkio.TCPChannel;
import networkio.UDPChannel;

import org.bouncycastle.openssl.PEMReader;

import proxy.IProxyCli;
import util.ChecksumUtils;
import util.ComponentFactory;
import util.Config;
import cli.Command;
import cli.Shell;

public class Proxy implements IProxyCli, Runnable {

	private ProxyUdpHandler udpHandler;
	private ExecutorService threadpool;
	private ServerSocket tcpServer;
	private DatagramSocket udpServer;

	private List<MyFileServerInfo> knownFileservers;
	private List<ProxySession> sessions;
	private Map<String, FileInfo> knownFiles;

	private UserDB users = new UserDB();
	private Thread shellThread, connectionHandler, udpListenerThread;
	private Timer fileserverOnlineTimer;

	private ProxyManagement managementComponent;

	private PrivateKey privKey;
	private File keyFolder;
	private Key shaKey;
	private Mac hmac;

	public static void main(String[] args) throws Exception {

		ComponentFactory factory = new ComponentFactory();
		Shell shell = new Shell("Proxy", System.out, System.in);
		Config cfg = new Config("proxy");
		factory.startProxy(cfg, shell);
	}

	public Proxy(int tcpPort, int udpPort, final int timeout, int checkPeriod, Key shaKey, File keyFolder,
			PrivateKey privKey, Shell shell) throws IOException {
		if (shell != null) {
			shell.register(this);
			shellThread = new Thread(shell);
			shellThread.start();
		}

		this.privKey = privKey;
		this.keyFolder = keyFolder;
		this.shaKey = shaKey;

		try {
			hmac = Mac.getInstance("HmacSHA256");
			hmac.init(shaKey);
		} catch (InvalidKeyException e1) {
			// does not happen
			e1.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// does not happen
			e.printStackTrace();
		}

		knownFileservers = Collections.synchronizedList(new ArrayList<MyFileServerInfo>());
		sessions = Collections.synchronizedList(new ArrayList<ProxySession>());
		knownFiles = Collections.synchronizedMap(new HashMap<String, FileInfo>());

		tcpServer = new ServerSocket(tcpPort);
		udpServer = new DatagramSocket(udpPort);

		udpHandler = new ProxyUdpHandler(udpServer);
		udpListenerThread = new Thread(udpHandler);
		udpListenerThread.start();

		threadpool = Executors.newCachedThreadPool();
		connectionHandler = new Thread(this);
		connectionHandler.start();

		TimerTask fsOnlineTask = new TimerTask() {
			public void run() {
				for (MyFileServerInfo fs : knownFileservers) {
					fs.updateOnlineStatus(timeout);
				}
			}
		};

		fileserverOnlineTimer = new Timer();
		fileserverOnlineTimer.schedule(fsOnlineTask, 0, checkPeriod);

		managementComponent = new ProxyManagement(this);
	}

	public void removeSession(ProxySession session) {
		sessions.remove(session);
	}

	public UserDB getUserDB() {
		return users;
	}

	// public void addFile(FileInfo info) {
	// knownFiles.put(info.getName(), info);
	// }

	@Override
	public void run() {
		try {
			while (true) {
				Socket clientSocket = tcpServer.accept();
				// System.out.println("spawn new Tcp client thread");

				ProxySession clientHandler = new ProxySession(clientSocket, this);
				sessions.add(clientHandler);
				threadpool.execute(clientHandler);

				// System.out.println("thread started");
			}
		} catch (SocketException e) {
			// socket now closed
			// System.out.println("i'M done");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	@Command
	public Response fileservers() throws IOException {
		List<FileServerInfo> fileServerInfo = new ArrayList<FileServerInfo>();
		for (MyFileServerInfo i : knownFileservers) {
			fileServerInfo.add(i.Info());
		}
		FileServerInfoResponse response = new FileServerInfoResponse(fileServerInfo);
		return response;
	}

	@Override
	@Command
	public Response users() throws IOException {

		List<UserInfo> info_list = new ArrayList<UserInfo>();

		for (Iterator<User> it = users.iterator(); it.hasNext();) {

			User u = it.next();
			boolean online = false;
			for (ProxySession s : sessions) {
				User user = s.getUser();
				if (user != null && user.hasName(u.getName())) {
					online = true;
					continue;
				}
			}

			info_list.add(u.createInfoObject(online));
		}

		UserInfoResponse uir = new UserInfoResponse(info_list);
		return uir;
	}

	@Override
	@Command
	public MessageResponse exit() throws IOException {

		// System.out.println("shutting down");
		tcpServer.close();
		udpServer.close();

		threadpool.shutdown();

		synchronized (sessions) {
			for (ProxySession s : sessions) {
				s.close();
			}
		}

		managementComponent.close();

		System.in.close();

		fileserverOnlineTimer.cancel();
		return new MessageResponse("closed Proxy");
	}

	private class ProxyUdpHandler implements Runnable {

		private DatagramSocket socket;
		private UDPChannel channel;

		public ProxyUdpHandler(DatagramSocket socket) {
			this.socket = socket;
			try {
				channel = new UDPChannel(socket);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		@Override
		public void run() {

			// byte[] buf = new byte[32];
			// DatagramPacket p = new DatagramPacket(buf, buf.length);

			try {
				while (true) {
					// socket.receive(p);
					// int port = Integer.parseInt(new String(buf).trim());

					AliveMessage msg = (AliveMessage) channel.read();
					int port = msg.getPort();
					DatagramPacket p = channel.getLatestPacket();
					MyFileServerInfo f = new MyFileServerInfo(p.getAddress(), port, 0, true, port);

					synchronized (knownFileservers) {

						MyFileServerInfo server = findServer(f);

						if (server == null) { // new fileserver
							server = f;
							// populateFiles(server);
							knownFileservers.add(server);
						} else {
							if (!server.isOnline()) {
								// populateFiles(f);
							}
						}

						server.setAlive();
						// server.updateOnlineStatus(3000);
					}
				}
			} catch (IOException e) {

			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		private MyFileServerInfo findServer(MyFileServerInfo f) throws IOException {
			for (MyFileServerInfo i : knownFileservers) { // find server
				if (i.equals(f)) {
					return i;
				}
			}
			return null;
		}
	}
	private Channel createFsChannel(Socket s) throws IOException {
		return new TCPChannel(s);
	}

	public MyFileServerInfo getLeastUsedFileServer() {

		return MyFileServerInfo.minimumUsage(knownFileservers);
	}

	public void distributeFile(ConcurrentHashMap<Long, MyFileServerInfo> writeQuorum, FileInfo info, int version) {

		for (MyFileServerInfo server : writeQuorum.values()) {

			Response res = uploadRequest(info, version, server);
			if (res == null || res instanceof MessageIntegrityErrorResponse) {
				// Try again if failed
				res = uploadRequest(info, version, server);
			}
		}
	}

	private Response uploadRequest(FileInfo info, int version, MyFileServerInfo server) {
		
		Socket s = null;
		try {
			s = server.createSocket();
			Channel req = createFsChannel(s);
			UploadRequest requestObj = new UploadRequest(info.getName(), version, info.getContent());
			HMACWrapped wrap = new HMACWrapped(requestObj, hmac);
			req.write(wrap);
			return (Response) req.read();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally
		{
			try {
				s.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		return null;
	}

	public Map<String, FileInfo> getFiles() {
		return knownFiles;
	}

	public PrivateKey getPrivKey() {
		return privKey;
	}

	public PublicKey getUserKey(String username) {
		for (File s : keyFolder.listFiles()) {
			if (s.getName().equals(username + ".pub.pem")) {
				PEMReader in;
				try {
					in = new PEMReader(new FileReader(s));
					return (PublicKey) in.readObject();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}

	public ProxyManagement getManagementComonent() {
		return managementComponent;
	}

	public HashMap<MyFileServerInfo, Long> getOnlineServer() {
		HashMap<MyFileServerInfo, Long> onlineservers = new HashMap<MyFileServerInfo, Long>();

		for (MyFileServerInfo server : knownFileservers) {
			
			System.out.println("server online "+server.getAddress());
			if (server.isOnline()) {
				onlineservers.put(server, server.getUsage());
			}
		}
		return onlineservers;
	}

	public Response infoRequest(MyFileServerInfo fileserver, String filename) {
		Response infoResponseObj = null;
		try {
			Socket s = fileserver.createSocket();
			InfoRequest infoRequestObj = new InfoRequest(filename);
			Channel infoRequest = new TCPChannel(s);
			infoRequest.write(infoRequestObj);
			try {
				infoResponseObj = (Response) infoRequest.read();
			} catch (ClassNotFoundException e) {
				// does not happen
				e.printStackTrace();
			}
			s.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return infoResponseObj;
	}
}
