package meowcat.voxelsync;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Random;

import static meowcat.voxelsync.Constants.HEADLINE;

public class StreamUtil {

    static final private Random rand = new Random(System.currentTimeMillis());

    static final public void writeInt(@NotNull OutputStream stream, int number) throws IOException {
        stream.write(number >>> 24);
        stream.write(number >>> 16);
        stream.write(number >>> 8);
        stream.write(number);
    }

    static final public int readInt(@NotNull InputStream stream) throws IOException {
        int number = stream.read() << 24;
        number |= stream.read() << 16;
        number |= stream.read() << 8;
        number |= stream.read();
        return number;
    }

    static final public boolean verifyPrefix(byte[] arr) {
        for (int i = 0; i < HEADLINE.length(); i++) {
            if (arr[i] != HEADLINE.charAt(i)) return false;
        }
        return true;
    }

    static final public int readBytes(InputStream is, byte[] buf, int offset, int length) throws IOException {
        for (int i = offset, j = 0, lim = offset + length; i < lim; i++, j++) {
            int b = is.read();
            if (b == -1) return j;
            buf[i] = (byte) b;
        }
        return length;
    }

    static final public byte[] readBytes(InputStream is, int length) throws IOException {
        byte[] bytes = new byte[length];
        readBytes(is, bytes, 0, length);
        return bytes;
    }

    static final public void writeBytes(OutputStream os, byte[] buf, int offset, int length) throws IOException {
        for (int i = offset, lim = offset + length; i < lim; i++) {
//            try {
//                Thread.sleep(rand.nextInt(10));
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
            os.write(buf[i]);
        }
    }

    static final public void writeBytes(OutputStream os, byte[] buf) throws IOException {
        writeBytes(os, buf, 0, buf.length);
    }

    @Nullable
    static final public byte[] readFile(File file) {
        FileInputStream fis;
        int size = (int) file.length();
        byte[] res = new byte[size];
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        try {
            for (int i = 0; i < size; i++) {
                int b = fis.read();
                if (b < 0) {
                    res = null;
                    break;
                }
                res[i] = (byte) b;
            }
        } catch (IOException e) {
            e.printStackTrace();
            res = null;
        }
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
        //Files.readAllBytes(file.toPath());
    }

    @Nullable
    static final public String readTextFile(File file) {
        InputStreamReader br = null;
        StringBuilder sb = new StringBuilder();
        try {
            FileInputStream fis = new FileInputStream(file);
            br = new InputStreamReader(fis, EncodingUtil.getUtf8Charset());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
        try {
            int ch;
            while ((ch = br.read()) != -1) {
                sb.append((char) ch);
            }
        } catch (IOException e) {
            e.printStackTrace();
            br = null;
        }
        try {
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (br == null) return null;
        return sb.toString();
    }
}
