package your;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;

import message.Request;
import message.Response;

public class SimpleTcpRequest<Req extends Request, Resp extends Response> {

	private Socket socket;
	private ObjectInputStream in;
	private ObjectOutputStream out;

	public SimpleTcpRequest(InetAddress adress, int port) throws IOException {
		socket = new Socket(adress, port);
		createStreams();
	}

	public SimpleTcpRequest(Socket s) throws IOException {
		this.socket = s;
		createStreams();
	}
	
	private void createStreams() throws IOException
	{
		in = new ObjectInputStream(socket.getInputStream());
		out = new ObjectOutputStream(socket.getOutputStream());
	}

	public void writeRequest(Req request) throws IOException {
		out.writeObject(request);
	}

	public Resp waitForResponse() throws IOException {
		try {
			return (Resp) in.readObject();
		} catch (ClassNotFoundException e) {
			// May NOT happen
			e.printStackTrace();
		}
		
		return null;
	}

	public void close() throws IOException {
		in.close();
		out.close();
		socket.close();
	}
}
