package your;

public class FileInfo {

	private long size;
	private String name;
	private byte[] data;

	public FileInfo(String name, long size) {
		this.name = name;
		this.size = size;
		this.data = null;
	}

	public FileInfo(String name, long size, byte[] data) {
		this.name = name;
		this.size = size;
		this.data = data;
	}

	public long getSize() {
		return size;
	}

	public String getName() {
		return name;
	}

	public byte[] getContent() {
		return data;
	}
}
