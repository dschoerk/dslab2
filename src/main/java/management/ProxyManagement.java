package management;

import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.Map.Entry;

import util.Config;
import your.FileInfo;
import your.Proxy;

public class ProxyManagement extends UnicastRemoteObject implements
        MessageInterface {

    /**
     * 
     */
    private static final long serialVersionUID = -5522795323645760709L;
    Registry registry;

    ArrayList<Subscription> subscribers;
    Proxy parent;
    
    public ProxyManagement(Proxy parent) throws RemoteException {

        this.parent=parent;
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
        
        Map<String,FileInfo> files = sortByValues(parent.getFiles());
        files.putAll(parent.getFiles());
        ArrayList<String> ret = new ArrayList<String>(3);
        int i=0;
        for(Entry<String,FileInfo> e : files.entrySet())
        {
            FileInfo fi = e.getValue();
            ret.add(i,(i+1)+". "+fi.getName()+"\t\t"+fi.getDownloadCnt()+"\n");
            i++;
            if(i==3)return ret;
        }
        return ret;
    }
    
    public static <K extends Comparable,V extends Comparable> Map<K,V> sortByValues(Map<K,V> map){
        List<Map.Entry<K,V>> entries = new LinkedList<Map.Entry<K,V>>(map.entrySet());
      
        Collections.sort(entries, new Comparator<Map.Entry<K,V>>() {

            @Override
            public int compare(Entry<K, V> o1, Entry<K, V> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });
      
        //LinkedHashMap will keep the keys in the order they are inserted
        //which is currently sorted on natural ordering
        Map<K,V> sortedMap = new LinkedHashMap<K,V>();
      
        for(Map.Entry<K,V> entry: entries){
            sortedMap.put(entry.getKey(), entry.getValue());
        }
      
        return sortedMap;
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
