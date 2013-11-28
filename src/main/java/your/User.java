package your;

import model.UserInfo;

public class User  {

	private String name = null;
	private String password = null;
	private long credits = 0;
	
	public User(String name, String password, long credits)
	{
		this.name = name;
		this.password = password;
		this.credits = credits;
	}
	
	public UserInfo createInfoObject(boolean online)
	{
		return new UserInfo(name, credits, online);
	}
	
	public boolean hasPassword(String pwd)
	{
		return pwd.equals(password);
	}
	
	public long getCredits()
	{
		return credits;
	}
	
	public void addCredits(long l)
	{
		credits += l;
	}
	
	@Override
	public boolean equals(Object b)
	{
		if(!(b instanceof User))
			return false;
		
		return name.equals(((User)b).name);
	}

	public String getName() {
		return name;
	}
	
	public boolean hasName(String s)
	{
		return getName().equals(s);
	}
}
