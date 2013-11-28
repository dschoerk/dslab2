package util;

import java.io.File;

import proxy.IProxyCli;
import server.IFileServerCli;
import your.Client;
import your.Fileserver;
import your.Proxy;
import cli.Shell;
import client.IClientCli;

/**
 * Provides methods for starting an arbitrary amount of various components.
 */
public class ComponentFactory {
	/**
	 * Creates and starts a new client instance using the provided
	 * {@link Config} and {@link Shell}.
	 * 
	 * @param config
	 *            the configuration containing parameters such as connection
	 *            info
	 * @param shell
	 *            the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception
	 *             if an exception occurs
	 */
	public IClientCli startClient(Config config, Shell shell) throws Exception {
		// TODO: create a new client instance (including a Shell) and start it

		String host = config.getString("proxy.host");
		int port = config.getInt("proxy.tcp.port");
		String downloaddir = config.getString("download.dir");

		return new Client(new File(downloaddir), host, port, shell);
	}

	/**
	 * Creates and starts a new proxy instance using the provided {@link Config}
	 * and {@link Shell}.
	 * 
	 * @param config
	 *            the configuration containing parameters such as connection
	 *            info
	 * @param shell
	 *            the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception
	 *             if an exception occurs
	 */
	public IProxyCli startProxy(Config config, Shell shell) throws Exception {
		// TODO: create a new proxy instance (including a Shell) and start it
		int tcpPort = config.getInt("tcp.port");
		int udpPort = config.getInt("udp.port");
		int timeout = config.getInt("fileserver.timeout");
		int checkPeriod = config.getInt("fileserver.checkPeriod");

		return new Proxy(tcpPort, udpPort, timeout, checkPeriod, shell);
	}

	/**
	 * Creates and starts a new file server instance using the provided
	 * {@link Config} and {@link Shell}.
	 * 
	 * @param config
	 *            the configuration containing parameters such as connection
	 *            info
	 * @param shell
	 *            the {@code Shell} used for processing commands
	 * @return the created component after starting it successfully
	 * @throws Exception
	 *             if an exception occurs
	 */
	public IFileServerCli startFileServer(Config config, Shell shell)
			throws Exception {
		// TODO: create a new file server instance (including a Shell) and start
		// it
		String fileserverdir = config.getString("fileserver.dir");
		int tcpport = config.getInt("tcp.port");
		String proxyhost = config.getString("proxy.host");
		int proxyudpport = config.getInt("proxy.udp.port");
		int fileserverminalive = config.getInt("fileserver.alive");

		return new Fileserver(fileserverdir, tcpport, proxyhost, proxyudpport,
				fileserverminalive, shell);
	}
}
