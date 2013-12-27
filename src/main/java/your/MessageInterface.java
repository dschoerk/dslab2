package your;

import java.rmi.*;
import java.util.List;

public interface MessageInterface extends Remote {

    int getReadQuorum() throws RemoteException;
    int getWriteQuorum() throws RemoteException;
    
    List<String> getTopThree() throws RemoteException;
    
    void subscribe(RMICallbackInterface client, String file, int trigger) throws RemoteException;
    
    char[] getProxyPublicKey() throws RemoteException;
    void setUserPublicKey (String user, char[] key) throws RemoteException;
    
}
