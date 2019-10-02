package io.apicurio.registry.cluster.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * @author Ales Justin
 */
public class ClusterUtils {
    private static Logger log = LoggerFactory.getLogger(ClusterUtils.class);

    private static Process[] nodes;

    public static void startCluster() throws Exception {
        Properties properties = getClusterProperties();
        if (properties == null) {
            return;
        }
        int N = Integer.parseInt(properties.getProperty("nodes", "2"));
        nodes = new Process[N];
        for (int i = 0; i < N; i++) {
            nodes[i] = startNode(i + 1);
        }
    }

    public static void stopCluster() {
        if (nodes != null) {
            try {
                for (Process node : nodes) {
                    node.destroyForcibly();
                }
            } finally {
                nodes = null;
            }
        }
    }

    public static Properties getClusterProperties() throws Exception {
        String buildDir = System.getProperty("project.build.directory");
        if (buildDir == null) {
            return null;
        }

        String finalName = System.getProperty("project.build.finalName");
        String runner = String.format("%s/%s-runner.jar", buildDir, finalName);
        File runnerFile = new File(runner);
        if (!runnerFile.exists()) {
            log.info("No Registry runner jar ... " + runner);
            return null;
        }

        File file = new File(buildDir, "test-classes/cluster-test.properties");
        Properties properties = new Properties();
        try (InputStream stream = new FileInputStream(file)) {
            properties.load(stream);
        }
        return properties;
    }

    private static Process startNode(int node) throws Exception {
        Properties properties = getClusterProperties();

        String dir = System.getProperty("project.build.directory");
        String name = System.getProperty("project.build.finalName");

        String props = properties.getProperty(String.format("node%s.props", node));
        String cmd = String.format("java -jar %s %s/%s-runner.jar", props, dir, name);

        log.info("Node cmd > " + cmd);

        Set<String> ends = new LinkedHashSet<>();
        int i = 1;
        while (true) {
            String end = properties.getProperty("end." + i);
            if (end == null) {
                break;
            }
            ends.add(end);
            i++;
        }
        log.info("Matching ends: " + ends);

        Process process = Runtime.getRuntime().exec(cmd);
        InputStream is = process.getInputStream();
        InputStream err = process.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        BufferedReader errReader = new BufferedReader(new InputStreamReader(err));
        List<String> msgs = new ArrayList<>();
        final CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                String line;
                String errLine;
                while (process.isAlive()) {
                    line = reader.readLine();
                    if (line != null) {
                        log.info("Registry > " + line);
                        msgs.add(line);
                        if (found(line, ends)) {
                            log.info("Registry started ...");
                            latch.countDown();
                            return;
                        }
                    } else {
                        errLine = errReader.readLine();
                        if (errLine != null) {
                            log.error("Registry err > " + errLine);
                            msgs.add("e: " + errLine);
                        }
                    }
                }
                int exitValue = process.exitValue();
                if (exitValue == 0) {
                    log.info("Registry process exited OK");
                } else {
                    log.error("Registry process exited with error: " + exitValue + ", msgs: " + msgs);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            latch.countDown();
        }, "Node-thread-" + node);
        t.start();
        latch.await();
        return process;
    }

    private static boolean found(String line, Set<String> ends) {
        for (String end : ends) {
            if (line.contains(end)) {
                return true;
            }
        }
        return false;
    }
}
