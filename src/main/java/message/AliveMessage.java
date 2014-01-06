package message;

import java.io.Serializable;

public class AliveMessage implements Serializable {

	private static final long serialVersionUID = 5345042290584029980L;
	private int port;

	public AliveMessage(int port) {
		this.port = port;
	}

	public int getPort() {
		return port;
	}
	
	@Override
	public String toString()
	{
		return "!alive "+port;
	}
}
