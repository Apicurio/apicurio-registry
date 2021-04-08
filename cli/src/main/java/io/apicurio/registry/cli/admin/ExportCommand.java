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

package io.apicurio.registry.cli.admin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import io.apicurio.registry.cli.AbstractCommand;
import io.apicurio.registry.utils.IoUtil;
import picocli.CommandLine;

/**
 * @author Fabian Martinez
 */
@CommandLine.Command(name = "export", description = "Export registry data into a zip file")
public class ExportCommand extends AbstractCommand {

    @CommandLine.Option(names = {"-f", "--file"}, description = "file to write the registry exported data ", defaultValue = "registry-export.zip")
    File file;

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {

        File output = file;
        try (FileOutputStream fos = new FileOutputStream(output)) {

            println("Exporting registry data to " + output.getName());

            InputStream export = getClient().exportData();

            IoUtil.copy(export, fos);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

}
