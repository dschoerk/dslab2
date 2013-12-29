package management;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import util.Config;
import your.FileInfo;

public class ProxyManagement extends UnicastRemoteObject implements
        MessageInterface {

    /**
     * 
     */
    private static final long serialVersionUID = -5522795323645760709L;
    Registry registry;

    ArrayList<Subscription> subscribers;

    public ProxyManagement() throws RemoteException {

        Config cfg = new Config("mc");
        subscribers = new ArrayList<Subscription>();

        try {
            registry = LocateRegistry.createRegistry(cfg
                    .getInt("proxy.rmi.port"));
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
    public void subscribe(RMICallbackInterface client,String user, String file, int trigger) {

        subscribers.add(new Subscription(user, file, trigger, client));
        
    }

    @Override
    public void setUserPublicKey(String user, char[] key)
            throws RemoteException {
        // TODO Auto-generated method stub

    }

    public void updateSubscriptions(FileInfo file) {

        for (Subscription s : subscribers) {
            if (s.getFile().equals(file.getName()) && s.getTrigger() >= file.getDownloadCnt()) {
                try {
                    s.getCallback().notifySubscriber(s.getFile(),
                            s.getTrigger());
                    subscribers.remove(s);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void removeSubscriptions(String user) {

        for (Subscription s : subscribers) {
            if (s.getUser().equals(user)) {
                subscribers.remove(s);
            }
        }
    }

    public void close() {
        try {
            registry.unbind("Proxy");
            UnicastRemoteObject.unexportObject(this, true);
            UnicastRemoteObject.unexportObject(registry, true);
        } catch (AccessException e) {

        } catch (RemoteException e) {

        } catch (NotBoundException e) {

        } catch (Exception e) {

        }
    }

}
