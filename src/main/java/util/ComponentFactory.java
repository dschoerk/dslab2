package util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.security.Key;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.openssl.PEMReader;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.util.encoders.Hex;

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
		String keydir = config.getString("keys.dir");

		String proxyPublicKeyPath = config.getString("proxy.key");
		PEMReader in = new PEMReader(new FileReader(proxyPublicKeyPath));
		PublicKey pubk = (PublicKey) in.readObject();
		in.close();
		
		return new Client(new File(downloaddir), host, port, pubk, new File(keydir), shell);
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
		String keydir = config.getString("keys.dir");

		String pathtoprivkey = config.getString("key");
		System.out.println(pathtoprivkey);
		PEMReader in = new PEMReader(new FileReader(pathtoprivkey), new PasswordFinder() {

			@Override
			public char[] getPassword() {
				// return
				try {

					// char [] a = new BufferedReader(new
					// InputStreamReader(System.in)).readLine().toCharArray();
					char[] a = new char[] { '1', '2', '3', '4', '5' };

					return a;
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				return null;
			}
		});

		KeyPair keyPair = (KeyPair) in.readObject();
		PrivateKey privKey = keyPair.getPrivate();
		in.close();
		
		String hmacKey = config.getString("hmac.key");
		byte[] keyBytes = new byte[1024];
		FileInputStream fis = new FileInputStream(hmacKey);
		fis.read(keyBytes);
		fis.close();
		byte[] keyData = Hex.decode(keyBytes);
		Key shaKey = new SecretKeySpec(keyData, "HmacSHA256");

		return new Proxy(tcpPort, udpPort, timeout, checkPeriod, shaKey, new File(keydir), privKey, shell);
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
	public IFileServerCli startFileServer(Config config, Shell shell) throws Exception {
		// TODO: create a new file server instance (including a Shell) and start
		// it
		String fileserverdir = config.getString("fileserver.dir");
		int tcpport = config.getInt("tcp.port");
		String proxyhost = config.getString("proxy.host");
		int proxyudpport = config.getInt("proxy.udp.port");
		int fileserverminalive = config.getInt("fileserver.alive");
		
		String hmacKey = config.getString("hmac.key");
		byte[] keyBytes = new byte[1024];
		FileInputStream fis = new FileInputStream(hmacKey);
		fis.read(keyBytes);
		fis.close();
		byte[] keyData = Hex.decode(keyBytes);
		Key shaKey = new SecretKeySpec(keyData, "HmacSHA256");


		return new Fileserver(shaKey, fileserverdir, tcpport, proxyhost, proxyudpport, fileserverminalive, shell);
	}
}
