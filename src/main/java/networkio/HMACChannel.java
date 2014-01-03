package networkio;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;

public class HMACChannel extends Channel {

	private Channel parent;
	private Mac hMac;

	private boolean correctChecksum;

	public HMACChannel(Channel parent, Mac hmac) {
		this.parent = parent;
		this.hMac = hmac;
	}

	@Override
	public void write(byte[] data) throws IOException {
		
		HMACWrapped w = new HMACWrapped(data, hMac.doFinal(data));
		parent.write(Serializer.encode(w));
	}

	@Override
	public byte[] readBytes() throws ClassNotFoundException, IOException {

		byte[] data = parent.readBytes();
		HMACWrapped w = (HMACWrapped)Serializer.decode(data);
		correctChecksum = w.isChecksumCorrect(hMac);
		return Serializer.encode(w.getObject());
	}

	public boolean isChecksumCorrect() {
		return correctChecksum;
	}

	@Override
	public void close() throws IOException {
		parent.close();
	}

}
