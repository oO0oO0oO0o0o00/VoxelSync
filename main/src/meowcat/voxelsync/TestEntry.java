package meowcat.voxelsync;

public class TestEntry {

    static public void main(String[] args) {
        Thread ts = new Main.ServerThread();
        Thread tc = new Main.ClientThread();
        ts.start();
        tc.start();
        try {
            tc.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        try {
            ts.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
