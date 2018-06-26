package meowcat.voxelsync;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class Main {

    static private final int PORT = 19950;
    static private final String voxeldir = "E:\\games\\[1.7.10][群峦传说][随便吃的饺砸服]\\.minecraft\\mods\\VoxelMods\\voxelMap\\cache\\dx.k.mcmiao.com~colon~11475\\DEFAULT (dimension 0)";
    static private final String voxelser = "E:\\Server\\voxel";

    static public void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        if (args[0].equals("-s")) {
            runAsServer();
            return;
        }
        printUsage();
    }

    static public void runAsServer() {
        File conff = new File(Constants.CONFIG_NAME);
        String confs = StreamUtil.readTextFile(conff);
        if (confs == null) {
            System.out.println("Failed loading config file. You need config.txt under this directory.");
            return;
        }
        int port;
        String basedir;
        try {
            JSONObject conf = new JSONObject(confs);
            port = conf.getInt(Constants.CONFIG_KEY_PORT);
            basedir = conf.getString(Constants.CONFIG_KEY_REPO);
        } catch (JSONException e) {
            System.out.println("Config file malformed.");
            return;
        }
        if (port <= 0 || port > 65535) {
            System.out.println("Port should be within (0,65535].");
            return;
        }
        Server ser = new Server(port);
        ser.setVoxelDir(basedir);
        System.out.printf("[Server] Running on port %d base dir %s.\n", port, basedir);
        ser.run();
    }

    static private void printUsage() {
        System.out.println("Usage: \n"
                + "\tAs server: run with parameter -s\n"
                + "\t\tYou would need a config file under same directory as this program.\n"
                + "\tAs client: compile with GUI enabled.\n");
    }

    static class ServerThread extends Thread {
        @Override
        public void run() {
            Server ser = new Server(PORT);
            ser.setVoxelDir(voxelser);
            ser.run();
        }
    }

    static class ClientThread extends Thread {
        @Override
        public void run() {
            Client client = new Client("localhost", PORT);
            client.setVoxelDir(voxeldir);
            client.setRemoteId(0);
            //client.compare();
            //client.upload("0,0.zip");
            //client.download("0,0.zip");
        }
    }

}
