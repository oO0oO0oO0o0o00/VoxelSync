package meowcat.voxelsync;

import java.nio.charset.Charset;

public class EncodingUtil {

    static public final Charset getUtf8Charset() {
        return Charset.forName("utf-8");
    }

}
