package management;

public class Subscription {
    
    private String user;
    private RMICallbackInterface callback;
    private String file;
    private int trigger;
    
    public Subscription(String user, String file, int trigger, RMICallbackInterface callack)
    {
        this.user = user;
        this.file=file;
        this.trigger=trigger;
        this.callback=callack;
    }

    public String getUser() {
        return user;
    }

    public RMICallbackInterface getCallback() {
        return callback;
    }

    public String getFile() {
        return file;
    }

    public int getTrigger() {
        return trigger;
    }

}
