package networkio;

import java.io.IOException;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.security.MessageDigest;

import javax.crypto.Mac;

import org.bouncycastle.util.encoders.Base64;

public class HMACWrapped implements Serializable {

	private static final long serialVersionUID = 3599128059842422585L;
	private byte[] checksum;
	private byte[] object;
	

	public HMACWrapped(Object o, Mac hmac) throws IOException {
		byte[] enc = Serializer.encode(o);
		checksum = Base64.encode(hmac.doFinal(o.toString().getBytes()));
		object = enc;
	}

	public HMACWrapped(byte[] obj, byte[] checksum) {
		this.object = obj;
		this.checksum = Base64.encode(checksum);
	}

	public boolean isChecksumCorrect(Mac hmac) throws IllegalStateException, IOException {
		try {
			return MessageDigest.isEqual(checksum,
					Base64.encode(hmac.doFinal(Serializer.decode(object).toString().getBytes())));
		} catch (StreamCorruptedException e) {
			return false;
		}
	}

	public Object getObject() throws IOException {

		return Serializer.decode(object);
	}
	
	public byte [] getObjectBytes() throws IOException {

		return object;
	}
	
	@Override
	public String toString()
	{
		return new String(checksum)+" "+new String(object);
	}
}
