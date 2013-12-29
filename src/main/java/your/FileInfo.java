package your;

import java.util.Comparator;

public class FileInfo implements Comparable<FileInfo>{

	private long size;
	private String name;
	private byte[] data;
    private int downloadCnt;

	public FileInfo(String name, long size) {
		this.name = name;
		this.size = size;
		this.data = null;
		
		downloadCnt = 0;
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
	
	public int getDownloadCnt() {
        return downloadCnt;
    }

    public void incDownloadCnt(){
	    downloadCnt++;
	}

    @Override
    public int compareTo(FileInfo o) {
        return getDownloadCnt()<=o.getDownloadCnt() ? 1 : -1;
    }

}
