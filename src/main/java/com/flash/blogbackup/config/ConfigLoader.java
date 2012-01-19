/**
 * 
 */
package com.flash.blogbackup.config;

/**
 * @author cairne huangyihua@diandian.com
 * @date Dec 23, 2011
 */
public interface ConfigLoader {

    public Config readConfig();

    public void writeConfig(Config config);

}
