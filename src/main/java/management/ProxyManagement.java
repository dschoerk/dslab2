package management;

import java.io.FileWriter;
import java.rmi.*;
import java.rmi.registry.*;
import java.rmi.server.UnicastRemoteObject;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.*;
import java.util.Map.Entry;

import org.bouncycastle.openssl.PEMWriter;

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
    Map<String, FileInfo> files;
    Proxy parent;

    public ProxyManagement(Proxy parent) throws RemoteException {
        
        this.parent = parent;
        
        files = new HashMap<String,FileInfo>();
        
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
        
        return (int) Math.ceil(parent.getOnlineServer().size()/2.0);
    }

    @Override
    public int getWriteQuorum() throws RemoteException {

        return (int) (Math.ceil(parent.getOnlineServer().size()/2.0)+1);
    }

    @Override
    public List<String> getTopThree() throws RemoteException {

        Map<String, FileInfo> f = sortByValues(files);
        f.putAll(parent.getFiles());
        ArrayList<String> ret = new ArrayList<String>(3);
        int i = 0;
        for (Entry<String, FileInfo> e : f.entrySet()) {
            FileInfo fi = e.getValue();
            ret.add(i,
                    (i + 1) + ". " + fi.getName() + "\t\t"
                            + fi.getDownloadCnt() + "\n");
            i++;
            if (i == 3)
                return ret;
        }
        return ret;
    }

    public static <K extends Comparable, V extends Comparable> Map<K, V> sortByValues(
            Map<K, V> map) {
        List<Map.Entry<K, V>> entries = new LinkedList<Map.Entry<K, V>>(
                map.entrySet());

        Collections.sort(entries, new Comparator<Map.Entry<K, V>>() {

            @Override
            public int compare(Entry<K, V> o1, Entry<K, V> o2) {
                return o1.getValue().compareTo(o2.getValue());
            }
        });

        // LinkedHashMap will keep the keys in the order they are inserted
        // which is currently sorted on natural ordering
        Map<K, V> sortedMap = new LinkedHashMap<K, V>();

        for (Map.Entry<K, V> entry : entries) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }

        return sortedMap;
    }

    @Override
    public PublicKey getProxyPublicKey() throws RemoteException {
        
        return parent.getPubKey();
    }

    @Override
    public void subscribe(RMICallbackInterface client, String user,
            String file, int trigger) {
        synchronized (subscribers) {
            subscribers.add(new Subscription(user, file, trigger, client));
        }
    }

    @Override
    public void setUserPublicKey(String user, PublicKey key)
            throws RemoteException {
        parent.setUserKey(user, key);

    }
    
    public void incDownloads(String filename){
        
        FileInfo f = files.get(filename);
        
        if(f==null){
            f=new FileInfo(filename,0);
            f.incDownloadCnt();
            files.put(filename, f);
        }else{
            f.incDownloadCnt();
        }
        updateSubscriptions(filename);
    }

    public void updateSubscriptions(String filename) {
        ArrayList<Subscription> toRemove = new ArrayList<Subscription>();
        synchronized (subscribers) {
            for (Subscription s : subscribers) {
                FileInfo f = files.get(filename);
                if(f==null)continue;
                if (s.getFile().equals(filename) && s.getTrigger() <= f.getDownloadCnt()) {
                    try {
                        s.getCallback().notifySubscriber(s.getFile(), s.getTrigger());
                        toRemove.add(s);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        subscribers.removeAll(toRemove);
    }

    public void removeSubscriptions(String user) {
        ArrayList<Subscription> toRemove = new ArrayList<Subscription>();
        synchronized(subscribers){
            for (Subscription s : subscribers) {
                if (s.getUser().equals(user)) {
                    toRemove.add(s);
                }
            }
            subscribers.removeAll(toRemove);
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
