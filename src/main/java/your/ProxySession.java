package your;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

import message.LoginMessageFinal;
import message.LoginMessageOk;
import message.Request;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadTicketRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.request.VersionRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.DownloadTicketResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.LoginResponse.Type;
import message.response.MessageResponse;
import message.response.VersionResponse;
import model.DownloadTicket;
import networkio.AESChannel;
import networkio.Base64Channel;
import networkio.Channel;
import networkio.HMACChannel;
import networkio.RSAChannel;
import networkio.RequestFailedException;
import networkio.TCPChannel;

import org.bouncycastle.util.encoders.Base64;

import proxy.IProxy;
import util.ChecksumUtils;

public class ProxySession implements Runnable, IProxy {

	private Socket socket;
	private Channel base_channel;
	private Channel channel_in;
	private Channel channel_out;

	private UserDB users;

	private User user = null;
	// private boolean running = true;

	private static Map<Class<?>, Method> commandMap = new HashMap<Class<?>, Method>();
	private static Set<Class<?>> hasArgument = new HashSet<Class<?>>();

	private Proxy parent;
	private Mac hmac;

	public ProxySession(Socket s, Proxy parent) {
		this.socket = s;
		this.parent = parent;
		this.users = parent.getUserDB();

		try {
			hmac = Mac.getInstance("HmacSHA256");
			hmac.init(parent.getShaKey());
		} catch (InvalidKeyException e1) {
			// does not happen
			e1.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// does not happen
			e.printStackTrace();
		}

		try {
			base_channel = new Base64Channel(new TCPChannel(socket));
			channel_in = new RSAChannel(base_channel, parent.getPrivKey(), Cipher.DECRYPT_MODE);
			// channel_out = new RSAChannel(base_channel, parent.getPrivKey(),
			// Cipher.ENCRYPT_MODE);

		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}

	public void close() throws IOException {
		socket.close();
	}

	@Override
	public void run() {
		try {
			while (true) {

				Object o = channel_in.read();
				Object response = null;

				if (hasArgument.contains(o.getClass())) {
					response = commandMap.get(o.getClass()).invoke(this, o);
				} else {
					response = commandMap.get(o.getClass()).invoke(this);
				}
				channel_out.write(response);

				if (user == null) {
					channel_in = new RSAChannel(base_channel, parent.getPrivKey(), Cipher.DECRYPT_MODE);
				}
			}
		} catch (IOException e) {
			// running = false;
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}

		parent.removeSession(this);
	}

	@Override
	public LoginResponse login(LoginRequest lr) throws IOException {
		User user = users.getUser(lr.getUsername());
		PublicKey key = parent.getUserKey(lr.getUsername());
		channel_out = new RSAChannel(base_channel, key, Cipher.ENCRYPT_MODE);

		SecureRandom rand = new SecureRandom();
		byte[] proxy_challenge = new byte[32];
		byte[] iv = new byte[16];

		KeyGenerator gen;
		SecretKey sec_key = null;
		try {
			gen = KeyGenerator.getInstance("AES");
			gen.init(256);
			sec_key = gen.generateKey();

		} catch (NoSuchAlgorithmException e1) {
			// may not happen
			e1.printStackTrace();
		}
		// byte[] sec_key = new byte[32];
		rand.nextBytes(proxy_challenge);
		rand.nextBytes(iv);
		// rand.nextBytes(sec_key);

		LoginMessageOk sec = new LoginMessageOk(Base64.encode(lr.getChallenge()), Base64.encode(proxy_challenge),
				Base64.encode(sec_key.getEncoded()), Base64.encode(iv));
		channel_out.write(sec);

		channel_in = new AESChannel(base_channel, sec_key, iv);
		channel_out = channel_in;

		try {
			Object o = channel_in.read();
			LoginMessageFinal resp = (LoginMessageFinal) o;
			byte[] solved_challenge = Base64.decode(resp.getChallenge());

			if (!Arrays.equals(solved_challenge, proxy_challenge))
				return new LoginResponse(Type.WRONG_CREDENTIALS);

		} catch (IOException e) {
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}

		this.user = user;
		return new LoginResponse(Type.SUCCESS);
	}

	@Override
	public Response credits() throws IOException {

		if (user == null)
			return new MessageResponse("Not Logged in");

		return new CreditsResponse(user.getCredits());
	}

	@Override
	public Response buy(BuyRequest credits) throws IOException {
		user.addCredits(credits.getCredits());
		return new BuyResponse(credits.getCredits());
	}

	@Override
	public Response list() throws IOException {
		if (user == null)
			return new MessageResponse("You have to login first");

		try {

			if (parent.getOnlineServer().isEmpty()) {
				return new MessageResponse("No Fileserver available");
			}
			Set<String> fileNames = new HashSet<String>();

			for (MyFileServerInfo ser : parent.getOnlineServer()) {
				// Ask the Fileserver what files he has
				ListRequest lreq = new ListRequest();
				ListResponse listResponseObj = retryableRequestToFileserver(ser, lreq, 1, ListResponse.class);
				fileNames.addAll(listResponseObj.getFileNames());
			}
			return new ListResponse(fileNames);

		} catch (RequestFailedException e) {
			return new MessageResponse("List Response Failed");
		}
	}

	@Override
	public Response download(DownloadTicketRequest request) throws IOException {

		if (user == null)
			return new MessageResponse("You have to login first");

		try {
			Set<MyFileServerInfo> onlineServer = parent.getOnlineServer();
			int NumberNR = (int) Math.ceil(onlineServer.size() / 2.0);
			Set<MyFileServerInfo> readQuorum = getQuorum(onlineServer, NumberNR);

			if (readQuorum.isEmpty()) {
				return new MessageResponse("No Fileserver available");
			}

			boolean filefound = false;

			int newestVersion = -2;
			MyFileServerInfo newestVersionServer = null;
			for (MyFileServerInfo server : readQuorum) {
				VersionRequest vreq = new VersionRequest(request.getFilename());

				// -1 not exist
				int version = ((VersionResponse) (retryableRequestToFileserver(server, vreq, 1, VersionResponse.class)))
						.getVersion();

				if (version > newestVersion) // TODO: aufsteigend oder
												// absteigend sortiert wegen
												// usage?
				{
					newestVersion = version;
					newestVersionServer = server;
				}
			}

			if (newestVersion == -1)
				return new MessageResponse("File \"" + request.getFilename() + "\" does not exist");

			InfoRequest ireq = new InfoRequest(request.getFilename());
			InfoResponse infoResponseObj = retryableRequestToFileserver(newestVersionServer, ireq, 1,
					InfoResponse.class);

			if (user.getCredits() < infoResponseObj.getSize())
				return new MessageResponse("Not enough Credits");

			String checksum = ChecksumUtils.generateChecksum(user.getName(), request.getFilename(), newestVersion,
					infoResponseObj.getSize());

			DownloadTicket ticket = new DownloadTicket(user.getName(), request.getFilename(), checksum,
					newestVersionServer.getAddress(), newestVersionServer.getTcpport());
			user.addCredits(-infoResponseObj.getSize());

			parent.getManagementComonent().incDownloads(request.getFilename());

			newestVersionServer.incUsage(infoResponseObj.getSize());

			parent.updateFileserverInKnownfileservers(newestVersionServer);

			DownloadTicketResponse response = new DownloadTicketResponse(ticket);
			return response;

		} catch (RequestFailedException e) {
			return new MessageResponse("Download Failed");
		}
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {
		if (user == null)
			return new MessageResponse("You have to login first");
		try {

			Set<MyFileServerInfo> servers = parent.getOnlineServer();
			int NumberNR = (int) Math.ceil(servers.size() / 2.0);
			int NumberNW = (int) Math.floor(servers.size() / 2.0) + 1;
			Set<MyFileServerInfo> readQuorum = getQuorum(servers, NumberNR);
			Set<MyFileServerInfo> writeQuorum = getQuorum(servers, NumberNW);
			
			if (readQuorum.isEmpty()) {
				return new MessageResponse("No Fileserver available");
			}
			int version = -1;

			for (MyFileServerInfo server : readQuorum) {

				VersionRequest vreq = new VersionRequest(request.getFilename());
				version = Math.max(
						((VersionResponse) (retryableRequestToFileserver(server, vreq, 1, VersionResponse.class)))
								.getVersion(), version);
			}

			if (version != -1) {
				version++;
			} else {
				version = 1;
			}

			FileInfo info = new FileInfo(request.getFilename(), request.getContent().length, request.getContent());
			distributeFile(writeQuorum, info, version);
			user.addCredits(2 * request.getContent().length);
			return new MessageResponse("File: " + info.getName() + " has been uploaded");

		} catch (RequestFailedException e) {
			return new MessageResponse("Upload Failed");
		}
	}

	@Override
	public MessageResponse logout() throws IOException {
		if (user != null)
			parent.getManagementComonent().removeSubscriptions(user.getName());
		user = null;
		return new MessageResponse("User logged out");
	}

	public User getUser() {
		return user;
	}

	private Set<MyFileServerInfo> getQuorum(Set<MyFileServerInfo> known, int quorumSize) {

		SortedSet<MyFileServerInfo> writeQuorum = new TreeSet<MyFileServerInfo>();
		Iterator<MyFileServerInfo> it = known.iterator();
		for (int i = 0; i < quorumSize; i++)
			writeQuorum.add(it.next());

		return writeQuorum;
	}

	static {
		try {
			commandMap.put(LoginRequest.class, ProxySession.class.getMethod("login", LoginRequest.class));
			hasArgument.add(LoginRequest.class);

			commandMap.put(CreditsRequest.class, ProxySession.class.getMethod("credits"));

			commandMap.put(LogoutRequest.class, ProxySession.class.getMethod("logout"));

			commandMap.put(BuyRequest.class, ProxySession.class.getMethod("buy", BuyRequest.class));
			hasArgument.add(BuyRequest.class);

			commandMap.put(DownloadTicketRequest.class,
					ProxySession.class.getMethod("download", DownloadTicketRequest.class));
			hasArgument.add(DownloadTicketRequest.class);

			commandMap.put(UploadRequest.class, ProxySession.class.getMethod("upload", UploadRequest.class));
			hasArgument.add(UploadRequest.class);

			commandMap.put(ListRequest.class, ProxySession.class.getMethod("list"));

		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	public <T extends Response> T retryableRequestToFileserver(MyFileServerInfo server, Request req, int retryCounter,
			Class<? extends Response> responseClass) throws RequestFailedException {

		Channel versionRequest = null;
		try {
			versionRequest = new HMACChannel(new TCPChannel(server.createSocket()), hmac);
			versionRequest.write(req);

			return (T) responseClass.cast(versionRequest.read());

		} catch (ClassCastException e) {
			// we received a wrong object, try again
			if (retryCounter > 0)
				return retryableRequestToFileserver(server, req, retryCounter - 1, responseClass);
			else
				throw new RequestFailedException();
		} catch (IOException e) {
			if (retryCounter > 0)
				return retryableRequestToFileserver(server, req, retryCounter - 1, responseClass);
			else
				throw new RequestFailedException();
		} finally {
			if (versionRequest != null)
				versionRequest.close();
		}
	}

	public void distributeFile(Set<MyFileServerInfo> writeQuorum, FileInfo info, int version) throws IOException,
			RequestFailedException {

		for (MyFileServerInfo server : writeQuorum) {

			UploadRequest requestObj = new UploadRequest(info.getName(), version, info.getContent());
			MessageResponse resp = retryableRequestToFileserver(server, requestObj, 1, MessageResponse.class);

			// Response res = uploadRequest(info, version, server);
			// if (res == null || res instanceof MessageIntegrityErrorResponse)
			// {
			// // Try again if failed
			// res = uploadRequest(info, version, server);
			//
			// // TODO: what todo if upload fails 2 times ?
			// }
		}
	}
}
