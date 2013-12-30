package message;

import java.io.Serializable;

public class LoginMessage3rd implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 8677335398316199538L;
	private byte[] challenge;

	public LoginMessage3rd(byte[] challenge) {
		this.challenge = challenge;
	}

	public byte[] getChallenge() {
		return challenge;
	}
}
