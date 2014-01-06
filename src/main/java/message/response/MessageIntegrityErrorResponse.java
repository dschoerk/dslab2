package message.response;

import message.Response;

/**
 * Sends a custom message to the receiver.
 */
public class MessageIntegrityErrorResponse implements Response {

	private static final long serialVersionUID = -6559716684681260153L;
	
	@Override
	public String toString()
	{
		return "MessageIntegrityErrorResponse";
	}
}
