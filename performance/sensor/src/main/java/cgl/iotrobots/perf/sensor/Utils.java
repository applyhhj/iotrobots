package cgl.iotrobots.perf.sensor;

import org.ho.yaml.Yaml;

import java.io.*;
import java.net.URL;
import java.util.*;

public class Utils {
    public static Map loadConfiguration(String fileName) {
        try {
            return (Map) Yaml.load(new FileInputStream(new File(fileName)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map findAndReadConfigFile(String name, boolean mustExist) {
        try {
            HashSet<URL> resources = new HashSet<URL>(findResources(name));
            if (resources.isEmpty()) {
                if (mustExist) throw new RuntimeException("Could not find config file on classpath " + name);
                else return new HashMap();
            }
            if (resources.size() > 1) {
                throw new RuntimeException("Found multiple " + name + " resources."
                        + resources);
            }
            URL resource = resources.iterator().next();
            Map ret = (Map) Yaml.load(new InputStreamReader(resource.openStream()));

            if (ret == null) ret = new HashMap();
            return new HashMap(ret);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<URL> findResources(String name) {
        try {
            Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(name);
            List<URL> ret = new ArrayList<URL>();
            while (resources.hasMoreElements()) {
                ret.add(resources.nextElement());
            }
            return ret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map readStreamConfig(String name) {
        return findAndReadConfigFile(name, true);
    }

    public static Map readStreamConfig() {
        String confFile = System.getProperty("test.conf.file");
        Map conf;
        if (confFile == null || confFile.equals("")) {
            conf = findAndReadConfigFile("test.yaml", false);
        } else {
            conf = readStreamConfig(confFile);
        }
        return conf;
    }
}
