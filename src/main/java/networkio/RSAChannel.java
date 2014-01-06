package networkio;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

public class RSAChannel extends Channel {

	private Channel parent;
	private Cipher cipher;
	
	public RSAChannel(Channel channel, Key key, int mode)
	{
		this.parent = channel;
		
		try {
			cipher = Cipher.getInstance("RSA/NONE/OAEPWithSHA256AndMGF1Padding");
			cipher.init(mode, key);
		
		} catch (NoSuchAlgorithmException e) {
			// may not happen
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// may not happen
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// may not happen
			e.printStackTrace();
		}
	}

	@Override
	public void write(byte[] data) throws IOException {
		
		try {
			byte[] enc = cipher.doFinal(data);
			parent.write(enc);
		
		} catch (IllegalBlockSizeException e) {
			// may not happen
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// may not happen
			e.printStackTrace();
		}
	}

	@Override
	public byte[] readBytes() throws IOException {
		
		byte[] data = parent.readBytes();
		try {
			byte [] dec = cipher.doFinal(data);
			return dec;
		
		} catch (IllegalBlockSizeException e) {
			// may not happen
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// may not happen
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	public void close() throws IOException {
		parent.close();
		
	}

}
