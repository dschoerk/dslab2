package your;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

import util.Config;

public class ProxyManagement extends UnicastRemoteObject implements MessageInterface {

    /**
     * 
     */
    private static final long serialVersionUID = -5522795323645760709L;
    Registry registry;
    
    public ProxyManagement() throws RemoteException
    {
        Config cfg = new Config("mc");
        
        try {
            registry = LocateRegistry.createRegistry(cfg.getInt("proxy.rmi.port"));
            registry.rebind("Proxy", this);

        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public int getReadQuorum() throws RemoteException {
        // TODO Auto-generated method stub
        return 1;
    }

    @Override
    public int getWriteQuorum() throws RemoteException {
        // TODO Auto-generated method stub
        return 2;
    }

    @Override
    public List<String> getTopThree() throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public char[] getProxyPublicKey() throws RemoteException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void subscribe(RMICallbackInterface client, String file, int trigger) {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            client.notifySubscriber(file, trigger);
        } catch (RemoteException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
    }

    @Override
    public void setUserPublicKey(String user, char[] key)
            throws RemoteException {
        // TODO Auto-generated method stub
        
    }
    
    public void close()
    {
        try {
            registry.unbind("Proxy");
            UnicastRemoteObject.unexportObject(this, true);
        } catch (AccessException e) {

        } catch (RemoteException e) {

        } catch (NotBoundException e) {

        }
    }


}
