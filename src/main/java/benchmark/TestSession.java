package benchmark;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

import util.ComponentFactory;
import util.Config;
import your.Client;
import your.MyFileServerInfo;
import cli.Shell;

public class TestSession implements Runnable {

    Client client;
    Shell shell;

    int number;
    Random rand;

    int filesize;
    int downRate;
    int upRate;
    double overwrite;
    Config cfg;

    LinkedBlockingQueue<String> commandqueue;

    public TestSession(int nr, int downRate, int upRate, double rate, int fs) {
        this.number = nr;
        this.downRate = downRate;
        this.upRate = upRate;
        this.overwrite = rate;
        this.filesize = fs;

        commandqueue = new LinkedBlockingQueue<String>();

        shell = new Shell("Client", System.out, System.in);
        cfg = new Config("client");

        rand = new Random(nr);
        // rand.setSeed(System.nanoTime());

    }

    private void login() {

    }

    @Override
    public void run() {
        Timer fileserverOnlineTimer;
        try {
            ComponentFactory factory = new ComponentFactory();
            client = (Client) factory.startClient(cfg, shell);

            try {
                client.login("alice", "12345");
                client.buy(999999999999999999L);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            TimerTask upTask = new TimerTask() {
                public void run() {
                    try {
                        String name;
                        byte[] randData = new byte[1024 * filesize];
                        // if (rand.nextDouble() < overwrite)// overwrite
                        // {
                        name = "" + rand.nextInt(Test.fileNumber) + ".txt";
                        /*
                         * } else { // upload new file name = number + "_" + System.nanoTime() + ".txt";// unique name }
                         */
                        rand.nextBytes(randData);

                        FileOutputStream fos = null;
                        try {
                            File f = new File(cfg.getString("download.dir"),
                                    name);
                            f.deleteOnExit();
                            if (!f.exists()) {
                                fos = new FileOutputStream(f);
                                fos.write(randData);
                                fos.close();
                            }
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                if (fos != null)
                                    fos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        commandqueue.put("!upload " + name);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            };
            
            TimerTask downTask = new TimerTask() {
                public void run() {
                    try {
                        int filename = rand.nextInt(Test.fileNumber);
                        commandqueue.put("!download " + filename + ".txt");
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            };

            fileserverOnlineTimer = new Timer();

            fileserverOnlineTimer.schedule(upTask, rand.nextInt(60000  / upRate), 60000  / upRate); 
            fileserverOnlineTimer.schedule(downTask, rand.nextInt(60000  / downRate), 60000 / downRate);

            Object result;
            try {
                while (true) {

                    String command = commandqueue.take();
                    result = shell.invoke(command);
                    if (command.startsWith("!upload")) {
                        // shell.writeLine(String.valueOf(result));
                    }
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (Throwable e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                try {
                    fileserverOnlineTimer.cancel();
                    client.exit();
                    System.out.println("client " + number + " closed");
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
