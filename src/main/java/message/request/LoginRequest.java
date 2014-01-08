package message.request;

import message.Request;

/**
 * Authenticates the client with the provided username and password.
 * <p/>
 * <b>Request</b>:<br/>
 * {@code !login &lt;username&gt; &lt;password&gt;}<br/>
 * <b>Response:</b><br/>
 * {@code !login success}<br/>
 * or<br/>
 * {@code !login wrong_credentials}
 * 
 * @see message.response.LoginResponse
 */
public class LoginRequest implements Request {
	private static final long serialVersionUID = -1596776158259072949L;

	private final String username;
	private byte[] challenge;

	// private final String password;

	public LoginRequest(String username, byte[] challenge/* , String password */) {
		this.username = username;
		// this.password = password;
		this.challenge = challenge;
	}

	public String getUsername() {
		return username;
	}

	public byte[] getChallenge() {
		return challenge;
	}

	// public String getPassword() {
	// return password;
	// }

	@Override
	public String toString() {
		return String.format("!login %s %s", getUsername()/* , getPassword() */, new String(challenge));
	}
}
