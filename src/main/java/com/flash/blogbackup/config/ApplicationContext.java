package com.flash.blogbackup.config;

import java.util.HashMap;
import java.util.Map;

import com.flash.blogbackup.console.Console;

/**
 * @author cairne huangyihua@diandian.com
 * @param <T>
 * @date Dec 28, 2011
 */
@SuppressWarnings("rawtypes")
public class ApplicationContext {

    private static Map<Class, Object> beanMap;

    static {
        beanMap = new HashMap<Class, Object>();
        beanMap.put(Config.class, new XmlConfigLoader().readConfig());
        beanMap.put(Console.class, new Console());
    }

    @SuppressWarnings("unchecked")
    public static <T> T getBean(Class<T> t) {
        return (T) beanMap.get(t);
    }
}
