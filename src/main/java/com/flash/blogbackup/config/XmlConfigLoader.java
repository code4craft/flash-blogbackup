package com.flash.blogbackup.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map.Entry;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;
import org.dom4j.tree.FlyweightText;

/**
 * @author cairne huangyihua@diandian.com
 * @date Dec 23, 2011
 */
public class XmlConfigLoader extends AbstractConfigLoader {

    private final String filename = "config.xml";

    /**
     * @return
     */
    public Config readConfig() {
        Config config = new Config();
        SAXReader reader = new SAXReader();
        try {
            Document doc = reader.read(readFile());
            Element root = doc.getRootElement();
            for (Object obj : root.elements()) {
                Element element = (Element) obj;
                config.put(element.getName(), element.getText());
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        return config;
    }

    @Override
    protected String getFileName() {
        return filename;
    }

    /**
     * @param config
     */
    public void writeConfig(Config config) {
        File file = new File(filename);
        if (!file.exists()) {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdir();
            }
        }
        Document document = DocumentHelper.createDocument();
        Element root = document.addElement("config");
        for (Entry<String, String> entry : config.getConfigMap().entrySet()) {
            root.addElement(entry.getKey()).add(new FlyweightText(entry.getValue()));
        }
        OutputFormat format = null;
        format = OutputFormat.createPrettyPrint();
        //设定编码
        format.setEncoding("UTF-8");
        try {
            XMLWriter xmlWriter = new XMLWriter(new FileOutputStream(getFileName()), format);
            xmlWriter.write(document);
            xmlWriter.flush();
            xmlWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
