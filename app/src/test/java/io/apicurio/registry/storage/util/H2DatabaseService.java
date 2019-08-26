/*
 * Copyright 2019 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.storage.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * Simple class that starts up an in-memory H2 database.
 *
 * @author eric.wittmann@gmail.com
 */
public class H2DatabaseService {

    private static Logger log = LoggerFactory.getLogger(H2DatabaseService.class);

    private Process process = null;

    /**
     * Called to start up the H2 database.
     *
     * @throws Exception
     */
    public void start() throws Exception {
        log.info("Starting H2 server");

        PropertiesLoader properties = new PropertiesLoader();
        String jar = properties.get("h2.jar.file.path");
        String port = properties.get("h2.port");

        String[] cmdArray = {
                "java", "-cp", jar, "org.h2.tools.Server",
                "-tcp", "-tcpPort", port, "-tcpAllowOthers",
                "-ifNotExists"
        };

        log.debug("H2 > " + Arrays.toString(cmdArray));

        process = Runtime.getRuntime().exec(cmdArray);
        InputStream is = process.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        final CountDownLatch latch = new CountDownLatch(1);
        Thread t = new Thread(() -> {
            try {
                String line = null;
                while (process.isAlive()) {
                    line = reader.readLine();
                    if (line != null) {
                        log.debug("H2 > " + line);
                    }
                    if (line != null && line.contains("TCP server running")) {
                        latch.countDown();
                    }
                }
                int retCode = process.exitValue();
                if (retCode == 0) {
                    log.info("H2 Server process exited OK");
                } else {
                    throw new RuntimeException("H2 Server process exited with error: " + retCode);
                }
                latch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
        latch.await();
        log.info("H2 server started!");
    }

    public void stop() {
        if (this.process != null) {
            this.process.destroyForcibly();
            log.info("H2 server stopped");
        }
    }
}
