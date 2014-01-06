package networkio;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UDPChannel extends Channel {

	private DatagramSocket socket;
	private DatagramPacket p; // latest read packet
	
	private InetAddress receiverAddress;
	private int port;

	public UDPChannel(DatagramSocket socket,InetAddress receiverAddress, int proxyudpport) throws IOException {
		this.socket = socket;
		this.port = proxyudpport;
		this.receiverAddress = receiverAddress;
	}
	
	public UDPChannel(DatagramSocket socket) throws IOException {
		this.socket = socket;
	}


	public DatagramPacket getLatestPacket()
	{
		return p;
	}
	
	@Override
	public byte[] readBytes() throws IOException {
		
		byte [] buf = new byte[256];
		p = new DatagramPacket(buf, buf.length);
		socket.receive(p);
		
		DataInputStream dis = new DataInputStream(new ByteArrayInputStream(buf));
		int paketLength = dis.readInt();
		dis.close();
		
		byte [] data = new byte[paketLength]; 
		dis.read(data);
		
		return data;
	}

	@Override
	public void write(byte[] data) throws IOException {
		
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		dos.writeInt(data.length);
		dos.write(data);
		byte [] encoded = bos.toByteArray();
		dos.close();
		bos.close();
		
		DatagramPacket p = new DatagramPacket(encoded, encoded.length, receiverAddress, port);
		socket.send(p);
	}

	@Override
	public void close() {
		socket.close();
	}
}
