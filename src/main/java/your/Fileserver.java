package your;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;

import org.bouncycastle.crypto.macs.HMac;

import message.AliveMessage;
import message.response.MessageResponse;
import networkio.Channel;
import networkio.HMACChannel;
import networkio.UDPChannel;
import server.IFileServerCli;
import util.ComponentFactory;
import util.Config;
import cli.Command;
import cli.Shell;

public class Fileserver implements IFileServerCli, Runnable {

	private DatagramSocket aliveSocket;
	private File downloadDir;
	private ServerSocket tcpServer;
	private ExecutorService threadpool;
	private Timer aliveTimer;
	private Key shaKey;
	private HashMap<String, Integer> version= new HashMap<String, Integer>();

	public static void main(String[] args) throws Exception {
		ComponentFactory factory = new ComponentFactory(); 
		Shell shell = new Shell("Fileserver", System.out, System.in);
		Config cfg = new Config("fs1");
		factory.startFileServer(cfg, shell);
	}

	public Fileserver(Key shaKey, String fileserverdir, int tcpport, String proxyhost, int proxyudpport, int fileserverminalive,
			Shell shell) throws IOException {

		if (shell != null) {
			shell.register(this);
			Thread shellThread = new Thread(shell);
			shellThread.start();
		}

		this.shaKey = shaKey;
		downloadDir = new File(fileserverdir);
		initVersionMap();
		InetAddress receiverAddress = InetAddress.getByName(proxyhost);
		aliveSocket = new DatagramSocket();
		
		Mac hmac = null;
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
		
		final Channel udpChannel = new UDPChannel(aliveSocket,receiverAddress,proxyudpport);
		final Channel channel = new HMACChannel(udpChannel, hmac);
		
		
		//byte[] buf = (tcpport + "\0").getBytes();
		final AliveMessage msg = new AliveMessage(tcpport);
		
		//final DatagramPacket keepAlivePacket = new DatagramPacket(buf, buf.length, receiverAddress, proxyudpport);
		
		TimerTask aliveTask = new TimerTask() {
			public void run() {
				try {
					//aliveSocket.send(keepAlivePacket);
					channel.write(msg);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};

		aliveTimer = new Timer();
		aliveTimer.schedule(aliveTask, 0, fileserverminalive);

		tcpServer = new ServerSocket(tcpport);
		threadpool = Executors.newCachedThreadPool();
		Thread connectionHandler = new Thread(this);
		connectionHandler.start();
	}

	@Override
	public void run() {
		try {
			while (true) {

				Socket clientSocket = tcpServer.accept();
				// System.out.println("spawn new Tcp client thread");

				FileserverSession clientHandler = new FileserverSession(clientSocket, this);
				// sessions.add(clientHandler);
				threadpool.execute(clientHandler);

				// System.out.println("thread started");
			}
		} catch (IOException e) {
			//e.printStackTrace();
			// System.out.println("Socket closed");
		}
	}

	@Override
	@Command
	public MessageResponse exit() throws IOException {
		tcpServer.close();
		aliveTimer.cancel();
		threadpool.shutdown();
		System.in.close();
		return new MessageResponse("closed Fileserver");
	}

	public synchronized File getDownloadDirectory() {
		return downloadDir;
	}

	public byte[] getFileContent(File f) {
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			FileIo.copy(new FileInputStream(f), baos, 128);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		byte[] bytes = baos.toByteArray();
		return bytes;
		/*byte[] data = null;
		try {
			data = Files.readAllBytes(Paths.get(f.toURI()));
		} catch (IOException e) {
			return null;
		}
		return data;*/
	}

	public Key getHMACKey() {
		return shaKey;
	}
	private void initVersionMap()
	{
		for (File f :downloadDir.listFiles()) {
			version.put(f.getName(), 0);
		}
	}
	public HashMap<String, Integer> getVersionMap()
	{
		return version;
	}
}
