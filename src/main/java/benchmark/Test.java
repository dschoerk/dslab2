package benchmark;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.*;

import util.ComponentFactory;
import util.Config;
import util.Util;
import your.FileInfo;
import your.Fileserver;
import your.Proxy;
import cli.Command;
import cli.Shell;

public class Test {
    
    public static final int fileNumber = 5;
    public static final int fileServerNumber = 4;
    
    private ExecutorService threadpool;
    
    private Fileserver[] fileserver;
    Proxy proxy;
    Shell shell;

    /**
     * @param args
     */
    public static void main(String[] args) {
        new Test();

    }
    
    public Test(){
        
        shell = new Shell("Testbench", System.out, System.in);

        shell.register(this);
        Thread shellThread = new Thread(shell);
        shellThread.start();

        
        Config cfg = new Config("loadtest");
        int clients = cfg.getInt("loadtest.clients");
        int filesize = cfg.getInt("loadtest.fileSizeKB");
        int downRate = cfg.getInt("loadtest.downloadsPerMin");
        int upRate = cfg.getInt("loadtest.uploadsPerMin");
        double overwrite = Double.parseDouble(cfg.getString("loadtest.overwriteRatio"));
        
        threadpool = Executors.newCachedThreadPool();
        
        ComponentFactory factory = new ComponentFactory();
        
        try {
            
            fileserver = new Fileserver[fileServerNumber];
            for(int i=0; i<fileServerNumber;i++){
                fileserver[i]=(Fileserver) factory.startFileServer(new Config("fs"+(i+1)), new Shell("FS"+(i+1), System.out, System.in));
                shell.writeLine("fs"+i+" started");
            }
            
            proxy = (Proxy) factory.startProxy(new Config("proxy"), new Shell("Proxy", System.out, System.in));
            Thread.sleep(Util.WAIT_FOR_COMPONENT_STARTUP);
            shell.writeLine("proxy started");
            
            
            //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            //create tmp files 
            byte[]randData= new byte[filesize*1024];
            new Random().nextBytes(randData);

            File f=null;
            for(int i=0; i<fileNumber; i++){
                FileOutputStream fos=null;
                try {
                    for(int j=0; j<fileServerNumber;j++){
                        String dir = fileserver[j].getDownloadDirectory().getPath();
                        f = new File(dir  , i+".txt");
                        f.deleteOnExit();
                        fos = new FileOutputStream(f);
                        fos.write(randData);
                        fos.close();
                    }
                    
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }finally{
                    try {
                        if(fos!=null)fos.close();
                    } catch (IOException e) {
                        
                    }
                }
            }
          //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        for(int i=0; i<clients; i++){
            
            threadpool.execute(new TestSession(i,downRate,upRate,overwrite,filesize));
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            shell.writeLine("startet "+clients+"clients");
            shell.writeLine("with "+downRate+" downloads/min "+upRate+" uploads/min");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        
    }
    
    @Command
    public void exit(){
        
        
        try {
            shell.writeLine("terminate clients...");
            threadpool.shutdown();
            threadpool.awaitTermination(2, TimeUnit.SECONDS);
            threadpool.shutdownNow();
            
            for(int i=0; i<fileServerNumber;i++){
                System.out.println(fileserver[i].exit());
            }
            proxy.exit();
            System.in.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.flush();
    }

}
