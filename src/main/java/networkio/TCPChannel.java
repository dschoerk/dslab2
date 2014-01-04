package networkio;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

public class TCPChannel extends Channel {

	private DataInputStream is;
	private DataOutputStream os;
	private Socket socket;

	public TCPChannel(Socket socket) throws IOException {
		is = new DataInputStream(socket.getInputStream());
		os = new DataOutputStream(socket.getOutputStream());
		this.socket = socket;
	}

	@Override
	public byte[] readBytes() throws ClassNotFoundException, IOException {
		int size = is.readInt();
		assert size > 0;
		
		byte [] buffer = new byte[size];
		is.read(buffer);
		return buffer;
	}

	@Override
	public void write(byte[] data) throws IOException {
		os.writeInt(data.length);
		os.write(data);
	}

	@Override
	public void close() throws IOException {
		is.close();
		os.close();
		socket.close();
	}
}
