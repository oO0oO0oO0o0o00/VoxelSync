package meowcat.voxelsync;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static meowcat.voxelsync.Constants.*;
import static meowcat.voxelsync.StreamUtil.verifyPrefix;

public class Server {

    private int port;
    private File voxelDir = null;
    private List<String> openedFiles;

    public Server(int port) {
        this.port = port;
        openedFiles = new LinkedList<>();
    }

    public void setVoxelDir(String dir) {
        voxelDir = new File(dir);
    }

    public boolean run() {
        ServerSocket sockin;
        try {
            sockin = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        while (true) {
            Socket sock;
            try {
                sock = sockin.accept();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            WorkThread th = new WorkThread(sock);
            th.start();
        }
    }

    @Nullable
    private JSONArray getMaps(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        JSONArray res = new JSONArray();
        for (File file : files) {
            try {
                acquireFile(file);
                ZipFile z = new ZipFile(file);
                ZipEntry e = z.getEntry(ZIP_ENTRY_VOXEL_NAME);
                JSONObject jso = new JSONObject();
                jso.put(JSON_KEY_COMPARE_REQUEST_DATA_NAME, file.getName());
                jso.put(JSON_KEY_COMPARE_REQUEST_DATA_CRC, e.getCrc());
                res.put(jso);
                z.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            releaseFile(file);
        }
        return res;
    }

    private synchronized void acquireFile(@NotNull File file) {
        String fn = file.getName();
        while (openedFiles.contains(fn)) {
            try {
                Thread.currentThread().sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        openedFiles.add(fn);
    }

    private synchronized void releaseFile(@NotNull File file) {
        openedFiles.remove(file.getName());
    }

    class WorkThread extends Thread {

        private Socket sock;

        public WorkThread(Socket sock) {
            this.sock = sock;
        }

        @Override
        public void run() {
            flow:
            try {
                InputStream is = sock.getInputStream();
                byte[] buf = new byte[16];
                StreamUtil.readBytes(is, buf, 0, 9);
                if (!verifyPrefix(buf)) break flow;
                OutputStream os = sock.getOutputStream();
                File dir = new File(voxelDir, Byte.toString(buf[8]));
                switch (buf[7]) {
                    case PACKET_COMPARE:
                        dir.mkdirs();
                        doCompare(is, os, dir);
                        break;
                    case PACKET_UPLOAD:
                        dir.mkdirs();
                        doUpload(is, os, dir);
                        break;
                    case PACKET_DOWNLOAD:
                        dir.mkdirs();
                        doDownload(is, os, dir);
                        break;
                    default:
                        doError(is, os);
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                sock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void doCompare(InputStream is, OutputStream os, File dir) {
            System.out.println("compare");
            try {
                int length = StreamUtil.readInt(is);
                byte[] arr = new byte[length];
                StreamUtil.readBytes(is, arr, 0, length);
                JSONObject jso = new JSONObject(new String(arr, EncodingUtil.getUtf8Charset()));
                JSONArray cli = jso.getJSONArray(JSON_KEY_COMPARE_REQUEST_DATA);
                jso = new JSONObject();
                JSONArray upl = new JSONArray();
                JSONArray dnl = new JSONArray();
                jso.put(JSON_KEY_COMPARE_RESPONSE_UPLOAD, upl);
                jso.put(JSON_KEY_COMPARE_RESPONSE_DOWNLOAD, dnl);
                JSONArray ser = getMaps(dir);
                for (int i = 0; i < ser.length(); i++) {
                    JSONObject es = ser.getJSONObject(i);
                    String name = es.getString(JSON_KEY_COMPARE_REQUEST_DATA_NAME);
                    boolean nfound = true;
                    for (int j = 0; j < cli.length(); j++) {
                        JSONObject ec = cli.getJSONObject(j);
                        if (name.equals(ec.getString(JSON_KEY_COMPARE_REQUEST_DATA_NAME))) {
                            nfound = false;
                            if (es.getLong(JSON_KEY_COMPARE_REQUEST_DATA_CRC) != ec.getLong(JSON_KEY_COMPARE_REQUEST_DATA_CRC)) {
                                upl.put(name);
                                dnl.put(name);
                            }
                            break;
                        }
                    }
                    if (nfound) dnl.put(name);
                }
                for (int i = 0; i < cli.length(); i++) {
                    JSONObject ec = cli.getJSONObject(i);
                    String name = ec.getString(JSON_KEY_COMPARE_REQUEST_DATA_NAME);
                    boolean nfound = true;
                    for (int j = 0; j < ser.length(); j++) {
                        JSONObject es = ser.getJSONObject(j);
                        if (name.equals(es.getString(JSON_KEY_COMPARE_REQUEST_DATA_NAME))) {
                            nfound = false;
                            break;
                        }
                    }
                    if (nfound) {
                        upl.put(name);
                    }
                }
                byte[] bytes = jso.toString().getBytes(EncodingUtil.getUtf8Charset());
                StreamUtil.writeBytes(os, HEADLINE.getBytes(EncodingUtil.getUtf8Charset()), 0, 7);
                os.write(PACKET_COMPARE);
                os.write(1);
                StreamUtil.writeInt(os, bytes.length);
                StreamUtil.writeBytes(os, bytes, 0, bytes.length);
                System.out.println("meeeowww");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void doUpload(InputStream is, OutputStream os, File dir) {
            System.out.println("upload");
            flow:
            try {
                //Get uploaded file name.
                int size = StreamUtil.readInt(is);
                byte[] buf = new byte[size];
                StreamUtil.readBytes(is, buf, 0, size);
                String name = new String(buf, EncodingUtil.getUtf8Charset());
                System.out.printf("[Server] Client uploading %s.\n", name);

                //Get uploaded file.
                size = StreamUtil.readInt(is);
                buf = new byte[size];
                StreamUtil.readBytes(is, buf, 0, size);

                //If server have this file.
                File fs = new File(dir, name);
                boolean needDownload = false;

                CombineResult res = combine(fs, buf, dir, name);
                if (res.failure != 0) break flow;
                if (res.needtransback) needDownload = true;
                StreamUtil.writeBytes(os, HEADLINE.getBytes(EncodingUtil.getUtf8Charset()), 0, 7);
                os.write(PACKET_UPLOAD);
                os.write(needDownload ? 1 : 0);
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void doDownload(InputStream is, OutputStream os, File dir) {
            System.out.println("download");
            try {
                int size = StreamUtil.readInt(is);
                byte[] buf = StreamUtil.readBytes(is, size);
                String name = new String(buf, EncodingUtil.getUtf8Charset());
                File fil = new File(dir, name);
                if (fil.isFile()) {
                    os.write(1);
                    buf = StreamUtil.readFile(fil);
                    StreamUtil.writeInt(os, buf.length);
                    StreamUtil.writeBytes(os, buf);
                } else {
                    os.write(0);
                }
                os.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        private void doError(InputStream is, OutputStream os) {
            System.out.println("error");
        }
    }

    static class CombineResult {
        public boolean needtransback = false;
        public int failure = 0;
    }

    static public CombineResult combine(File local, byte[] another, File baseDir, String name) throws IOException {
        CombineResult result = new CombineResult();
        flow:
        try {
            //Stream in the data.
            ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(another));
            ZipEntry zen = zis.getNextEntry();
            if (zen == null || !zen.getName().equals(ZIP_ENTRY_VOXEL_NAME)) {
                result.failure = -3;
                break flow;
            }
            if (local.exists()) {
                //As it exists, get its content.
                ZipFile zf = new ZipFile(local);
                ZipEntry zem = zf.getEntry(ZIP_ENTRY_VOXEL_NAME);
                if (zem == null) {
                    result.failure = -1;
                    break flow;
                }
                InputStream zfis = zf.getInputStream(zem);
                byte[] data = StreamUtil.readBytes(zfis, (int) zem.getSize());
                zf.close();

                //Then compare.
                boolean svrModified = false;
                for (int i = 0; i < 1114112; i += 17) {
                    byte[] line = StreamUtil.readBytes(zis, 17);
                    boolean ec = isEmptyLine(line, 0);
                    boolean es = isEmptyLine(data, i);
                    //If we have a line client doesn't have we would need to tell it a downloading is needed.
                    //Else if client have a line we don't have we update.
                    if (ec && !es) result.needtransback = true;
                    else if (es && !ec) {
                        svrModified = true;
                        for (int j = 0; j < 17; j++) {
                            data[i + j] = line[j];
                        }
                    }
                }
                zis.close();
                //If we've updated it have to be saved to zip file.
                if (svrModified) {
                    File fnew = new File(baseDir, name + ".new");
                    File fbak = new File(baseDir, name + ".bak");
                    FileOutputStream fos = new FileOutputStream(fnew);
                    ZipOutputStream zos = new ZipOutputStream(fos);
                    ZipEntry znew = new ZipEntry(ZIP_ENTRY_VOXEL_NAME);
                    zos.putNextEntry(znew);
                    zos.write(data);
                    zos.closeEntry();
                    zos.close();
                    local.renameTo(fbak);
                    fnew.renameTo(local);
                    fbak.delete();
                }
            } else {
                //If we don't have the whole file we just use client's.
                File fnew = new File(baseDir, name + ".new");
                FileOutputStream fos = new FileOutputStream(fnew);
                fos.write(another);
                fos.close();
                fnew.renameTo(local);
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.failure = -2;
        }
        return result;
    }

    @Contract(pure = true)
    static private boolean isEmptyLine(byte[] bytes, int offset) {
        for (int i = offset, lim = offset + 17; i < lim; i++) {
            if (bytes[i] != 0) return false;
        }
        return true;
    }

}
