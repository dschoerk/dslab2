package networkio;

import java.io.IOException;
import java.io.Serializable;
import java.security.MessageDigest;

import javax.crypto.Mac;

import org.bouncycastle.util.encoders.Base64;

public class HMACWrapped implements Serializable {

	private static final long serialVersionUID = 3599128059842422585L;
	private byte [] object;
	private byte [] checksum;
	
	public HMACWrapped(Object o, Mac hmac) throws IOException
	{
		byte [] enc = Serializer.encode(o);
		checksum = Base64.encode(hmac.doFinal(enc));
		object = enc;
	}
	
	public HMACWrapped(byte [] obj, byte [] checksum)
	{
		this.object = obj;
		this.checksum = Base64.encode(checksum);
	}
	
	public boolean isChecksumCorrect(Mac hmac)
	{
		return MessageDigest.isEqual(checksum, Base64.encode(hmac.doFinal(object)));
	}
	
	public Object getObject() throws IOException
	{
		try {
			return Serializer.decode(object);
		} catch (ClassNotFoundException e) {
			// does not happen
			e.printStackTrace();
			return null;
		}
	}
}
