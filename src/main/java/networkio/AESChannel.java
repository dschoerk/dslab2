package networkio;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;

public class AESChannel extends Channel {

	private Channel parent;
	private Cipher cipher_enc;
	private Cipher cipher_dec;
	
	public AESChannel(Channel channel, Key key, byte []iv)
	{
		this.parent = channel;
		
		try {
			cipher_enc = Cipher.getInstance("AES/CTR/NoPadding");
			cipher_enc.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
			
			cipher_dec = Cipher.getInstance("AES/CTR/NoPadding");
			cipher_dec.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));
		
		} catch (NoSuchAlgorithmException e) {
			// may not happen
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// may not happen
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// may not happen
			e.printStackTrace();
		} catch (InvalidAlgorithmParameterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void write(byte[] data) throws IOException {
		
		try {
			byte[] enc = cipher_enc.doFinal(data);
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
	public byte[] readBytes() throws ClassNotFoundException, IOException {
		
		byte[] data = parent.readBytes();
		try {
			byte [] dec = cipher_dec.doFinal(data);
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
