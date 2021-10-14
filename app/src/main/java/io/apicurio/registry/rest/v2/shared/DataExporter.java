/*
 * Copyright 2021 Red Hat
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

package io.apicurio.registry.rest.v2.shared;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipOutputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.slf4j.Logger;

import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.types.Current;
import io.apicurio.registry.utils.impexp.EntityWriter;

/**
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class DataExporter {

    @Inject
    Logger log;

    @Inject
    @Current
    RegistryStorage storage;

    /**
     * Exports all registry data.
     */
    public Response exportData() {
        StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(OutputStream os) throws IOException, WebApplicationException {
                try {
                    ZipOutputStream zip = new ZipOutputStream(os, StandardCharsets.UTF_8);
                    EntityWriter writer = new EntityWriter(zip);
                    AtomicInteger errorCounter = new AtomicInteger(0);
                    storage.exportData(entity -> {
                        try {
                            writer.writeEntity(entity);
                        } catch (Exception e) {
                            // TODO do something interesting with this
                            e.printStackTrace();
                            errorCounter.incrementAndGet();
                        }
                        return null;
                    });

                    // TODO if the errorCounter > 0, then what?

                    zip.flush();
                    zip.close();
                } catch (IOException e) {
                    throw e;
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        };

        return Response.ok(stream).type("application/zip").build();
    }

}
