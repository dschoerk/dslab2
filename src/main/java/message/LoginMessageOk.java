package message;

import java.io.Serializable;
import java.security.Key;

public class LoginMessageOk implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = -2858743037316154617L;
	private byte[] client_challenge;
	private byte[] proxy_challenge;
	private byte[] secret_key;
	private byte[] iv;

	public LoginMessageOk(byte[] client_challenge, byte[] proxy_challenge, byte[] secret_key, byte[] iv) {
		this.client_challenge = client_challenge;
		this.proxy_challenge = proxy_challenge;
		this.secret_key = secret_key;
		this.iv = iv;
	}

	public byte[] getClientChallenge() {
		return client_challenge;
	}

	public byte[] getProxyChallenge() {
		return proxy_challenge;
	}

	public byte[] getSecretKey() {
		return secret_key;
	}

	public byte[] getIV() {
		return iv;
	}

	public String toString() {
		return "!ok " + new String(client_challenge) + " " + new String(proxy_challenge) + " " + new String(secret_key)
				+ " " + new String(iv);
	}
}
