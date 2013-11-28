package your;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class FileIo {
	// http://stackoverflow.com/questions/11741804/java-file-to-byte-array-fast-one
	public static void copy(InputStream input, OutputStream output, int bufferSize) throws IOException {
		byte[] buf = new byte[bufferSize];
		int bytesRead = input.read(buf);
		while (bytesRead != -1) {
			output.write(buf, 0, bytesRead);
			bytesRead = input.read(buf);
		}
		output.flush();
	}
}
