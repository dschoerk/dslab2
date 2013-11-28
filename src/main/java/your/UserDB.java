package your;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.Scanner;

import model.UserInfo;
import util.Config;

public class UserDB {

	private HashMap<String, User> readUsers;

	private Config cfg;

	public UserDB() {
		cfg = new Config("user");
		readUsers = new HashMap<String, User>();

		Scanner s;
		try {
			s = new Scanner(new FileInputStream(new File("src/main/resources/user.properties")));

			while (s.hasNext()) {
				String line = s.nextLine();
				if(line.isEmpty())
					continue;
				String name = line.substring(0, line.indexOf('.'));
				String password = cfg.getString(name + ".password");
				int credits = cfg.getInt(name + ".credits");

				User info = new User(name, password, credits);
				readUsers.put(name, info);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
	public Iterator<User> iterator()
	{
		return readUsers.values().iterator();
	}

	public User getUser(String name) {
		User u = readUsers.get(name);
		return u;
	}
}
