package your;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;

import model.FileServerInfo;

public class MyFileServerInfo implements Comparable<MyFileServerInfo> {

	private long lastAliveMessage;
	private int tcpport;
	private boolean online;
	private long usage;
	private InetAddress address;
	private int port;

	public MyFileServerInfo(InetAddress address, int port, long usage, boolean online, int tcpport) {
		this.address = address;
		this.port = port;
		this.tcpport = tcpport;
		this.online = online;
		this.usage = usage;
	}

	public int getTcpport() {
		return tcpport;
	}

	public Socket createSocket() throws IOException {
		return new Socket(address, tcpport);
	}

	public void setAlive() {
		lastAliveMessage = System.currentTimeMillis();
	}

	public FileServerInfo Info() {
		return new FileServerInfo(address, port, usage, online);
	}

	public void updateOnlineStatus(int timeout) {
		online = System.currentTimeMillis() - lastAliveMessage < timeout;
	}

	public long getUsage() {
		return usage;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof MyFileServerInfo))
			return false;

		MyFileServerInfo other = (MyFileServerInfo) o;
		if (address.equals(other.address) && port == other.port) {
			return true;
		}

		return false;
	}

	public static MyFileServerInfo minimumUsage(List<MyFileServerInfo> list) {
		MyFileServerInfo minimumusage = null;
		for (MyFileServerInfo i : list) {
			if (!i.isOnline())
				continue;

			if (minimumusage == null || minimumusage.getUsage() > i.getUsage())
				minimumusage = i;
		}
		return minimumusage;
	}

	public boolean isOnline() {
		return online;
	}

	public void incUsage(long size) {
		usage += size;
	}

	public InetAddress getAddress() {
		return address;
	}

	@Override
	public int compareTo(MyFileServerInfo o) {
		//return (int)(usage-o.usage);
	    return usage <= o.getUsage() ? -1:1;
	}
}
