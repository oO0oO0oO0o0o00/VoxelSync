package meowcat.voxelsync;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;
import org.omg.IOP.ENCODING_CDR_ENCAPS;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static meowcat.voxelsync.Constants.*;
import static meowcat.voxelsync.StreamUtil.verifyPrefix;

public class Client {

    private String host;
    private int port;
    private int remoteId = -1;
    private File voxelDir = null;

    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void setVoxelDir(String voxelDir) {
        this.voxelDir = new File(voxelDir);
    }

    public void setVoxelDir(File voxelDir) {
        this.voxelDir = voxelDir;
    }

    public void setRemoteId(int remoteId) {
        this.remoteId = remoteId;
    }

    @Nullable
    private JSONObject prepareCompare() {
        if (!voxelDir.isDirectory()) return null;
        File[] list = voxelDir.listFiles(file -> {
            if (!file.isFile()) return false;
            return file.getName().matches("^-?[0-9]+,-?[0-9]+\\.zip$");
        });
        if (list == null) return null;
        JSONObject jso = new JSONObject();
        JSONArray jarr = new JSONArray();
        jso.put(JSON_KEY_COMPARE_REQUEST_DATA, jarr);
        for (File file : list) {
            try {
                ZipFile zip = new ZipFile(file);
                JSONObject jen = new JSONObject();
                jen.put(JSON_KEY_COMPARE_REQUEST_DATA_NAME, file.getName());
                jen.put(JSON_KEY_COMPARE_REQUEST_DATA_CRC, zip.getEntry(ZIP_ENTRY_VOXEL_NAME).getCrc());
                jarr.put(jen);
                zip.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return jso;
    }

    @Nullable
    private Socket newConnection() {
        Socket sock = null;
        try {
            sock = new Socket(host, port);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        try {
            sock.getOutputStream().write(HEADLINE.getBytes(EncodingUtil.getUtf8Charset()));
        } catch (IOException e) {
            e.printStackTrace();
            try {
                sock.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return null;
        }
        return sock;
    }

    @Nullable
    private JSONObject compare() {

        JSONObject ret = null;

        //Settings should be correct.
        if (remoteId < 0 || remoteId > 255) return null;
        if (voxelDir == null) return null;

        //Generate the data to be sent.
        JSONObject jso = prepareCompare();
        if (jso == null) return null;

        //Start the connection.
        Socket sock = newConnection();
        if (sock == null) return null;
        flow:
        try {
            //Write method id and remote sub dir id.
            OutputStream os = sock.getOutputStream();
            os.write(PACKET_COMPARE);
            os.write(remoteId);

            //Write size and content of data.
            byte[] dat = jso.toString().getBytes(EncodingUtil.getUtf8Charset());
            StreamUtil.writeInt(os, dat.length);
            os.write(dat);
            os.flush();

            //Read response.
            byte[] buf = new byte[16];
            InputStream is = sock.getInputStream();

            //Verify response.
            StreamUtil.readBytes(is, buf, 0, 9);
            System.out.println(new String(buf, 0, 7));
            System.out.println(buf[0]);
            if (!verifyPrefix(buf)) break flow;
            System.out.printf("%d", buf[7]);
            if (buf[7] != PACKET_COMPARE) break flow;

            //Read response json.
            int len = StreamUtil.readInt(is);
            buf = new byte[len];
            StreamUtil.readBytes(is, buf, 0, len);
            ret = new JSONObject(new String(buf, EncodingUtil.getUtf8Charset()));
            System.out.println(ret.toString(4));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            System.out.println("[Client] Closing connection.");
            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private int upload(String name) {
        if (remoteId < 0 || remoteId > 255) return -1;
        if (voxelDir == null) return -1;
        int ret = -2;
        Socket sock = newConnection();
        flow:
        try {
            OutputStream os = sock.getOutputStream();
            os.write(PACKET_UPLOAD);
            os.write(remoteId);
            byte[] buf = name.getBytes(EncodingUtil.getUtf8Charset());
            StreamUtil.writeInt(os, buf.length);
            StreamUtil.writeBytes(os, buf, 0, buf.length);
            buf = StreamUtil.readFile(new File(voxelDir, name));
            StreamUtil.writeInt(os, buf.length);
            StreamUtil.writeBytes(os, buf, 0, buf.length);
            InputStream is = sock.getInputStream();
            buf = StreamUtil.readBytes(is, 9);
            if (!verifyPrefix(buf)) break flow;
            if (buf[7] != PACKET_UPLOAD) break flow;
            ret = buf[8];
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private int download(String name) {
        Socket sock = newConnection();
        int ret = -1;
        flow:
        try {
            OutputStream os = sock.getOutputStream();
            os.write(PACKET_DOWNLOAD);
            os.write(remoteId);
            byte[] buf = name.getBytes(EncodingUtil.getUtf8Charset());
            StreamUtil.writeInt(os, buf.length);
            StreamUtil.writeBytes(os, buf);
            InputStream is = sock.getInputStream();
            int status = is.read();
            if (status != 1) break flow;
            int size = StreamUtil.readInt(is);
            buf = StreamUtil.readBytes(is, size);
            File fc = new File(voxelDir, name);
            Server.CombineResult res = Server.combine(fc, buf, voxelDir, name);
            //System.out.println(res.failure);
            ret = 0;
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            sock.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ret;
    }

    public void sync() {
        JSONObject jso = compare();
        if (jso == null) {
            System.err.println("文件对比失败。结束同步。");
            return;
        }
        JSONArray upl = jso.getJSONArray(JSON_KEY_COMPARE_RESPONSE_UPLOAD);
        JSONArray dnl = jso.getJSONArray(JSON_KEY_COMPARE_RESPONSE_DOWNLOAD);
        List<String> noupl = new ArrayList<>(16);
        System.out.println("开始上传文件。");
        for (int i = 0; i < upl.length(); i++) {
            String name = upl.getString(i);
            System.out.printf("正在上传文件 %s 。\n", name);
            //File fil = new File(voxelDir, name);
            int ret = upload(name);
            if (ret < 0) {
                System.err.printf("文件上传失败：%d 。\n", ret);
                continue;
            }
            System.out.println("完成。");
            if (ret == 0) {
                noupl.add(name);
                System.out.println("服务端报告该文件不需要下载。");
            }
        }
        System.out.println("完成上传，开始下载合并。");
        for (int i = 0; i < dnl.length(); i++) {
            String name = dnl.getString(i);
            if (noupl.contains(name)) continue;
            int ret = download(name);
            if (ret < 0) {
                System.err.printf("文件下载合并失败：%d 。\n", ret);
                continue;
            }
            System.out.println("完成。");
        }
        System.out.println("同步完成。");
    }

}
