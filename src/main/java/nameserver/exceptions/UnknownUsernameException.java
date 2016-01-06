package nameserver.exceptions;

public class UnknownUsernameException extends Exception {
	private static final long serialVersionUID = 1L;

	public UnknownUsernameException(String message) {
		super(message);
	}

	public UnknownUsernameException(String message, Throwable cause) {
		super(message, cause);
	}
}
