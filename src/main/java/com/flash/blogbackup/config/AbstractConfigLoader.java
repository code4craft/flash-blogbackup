/**
 * 
 */
package com.flash.blogbackup.config;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * @author cairne huangyihua@diandian.com
 * @date Dec 23, 2011
 */
public abstract class AbstractConfigLoader implements ConfigLoader {

    protected String filename;

    protected String getFileName() {
        return filename;
    }

    protected File readFile() throws FileNotFoundException {
        File file = new File(getFileName());
        if (!file.exists()) {
            file.getParentFile().mkdir();
            writeConfig(Config.defaultConfig);
        }
        return file;
    }
}
