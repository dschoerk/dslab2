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
import java.security.Key;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import management.ProxyManagement;
import message.Request;
import message.Response;
import message.request.DownloadFileRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.UploadRequest;
import message.response.DownloadFileResponse;
import message.response.FileServerInfoResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.MessageResponse;
import message.response.UserInfoResponse;
import model.DownloadTicket;
import model.FileServerInfo;
import model.UserInfo;

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

	public static void main(String[] args) throws Exception {

		ComponentFactory factory = new ComponentFactory();
		Shell shell = new Shell("Proxy", System.out, System.in);
		Config cfg = new Config("proxy");
		factory.startProxy(cfg, shell);
	}

	public Proxy(int tcpPort, int udpPort, final int timeout, int checkPeriod, Key shaKey, File keyFolder, PrivateKey privKey,
			Shell shell) throws IOException {
		if (shell != null) {
			shell.register(this);
			shellThread = new Thread(shell);
			shellThread.start();
		}

		this.privKey = privKey;
		this.keyFolder = keyFolder;
		this.shaKey = shaKey;

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

		public ProxyUdpHandler(DatagramSocket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {

			byte[] buf = new byte[32];
			DatagramPacket p = new DatagramPacket(buf, buf.length);

			try {
				while (true) {
					socket.receive(p);
					int port = Integer.parseInt(new String(buf).trim());
					MyFileServerInfo f = new MyFileServerInfo(p.getAddress(), port, 0, true, port);

					synchronized (knownFileservers) {

						MyFileServerInfo server = findServer(f);
						// System.out.println(server==null?"new server":"update "+server.Info());

						if (server == null) { // new fileserver
							server = f;
							populateFiles(server);
							knownFileservers.add(server);
						} else {
							if (!server.isOnline()) {
								populateFiles(f);
							}
						}

						server.setAlive();
						// server.updateOnlineStatus(3000);
					}
				}
			} catch (IOException e) {

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

		private void populateFiles(MyFileServerInfo f) throws IOException {
			// Ask the new Fileserver what files he has
			ListRequest reqObj = new ListRequest();
			SimpleTcpRequest<Request, Response> req = new SimpleTcpRequest<Request, Response>(f.createSocket());
			req.writeRequest(reqObj);
			ListResponse respObj = (ListResponse) req.waitForResponse();

			Set<String> filenamesFromServer = respObj.getFileNames(); // Files
																		// the
																		// server
																		// knows
			req.close();

			Set<String> filesWeUploadToServer = new HashSet<String>(knownFiles.keySet());
			filesWeUploadToServer.removeAll(filenamesFromServer);

			Set<String> filesWeGetFromServer = new HashSet<String>(filenamesFromServer);
			filesWeGetFromServer.removeAll(knownFiles.keySet());

			for (String filename : filesWeGetFromServer) {
				req = new SimpleTcpRequest<Request, Response>(f.createSocket());
				InfoRequest infoObj = new InfoRequest(filename);
				req.writeRequest(infoObj);
				InfoResponse infoResp = (InfoResponse) req.waitForResponse();
				req.close();

				req = new SimpleTcpRequest<Request, Response>(f.createSocket());
				String checksum = ChecksumUtils.generateChecksum("", filename, 0, infoResp.getSize());
				DownloadTicket ticket = new DownloadTicket("", filename, checksum, null, 0);
				DownloadFileRequest downloadObj = new DownloadFileRequest(ticket);
				req.writeRequest(downloadObj);
				DownloadFileResponse downloadResp = (DownloadFileResponse) req.waitForResponse();
				req.close();

				FileInfo fileInfo = new FileInfo(filename, 0, downloadResp.getContent());
				distributeFile(fileInfo);

				knownFiles.put(filename, fileInfo);
			}

			for (String filename : filesWeUploadToServer) {
				req = new SimpleTcpRequest<Request, Response>(f.createSocket());
				UploadRequest uploadObj = new UploadRequest(filename, 0, knownFiles.get(filename).getContent());
				req.writeRequest(uploadObj);
				MessageResponse uploadResp = (MessageResponse) req.waitForResponse();
				req.close();
			}
		}
	}

	public MyFileServerInfo getLeastUsedFileServer() {

		return MyFileServerInfo.minimumUsage(knownFileservers);
	}

	public void distributeFile(FileInfo info) {

		for (MyFileServerInfo server : knownFileservers) {

			if (!server.isOnline())
				continue;

			try {
				Socket s = server.createSocket();
				SimpleTcpRequest<UploadRequest, MessageResponse> req = new SimpleTcpRequest<UploadRequest, MessageResponse>(
						s);
				UploadRequest requestObj = new UploadRequest(info.getName(), 0, info.getContent());
				req.writeRequest(requestObj);
				MessageResponse responseObj = req.waitForResponse();
				req.close();

			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public Map<String, FileInfo> getFiles() {
		return knownFiles;
	}

	public PrivateKey getPrivKey() {
		return privKey;
	}

	public PublicKey getUserKey(String username) {
		for (File s : keyFolder.listFiles()) {
			if (s.getName().equals(username+".pub.pem")) {
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
	
	public ProxyManagement getManagementComonent(){
	    return managementComponent;
	}
}
