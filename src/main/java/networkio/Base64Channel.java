package networkio;

import java.io.IOException;

import org.bouncycastle.util.encoders.Base64;

public class Base64Channel extends Channel {

	Channel parent;
	
	public Base64Channel(Channel parent)
	{
		this.parent = parent;
	}

	@Override
	public byte[] readBytes() throws IOException {
		byte [] data = parent.readBytes();
		byte [] dec = Base64.decode(data);
		return dec;
	}

	@Override
	public void write(byte[] data) throws IOException {
		
		parent.write(Base64.encode(data));
	}

	@Override
	public void close() {
		parent.close();
	}
}
