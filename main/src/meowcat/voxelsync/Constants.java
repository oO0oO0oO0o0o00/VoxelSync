package meowcat.voxelsync;

public class Constants {

    static final public String HEADLINE = "meowcat";
    static final public byte PACKET_COMPARE = 0b0101111;
    static final public byte PACKET_UPLOAD = 0b1110100;
    static final public byte PACKET_DOWNLOAD = 0b1010011;
    static final public String JSON_KEY_COMPARE_REQUEST_DATA = "data";
    static final public String JSON_KEY_COMPARE_REQUEST_DATA_NAME = "name";
    static final public String JSON_KEY_COMPARE_REQUEST_DATA_CRC = "crc";
    static final public String JSON_KEY_COMPARE_RESPONSE_UPLOAD = "upload";
    static final public String JSON_KEY_COMPARE_RESPONSE_DOWNLOAD = "download";
    static final public String ZIP_ENTRY_VOXEL_NAME = "data";
    static final public String CONFIG_NAME = "config.txt";
    static final public String CONFIG_KEY_PORT = "port";
    static final public String CONFIG_KEY_REPO = "repo";
    static final public String CONFIG_KEY_MAPS = "maps";
    static final public String CONFIG_KEY_SERVER = "server";
    static final public String CONFIG_KEY_MAPS_NAME = "name";
    static final public String CONFIG_KEY_MAPS_FOLDER = "folder";
    static final public String CONFIG_KEY_MAPS_SUBDIR = "subdir";
    static final public String CONFIG_KEY_MAPS_REMOTEID = "remoteid";
    static final public String CONFIG_KEY_MAPS_CHECKED = "checked";
}
