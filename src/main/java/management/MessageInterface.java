package management;

import java.rmi.*;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

public interface MessageInterface extends Remote {

    int getReadQuorum() throws RemoteException;
    int getWriteQuorum() throws RemoteException;
    
    List<String> getTopThree() throws RemoteException;
    
    void subscribe(RMICallbackInterface client,String user, String file, int trigger) throws RemoteException;
    
    PublicKey getProxyPublicKey() throws RemoteException;
    void setUserPublicKey (String user, PublicKey o) throws RemoteException;
    
}
