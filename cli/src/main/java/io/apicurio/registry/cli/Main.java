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

import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import picocli.CommandLine;
import picocli.shell.jline2.PicocliJLineCompleter;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Main to run as interactive shell.
 *
 * @author Ales Justin
 */
public class Main {
    public static void main(String[] args) {
        try {
            ConsoleReader reader = new ConsoleReader();
            CommandLine.IFactory factory = new CachingFactory(CommandLine.defaultFactory());

            // set up the completion
            MainCommand commands = new MainCommand(reader);
            CommandLine cmd = new CommandLine(commands, factory);
            cmd.registerConverter(Level.class, Level::parse);
            reader.addCompleter(new PicocliJLineCompleter(cmd.getCommandSpec()));

            // start the shell and process input until the user quits with Ctl-D or exit/x
            reader.getOutput().append("Welcome to Apicurio Registry CLI\n");
            String line;
            while ((line = reader.readLine("$> ")) != null) {
                ArgumentCompleter.ArgumentList list = new ArgumentCompleter.WhitespaceArgumentDelimiter()
                        .delimit(line, line.length());
                cmd.execute(list.getArguments());
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static class CachingFactory implements CommandLine.IFactory {
        private Map<Class<?>, Object> instances = new HashMap<>();

        private CommandLine.IFactory factory;

        public CachingFactory(CommandLine.IFactory factory) {
            this.factory = factory;
        }

        @Override
        public <K> K create(Class<K> cls) {
            Object result = instances.computeIfAbsent(cls, clazz -> {
                try {
                    return factory.create(clazz);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            return cls.cast(result);
        }
    }
}
