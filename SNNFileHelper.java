package ext.sni.helper;

import java.io.File;

public class SNNFileHelper {

    public static void createDirectory(File directory) {
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                throw new RuntimeException("Failed to create directory: " + directory.getAbsolutePath());
            }
        }
    }
}