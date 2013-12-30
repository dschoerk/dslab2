package your;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;

import org.bouncycastle.util.encoders.Base64;

import message.LoginMessage2nd;
import message.LoginMessage3rd;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadTicketRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.DownloadTicketResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.LoginResponse;
import message.response.LoginResponse.Type;
import message.response.MessageResponse;
import model.DownloadTicket;
import networkio.Base64Channel;
import networkio.Channel;
import networkio.RSAChannel;
import networkio.TCPChannel;
import proxy.IProxy;
import util.ChecksumUtils;

public class ProxySession implements Runnable, IProxy {

	private Socket socket;
	private ObjectInputStream in;
	private ObjectOutputStream out;
	private Channel base_channel;
	private Channel channel_in;
	private Channel channel_out;

	private UserDB users;

	private User user = null;
	private boolean running = true;

	private static Map<Class<?>, Method> commandMap = new HashMap<Class<?>, Method>();
	private static Set<Class<?>> hasArgument = new HashSet<Class<?>>();

	private Proxy parent;

	public ProxySession(Socket s, Proxy parent) {
		this.socket = s;
		this.parent = parent;
		this.users = parent.getUserDB();

		try {
			// out = new ObjectOutputStream(socket.getOutputStream());
			// out.flush();
			// in = new ObjectInputStream(socket.getInputStream());

			out = null;
			in = null;

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
				// System.out.println("incoming Request: " + o.getClass());

				Object response = null;
				if (hasArgument.contains(o.getClass())) {
					response = commandMap.get(o.getClass()).invoke(this, o);
				} else {
					response = commandMap.get(o.getClass()).invoke(this);
				}
				channel_out.write(response);

			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
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
		byte[] sec_key = new byte[32];
		rand.nextBytes(proxy_challenge);
		rand.nextBytes(iv);
		rand.nextBytes(sec_key);

		LoginMessage2nd sec = new LoginMessage2nd(
				Base64.encode(lr.getChallenge()), 
				Base64.encode(proxy_challenge), 
				Base64.encode(sec_key), 
				Base64.encode(iv));
		channel_out.write(sec);

		try {
			Object o = channel_in.read();
			LoginMessage3rd resp = (LoginMessage3rd) o;
			byte[] solved_challenge = Base64.decode(resp.getChallenge());

			if (!Arrays.equals(solved_challenge, proxy_challenge))
				return new LoginResponse(Type.WRONG_CREDENTIALS);

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}

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

		Set<String> fileNames = new HashSet<String>();
		fileNames.addAll(parent.getFiles().keySet());
		return new ListResponse(fileNames);
	}

	@Override
	public Response download(DownloadTicketRequest request) throws IOException {
		if (user == null)
			return new MessageResponse("You have to login first");

		MyFileServerInfo fileserver = parent.getLeastUsedFileServer();

		if (fileserver == null)
			return new MessageResponse("No Fileserver available");

		SimpleTcpRequest<InfoRequest, InfoResponse> infoRequest = new SimpleTcpRequest<InfoRequest, InfoResponse>(
				fileserver.createSocket());
		InfoRequest infoRequestObj = new InfoRequest(request.getFilename());
		infoRequest.writeRequest(infoRequestObj);
		InfoResponse infoResponseObj = infoRequest.waitForResponse();
		infoRequest.close();

		if (infoResponseObj.getSize() < 0)
			return new MessageResponse("File \"" + request.getFilename() + "\" does not exist");

		if (user.getCredits() < infoResponseObj.getSize())
			return new MessageResponse("Not enough Credits");

		String checksum = ChecksumUtils.generateChecksum(user.getName(), request.getFilename(), 0,
				infoResponseObj.getSize());

		DownloadTicket ticket = new DownloadTicket(user.getName(), request.getFilename(), checksum,
				fileserver.getAddress(), fileserver.getTcpport());

		user.addCredits(-infoResponseObj.getSize());
		FileInfo file = parent.getFiles().get(infoResponseObj.getFilename());
		if(file!=null){
		    file.incDownloadCnt();
		    parent.getManagementComonent().updateSubscriptions(file);
		}
		
		fileserver.incUsage(infoResponseObj.getSize());

		DownloadTicketResponse response = new DownloadTicketResponse(ticket);
		return response;
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {

		if (user == null)
			return new MessageResponse("You have to login first");

		FileInfo info = new FileInfo(request.getFilename(), request.getContent().length, request.getContent());
		parent.distributeFile(info);

		user.addCredits(2 * request.getContent().length);
		return new MessageResponse("File: " + info.getName() + " has been uploaded");
	}

	@Override
	public MessageResponse logout() throws IOException {
	    if(user!=null) parent.getManagementComonent().removeSubscriptions(user.getName());
		user = null;
		return new MessageResponse("User logged out");
	}

	public User getUser() {
		return user;
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
}
