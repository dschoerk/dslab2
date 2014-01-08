package util;

import java.io.IOException;
import java.io.InputStream;

public class NullInputStream extends InputStream {

    @Override
    public int read() throws IOException {
        try {
            this.wait();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return 0;
    }

}
