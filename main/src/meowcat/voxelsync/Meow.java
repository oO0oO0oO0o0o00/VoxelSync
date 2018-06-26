package meowcat.voxelsync;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.LinkedList;
import java.util.List;

import static meowcat.voxelsync.Constants.*;

public class Meow {

    static private Meow thiz = null;

    private JPanel panel1;
    private CheckBoxList list;
    private JButton buttonSync;
    private JLabel labelInfo;
    private JTextPane textPane;

    private List<Map> maps = null;
    private String server;
    private int port;

    public Meow() {
        buttonSync.addActionListener(actionEvent -> {
            ListModel<JCheckBox> model = list.getModel();
            SyncThread th = new SyncThread();
            th.start();
        });
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Meooow~");
        if (null == thiz) thiz = new Meow();
        thiz.loadCfg();
        MessageConsole console = new MessageConsole(thiz.textPane);
        console.redirectOut();
        console.redirectErr(Color.RED, null);
        console.setMessageLines(100);
        frame.setContentPane(thiz.panel1);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        System.out.println("Meow~");
    }

    private void loadCfg() {
        File conff = new File("config.txt");
        if (!conff.exists()) {
            //System.out.println(System.getProperty("user.dir"));
            return;
        }
        String confs = StreamUtil.readTextFile(conff);
        if (confs == null) {
            //System.out.println("23");
            return;
        }
        try {
            JSONObject conf = new JSONObject(confs);
            JSONArray maps = conf.getJSONArray(CONFIG_KEY_MAPS);
            this.maps = new LinkedList<>();
            for (int i = 0, lim = maps.length(); i < lim; i++) {
                JSONObject mcon = maps.getJSONObject(i);
                Map map = new Map(
                        mcon.getString(CONFIG_KEY_MAPS_NAME),
                        mcon.getString(CONFIG_KEY_MAPS_FOLDER),
                        mcon.getString(CONFIG_KEY_MAPS_SUBDIR),
                        mcon.getInt(CONFIG_KEY_MAPS_REMOTEID),
                        mcon.has(CONFIG_KEY_MAPS_CHECKED) ? mcon.getBoolean(CONFIG_KEY_MAPS_CHECKED) : false
                );
                this.maps.add(map);
            }
            this.server = conf.getString(CONFIG_KEY_SERVER);
            this.port = conf.getInt(CONFIG_KEY_PORT);
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        updateUi();
    }

    private void updateUi() {
        DefaultListModel<JCheckBox> listModel = new DefaultListModel<>();
        for (Map map : maps) {
            JCheckBox box = new JCheckBox(map.name);
            if (map.checked) box.doClick();
            box.addItemListener(itemEvent -> {
                boolean checked = ((JCheckBox) itemEvent.getItem()).isSelected();
                map.checked = checked;
            });
            listModel.addElement(box);
        }
        thiz.list.setModel(listModel);
        labelInfo.setText(String.format("同步主机\t%s:%d", server, port));
    }

    static private class Map {
        public String name;
        public String folder;
        public String subdir;
        public int remoteid;
        public boolean checked;
        public JCheckBox ref;

        public Map(String name, String folder, String subdir, int remoteid, boolean checked) {
            this.name = name;
            this.folder = folder;
            this.subdir = subdir;
            this.remoteid = remoteid;
            this.checked = checked;
        }
    }

    private class SyncThread extends Thread {

        @Override
        public void run() {
            Client client = new Client(server, port);
            for (Map map : maps) {
                if (map.checked) {
                    System.out.printf("开始同步 %s 。\n", map.name);
                    File dir = new File(".minecraft\\mods\\VoxelMods\\voxelMap\\cache");
                    if (!dir.exists()) {
                        dir = new File("E:\\games\\[1.7.10][群峦传说][随便吃的饺砸服]", dir.getPath());
                    }
                    dir = new File(dir, map.folder);
                    dir = new File(dir, map.subdir);
                    client.setVoxelDir(dir);
                    client.setRemoteId(map.remoteid);
                    client.sync();
                }
            }
        }
    }

}
