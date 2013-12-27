package your;

import java.rmi.*;

public interface RMICallbackInterface extends Remote{
    
    void notifySubscriber(String file, int trigger) throws RemoteException;
    
}
