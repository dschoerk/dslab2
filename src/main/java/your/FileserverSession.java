package your;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.Mac;

import message.Response;
import message.request.DownloadFileRequest;
import message.request.InfoRequest;
import message.request.ListRequest;
import message.request.UploadRequest;
import message.request.VersionRequest;
import message.response.DownloadFileResponse;
import message.response.MessageIntegrityErrorResponse;
import message.response.InfoResponse;
import message.response.ListResponse;
import message.response.MessageResponse;
import message.response.VersionResponse;
import model.DownloadTicket;
import networkio.Channel;
import networkio.HMACWrapped;
import networkio.TCPChannel;
import server.IFileServer;
import util.ChecksumUtils;

public class FileserverSession implements IFileServer, Runnable {

	private Socket socket;
	private Fileserver parent;

	private Mac hmac;
	private Channel channel;
	private HashMap<String, Integer> version= new HashMap<String, Integer>();
	private static Map<Class<?>, Method> commandMap = new HashMap<Class<?>, Method>();
	private static Set<Class<?>> hasArgument = new HashSet<Class<?>>();

	public FileserverSession(Socket socket, Fileserver parent) {

		this.socket = socket;
		this.parent = parent;
		version=parent.getVersionMap();
		try {
			hmac = Mac.getInstance("HmacSHA256");
			hmac.init(parent.getHMACKey());
		} catch (InvalidKeyException e1) {
			// does not happen
			e1.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// does not happen
			e.printStackTrace();
		}

		try {
			channel = new TCPChannel(socket);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void run() {

		try {
			Object o = channel.read();
			Object response = null;
			if (hasArgument.contains(o.getClass())) {
				response = commandMap.get(o.getClass()).invoke(this, o);
			} else {
				response = commandMap.get(o.getClass()).invoke(this);
			}
			channel.write(response);

		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("lost connection");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Response list() throws IOException {

		Set<String> files = new HashSet<String>();
		for (String name : parent.getDownloadDirectory().list()) {
			files.add(name);
		}
		if (files.size() == 0)
			return new MessageResponse("No Files");

		return new ListResponse(files);
	}

	@Override
	public Response download(DownloadFileRequest request) throws IOException {

		DownloadTicket ticket = request.getTicket();

		File f = parent.getFile(ticket.getFilename());
		if (f == null)
			return new MessageResponse("File \"" + request.getTicket().getFilename() + "\" does not exist");

		if (!ChecksumUtils.verifyChecksum(request.getTicket().getUsername(), f, version.get(request.getTicket().getFilename()), request.getTicket().getChecksum())) {
			return new MessageResponse("Download Ticket failed Checksum Test");
		}

		byte[] data = parent.getFileContent(f);
		return new DownloadFileResponse(ticket, data);
	}

	@Override
	public Response info(InfoRequest request) throws IOException {
		String filename = request.getFilename();

		InfoResponse response = new InfoResponse(filename, -1); // file does not
																// exist
		File f = parent.getFile(filename);
		if (f != null)
			response = new InfoResponse(filename, f.length());

		return response;
	}

	@Override
	public Response version(VersionRequest request) throws IOException {
		if(!(version.containsKey(request.getFilename())))
			return new VersionResponse(request.getFilename(), -1);
		return new VersionResponse(request.getFilename(), version.get(request.getFilename()));
	}

	@Override
	public MessageResponse upload(UploadRequest request) throws IOException {

		try {
			FileOutputStream fos = new FileOutputStream(parent.getDownloadDirectory().getAbsolutePath() + "/"
					+ request.getFilename());
			fos.write(request.getContent());
			fos.close();
			version.put(request.getFilename(),request.getVersion());
			return new MessageResponse("File \"" + request.getFilename() + "\" has been uploaded");
		} catch (IOException e) {
			return new MessageResponse("Error while uploading\"" + request.getFilename() + "\" !");
		}

	}

	public Response hmacwrapped(HMACWrapped obj) {
		System.out.println("received hmac wrapped");
		System.out.println("checksum correct: " + obj.isChecksumCorrect(hmac));

		if (!obj.isChecksumCorrect(hmac))
			return new MessageIntegrityErrorResponse();

		try {
			Object o = obj.getObject();
			Object response = null;
			if (hasArgument.contains(o.getClass())) {
				response = commandMap.get(o.getClass()).invoke(this, o);
			} else {
				response = commandMap.get(o.getClass()).invoke(this);
			}

			return (Response) response;

		} catch (IOException e) {
			// TODO Auto-generated catch block
			System.err.println("lost connection");
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	static {
		try {
			commandMap.put(ListRequest.class, FileserverSession.class.getMethod("list"));

			commandMap.put(DownloadFileRequest.class,
					FileserverSession.class.getMethod("download", DownloadFileRequest.class));
			hasArgument.add(DownloadFileRequest.class);

			commandMap.put(InfoRequest.class, FileserverSession.class.getMethod("info", InfoRequest.class));
			hasArgument.add(InfoRequest.class);

			commandMap.put(VersionRequest.class, FileserverSession.class.getMethod("version", VersionRequest.class));
			hasArgument.add(VersionRequest.class);

			commandMap.put(UploadRequest.class, FileserverSession.class.getMethod("upload", UploadRequest.class));
			hasArgument.add(UploadRequest.class);

			commandMap.put(ListRequest.class, FileserverSession.class.getMethod("list"));

			commandMap.put(HMACWrapped.class, FileserverSession.class.getMethod("hmacwrapped", HMACWrapped.class));
			hasArgument.add(HMACWrapped.class);

		} catch (NoSuchMethodException e) {
			// does not happen
			e.printStackTrace();
		} catch (SecurityException e) {
			// does not happen
			e.printStackTrace();
		}
	}
}
