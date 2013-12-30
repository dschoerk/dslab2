package your;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ExportException;
import java.rmi.server.UnicastRemoteObject;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import management.MessageInterface;
import management.RMICallbackInterface;
import message.LoginMessage2nd;
import message.LoginMessage3rd;
import message.Response;
import message.request.BuyRequest;
import message.request.CreditsRequest;
import message.request.DownloadFileRequest;
import message.request.DownloadTicketRequest;
import message.request.ListRequest;
import message.request.LoginRequest;
import message.request.LogoutRequest;
import message.request.UploadRequest;
import message.response.BuyResponse;
import message.response.CreditsResponse;
import message.response.DownloadFileResponse;
import message.response.DownloadTicketResponse;
import message.response.LoginResponse;
import message.response.LoginResponse.Type;
import message.response.MessageResponse;
import model.DownloadTicket;
import networkio.AESChannel;
import networkio.Base64Channel;
import networkio.Channel;
import networkio.RSAChannel;
import networkio.TCPChannel;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.util.encoders.Base64;

import util.ComponentFactory;
import util.Config;
import cli.Command;
import cli.Shell;
import client.IClientCli;

public class Client implements IClientCli, RMICallbackInterface {

	private Socket socket;
	private Channel channel;
	private Channel rsaChannelToProxy;
	private Channel rsaChannelFromProxy;
	private Channel aesChannel;

	private File downloadDirectory;
	private File keyDir;

	MessageInterface managementComponent;

	public static void main(String[] args) throws Exception {

		ComponentFactory factory = new ComponentFactory();
		Shell shell = new Shell("Client", System.out, System.in);
		Config cfg = new Config("client");
		factory.startClient(cfg, shell);
	}

	public Client(File downloadDir, String host, int port, PublicKey pubk, File keyDir, Shell shell) {

		if (shell != null) {
			shell.register(this);
			Thread shellThread = new Thread(shell);
			shellThread.start();
		}

		this.downloadDirectory = downloadDir;
		this.keyDir = keyDir;

		try {
			socket = new Socket(host, port);
			channel = new Base64Channel(new TCPChannel(socket));
			rsaChannelToProxy = new RSAChannel(channel, pubk, Cipher.ENCRYPT_MODE);
			// out = new ObjectOutputStream(socket.getOutputStream());
			// out.flush();
			// in = new ObjectInputStream(socket.getInputStream());

			Config cfg_mc = new Config("mc");
			Registry registry = LocateRegistry.getRegistry(cfg_mc.getString("proxy.host"),
					cfg_mc.getInt("proxy.rmi.port"));

			managementComponent = (MessageInterface) registry.lookup("Proxy");

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NotBoundException e) {
			// TODO Auto-generated catch block
			System.out.println("Proxy RMI not registered");
			e.printStackTrace();
		}
	}

	private PrivateKey getUserKey(String username, final String password) {
		System.out.println(keyDir.listFiles().length);
		for (File s : keyDir.listFiles()) {
			if (s.getName().equals(username + ".pem")) {
				System.out.println("found");
				try {
					PEMReader in = new PEMReader(new FileReader(s.getAbsolutePath()), new PasswordFinder() {
						@Override
						public char[] getPassword() {
							return password.toCharArray();
						}
					});

					KeyPair keyPair = (KeyPair) in.readObject();
					PrivateKey privKey = keyPair.getPrivate();
					in.close();
					return privKey;

				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}

	@Command
	@Override
	public LoginResponse login(String username, String password) throws IOException {

		Key privk = getUserKey(username, password); // read private key for
													// username
		rsaChannelFromProxy = new RSAChannel(channel, privk, Cipher.DECRYPT_MODE);

		SecureRandom rand = new SecureRandom();
		byte[] client_challenge = new byte[32];
		rand.nextBytes(client_challenge);
		client_challenge = Base64.encode(client_challenge);

		LoginRequest message = new LoginRequest(username, client_challenge);

		Object response;
		try {
			rsaChannelToProxy.write(message);
			response = (LoginMessage2nd) rsaChannelFromProxy.read();
			LoginMessage2nd msg_2nd = (LoginMessage2nd) response;

			byte[] key = Base64.decode(msg_2nd.getSecretKey());
			SecretKey originalKey = new SecretKeySpec(key, 0, key.length, "AES");
			byte[] iv = Base64.decode(msg_2nd.getIV());
			channel = new AESChannel(channel, originalKey, iv);

			LoginMessage3rd msg_3rd = new LoginMessage3rd(msg_2nd.getProxyChallenge());
			channel.write(msg_3rd);

			response = channel.read();
			LoginResponse loginresponse = (LoginResponse) response;
			return loginresponse;

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		} catch (SocketException e) {
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		} catch (IOException e) {
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}
	}

	@Command
	@Override
	public Response credits() throws IOException {

		CreditsRequest message = new CreditsRequest();

		Response response;
		try {
			channel.write(message);
			response = (Response) channel.read();

			if (response instanceof MessageResponse) {
				return response;
			}

			CreditsResponse credits = (CreditsResponse) response;
			return new MessageResponse("You have " + credits.getCredits() + " left!");

		} catch (ClassNotFoundException e) {
			throw new IOException("Failed Credits Request");
		} catch (SocketException e) {
			return new MessageResponse("Lost Connection");
		}
	}

	@Command
	@Override
	public Response buy(long credits) throws IOException {
		BuyRequest message = new BuyRequest(credits);

		Object response;
		try {
			channel.write(message);
			response = (Response) channel.read();
			BuyResponse buyresponse = (BuyResponse) response;
			return new MessageResponse("You now have " + buyresponse.getCredits() + "!");

		} catch (ClassNotFoundException e) {
			throw new IOException("Failed Buy Request");
		} catch (SocketException e) {
			return new MessageResponse("Lost Connection");
		}
	}

	@Command
	@Override
	public Response list() throws IOException {

		ListRequest req = new ListRequest();

		Response response;
		try {
			channel.write(req);
			response = (Response) channel.read();
			return response;

		} catch (ClassNotFoundException e) {
			// may not happen
			e.printStackTrace();
		} catch (SocketException e) {
			return new MessageResponse("Lost Connection");
		}
		return new MessageResponse("Error while reading Files");
	}

	@Command
	@Override
	public Response download(String filename) throws IOException {

		DownloadTicketRequest message = new DownloadTicketRequest(filename);
		Object response;
		try {
			channel.write(message);
			response = (Response) channel.read();

			if (response instanceof MessageResponse) {
				return (Response) response;
			}

			DownloadTicketResponse ticketResponse = (DownloadTicketResponse) response;
			DownloadTicket ticket = ticketResponse.getTicket();

			Socket s = new Socket(ticket.getAddress(), ticket.getPort());
			SimpleTcpRequest<DownloadFileRequest, Response> req = new SimpleTcpRequest<DownloadFileRequest, Response>(s);
			req.writeRequest(new DownloadFileRequest(ticket));
			Response downloadResponse = req.waitForResponse();

			if (downloadResponse instanceof MessageResponse) {
				return (Response) downloadResponse;
			}
			DownloadFileResponse downloadResponseCasted = (DownloadFileResponse) downloadResponse;

			return downloadResponseCasted;

		} catch (ClassNotFoundException e) {
			throw new IOException("Failed Buy Request");
		} catch (SocketException e) {
			return new MessageResponse("Lost Connection");
		}
	}

	@Command
	@Override
	public MessageResponse upload(String filename) throws IOException {
		File f = new File(downloadDirectory.getAbsolutePath() + "/" + filename);
		if (!f.exists())
			return new MessageResponse("file does not exist");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		FileIo.copy(new FileInputStream(f.getPath()), baos, 128);
		byte[] data = baos.toByteArray();

		UploadRequest upload = new UploadRequest(filename, 0, data);

		Object response;
		try {
			channel.write(upload);
			response = (Response) channel.read();
			MessageResponse uploadresponse = (MessageResponse) response;
			return uploadresponse;

		} catch (ClassNotFoundException e) {
			throw new IOException("Failed Buy Request");
		} catch (SocketException e) {
			return new MessageResponse("Lost Connection");
		}
	}

	@Command
	@Override
	public MessageResponse logout() throws IOException {
		LogoutRequest message = new LogoutRequest();
		channel.write(message);

		// muss gelesen werden!
		try {
			MessageResponse uploadresponse = (MessageResponse) channel.read();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return new MessageResponse("Successfully logged out.");
	}

	@Command
	public MessageResponse readQuorum() throws IOException {

		return new MessageResponse("" + managementComponent.getReadQuorum());
	}

	@Command
	public MessageResponse writeQuorum() throws IOException {

		return new MessageResponse("" + managementComponent.getWriteQuorum());
	}

	@Command
	public MessageResponse topThreeDownloads() throws IOException {
		String out = "";
		List<String> top = managementComponent.getTopThree();
		for (String line : top) {
			out += line;
		}
		return new MessageResponse(out);
	}

	@Command
	public MessageResponse subscribe(String file, int trigger) throws IOException {
		try {
			UnicastRemoteObject.exportObject(this, 0);
		} catch (ExportException E) {
			System.out.println("reexport");
		}
		RMICallbackInterface callback = (RMICallbackInterface) this;
		managementComponent.subscribe(callback, "", file, trigger);
		return new MessageResponse("Successfully subscribed for file: " + file);
	}

	@Command
	public MessageResponse getProxyPublicKey() throws IOException {

		return new MessageResponse("TODO: IMPLEMENT!!!");
	}

	@Command
	public MessageResponse setUserPublicKey(String user) throws IOException {

		return new MessageResponse("TODO: IMPLEMENT!!!");
	}

	@Command
	@Override
	public MessageResponse exit() throws IOException {
		socket.close();
		System.in.close();
		try {
			UnicastRemoteObject.unexportObject(this, true);
		} catch (Exception e) {
		}
		return null;
	}

	@Override
	public void notifySubscriber(String file, int trigger) {
		System.out.println("notify!!!");

	}
}
