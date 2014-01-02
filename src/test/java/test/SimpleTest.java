package test;

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import proxy.IProxyCli;
import server.IFileServerCli;
import util.ComponentFactory;
import util.Config;
import util.Util;
import cli.Shell;
import cli.TestInputStream;
import cli.TestOutputStream;
import client.IClientCli;

public class SimpleTest {
	static ComponentFactory componentFactory = new ComponentFactory();
	private IProxyCli proxy;
	private IFileServerCli server;
	private IClientCli client;

	@Before
	public void before() throws Exception {		
		
		proxy = componentFactory.startProxy(new Config("proxy"), new Shell("proxy", new TestOutputStream(System.out), new TestInputStream()));
		Thread.sleep(Util.WAIT_FOR_COMPONENT_STARTUP);

		server = componentFactory.startFileServer(new Config("fs1"), new Shell("fs1", new TestOutputStream(System.out), new TestInputStream()));
		Thread.sleep(Util.WAIT_FOR_COMPONENT_STARTUP);
		
		client = componentFactory.startClient(new Config("client"), new Shell("client", new TestOutputStream(System.out), new TestInputStream()));
		Thread.sleep(Util.WAIT_FOR_COMPONENT_STARTUP);
	}

	@Test
	public void test() throws Exception {

		System.out.println("start");
		String actual = client.login("alice", "12345").toString();
		String expected = "success";
		assertTrue(String.format("Response must contain '%s' but was '%s'", expected, actual), actual.contains(expected));
		
		System.out.println("start");
		actual = client.credits().toString();
		expected = "200";
		assertTrue(String.format("Response must contain '%s' but was '%s'", expected, actual), actual.contains(expected));

		System.out.println("start");
		actual = client.download("short.txt").toString();
		expected = "!data dslab13";
		assertTrue(String.format("Response must start with '%s' but was '%s'", expected, actual), actual.startsWith(expected));

		System.out.println("start");
		actual = client.credits().toString();
		expected = "192";
		assertTrue(String.format("Response must contain '%s' but was '%s'", expected, actual), actual.contains(expected));
		
		System.out.println("start");
		actual = client.upload("upload.txt").toString();
		expected = "File: upload.txt has been uploaded";
		assertTrue(String.format("Response must contain '%s' but was '%s'", expected, actual), actual.contains(expected));

		System.out.println("start");
		actual = client.credits().toString();
		expected = "292";
		assertTrue(String.format("Response must contain '%s' but was '%s'", expected, actual), actual.contains(expected));

		System.out.println("start");
		actual = client.logout().toString();
		expected = "Successfully logged out.";
		assertTrue(String.format("Response must contain '%s' but was '%s'", expected, actual), actual.contains(expected));
	}
	
	@After
	public void after() throws Exception {
		try {
			proxy.exit();
		} catch (Exception e) {
			// This should not happen. In case it does, output the stack trace for easier trouble shooting.
			e.printStackTrace();
		}
		try {
			server.exit();
		} catch (IOException e) {
			// This should not happen. In case it does, output the stack trace for easier trouble shooting.
			e.printStackTrace();
		}
		try {
			client.exit();
		} catch (IOException e) {
			// This should not happen. In case it does, output the stack trace for easier trouble shooting.
			e.printStackTrace();
		}
	}

}
