package your;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;

import util.ComponentFactory;
import util.Config;
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
import cli.Command;
import cli.Shell;
import client.IClientCli;

public class Client implements IClientCli {

	private Socket socket;
	private ObjectInputStream in;
	private ObjectOutputStream out;

	private File downloadDirectory;

	public static void main(String[] args) throws Exception {
		ComponentFactory factory = new ComponentFactory(); 
		Shell shell = new Shell("Client", System.out, System.in);
		Config cfg = new Config("client");
		factory.startClient(cfg, shell);
	}

	public Client(File downloadDir, String host, int port, Shell shell) {

		if (shell != null) {
			shell.register(this);
			Thread shellThread = new Thread(shell);
			shellThread.start();
		}

		this.downloadDirectory = downloadDir;

		try {
			socket = new Socket(host, port);

			out = new ObjectOutputStream(socket.getOutputStream());
			out.flush();
			in = new ObjectInputStream(socket.getInputStream());

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Command
	@Override
	public LoginResponse login(String username, String password) throws IOException {

		LoginRequest message = new LoginRequest(username, password);

		Object response;
		try {
			out.writeObject(message);
			response = (Response) in.readObject();
			LoginResponse loginresponse = (LoginResponse) response;
			return loginresponse;

		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}catch (SocketException e) {
			return new LoginResponse(Type.WRONG_CREDENTIALS);
		}
	}

	@Command
	@Override
	public Response credits() throws IOException {

		CreditsRequest message = new CreditsRequest();
		

		Response response;
		try {
			out.writeObject(message);
			response = (Response) in.readObject();

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
			out.writeObject(message);
			response = (Response) in.readObject();
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
			out.writeObject(req);
			response = (Response) in.readObject();
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
			out.writeObject(message);
			response = (Response) in.readObject();

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
			out.writeObject(upload);
			response = (Response) in.readObject();
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
		out.writeObject(message);

		return new MessageResponse("Successfully logged out.");
	}

	@Command
	@Override
	public MessageResponse exit() throws IOException {
		socket.close();
		System.in.close();
		return null;
	}
}
