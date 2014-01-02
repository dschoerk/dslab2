package networkio;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

public class HMACChannel extends Channel {

	private Channel parent;
	private Mac hMac;

	private boolean correctChecksum;

	public HMACChannel(Channel parent, SecretKey key) {
		this.parent = parent;
		try {
			hMac = Mac.getInstance("HmacSHA256");
			hMac.init(key);
		} catch (NoSuchAlgorithmException e) {
			// does not happen
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// does not happen
			e.printStackTrace();
		}
	}

	@Override
	public void write(byte[] data) throws IOException {

		byte[] checksum = hMac.doFinal(data);
		parent.write(checksum);
		parent.write(data);
	}

	@Override
	public byte[] readBytes() throws ClassNotFoundException, IOException {

		byte[] data = parent.readBytes();
		byte[] sentChecksum = parent.readBytes();
		correctChecksum = MessageDigest.isEqual(sentChecksum, hMac.doFinal(data));
		return data;
	}

	public boolean isChecksumCorrect() {
		return correctChecksum;
	}

	@Override
	public void close() throws IOException {
		parent.close();
	}

}
