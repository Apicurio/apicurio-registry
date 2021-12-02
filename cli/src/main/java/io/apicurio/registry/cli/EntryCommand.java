/*
 * Copyright 2020 Red Hat
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

package io.apicurio.registry.cli;

import picocli.CommandLine;

import java.util.logging.Level;

import io.apicurio.registry.cli.admin.ExportCommand;
import io.apicurio.registry.cli.admin.ImportCommand;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * The registry cli entry point
 *
 * @author Ales Justin
 */
@QuarkusMain
@CommandLine.Command(
        name = "rscli",
        description = "Simple entry command",
        mixinStandardHelpOptions = true,
        version = "1.0",
        subcommands = {
                CommandLine.HelpCommand.class,
                ListCommand.class,
                CreateCommand.class,
                UpdateCommand.class,
                VersionsCommand.class,
                GetCommand.class,
                MetadataCommand.class,
                DeleteCommand.class,
                UpdateStateCommand.class,
                UpdateMetadataCommand.class,
                ListRulesCommand.class,
                ExportCommand.class,
                ImportCommand.class
        }
)
public class EntryCommand implements Runnable, QuarkusApplication {

    /**
     * @see io.quarkus.runtime.QuarkusApplication#run(java.lang.String[])
     */
    @Override
    public int run(String... args) throws Exception {
        CommandLine cmd = new CommandLine(new EntryCommand());
        cmd.registerConverter(Level.class, Level::parse);
        int exit = cmd.execute(args);
        return exit;
    }

    /**
     * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        CommandLine cmd = new CommandLine(new EntryCommand());
        cmd.usage(System.out);
    }

}