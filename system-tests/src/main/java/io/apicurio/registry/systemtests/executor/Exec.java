/*
 * Copyright 2018-2020, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.apicurio.registry.systemtests.executor;
import io.apicurio.registry.systemtests.framework.LoggerUtils;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Class provide execution of external command
 */
public class Exec {
    private static final Logger LOGGER = LoggerUtils.getLogger();
    private Process process;
    private String stdOut;
    private String stdErr;
    private StreamGobbler stdOutReader;
    private StreamGobbler stdErrReader;
    private Path logPath;
    private Map<String, String> env;
    private final boolean appendLineSeparator;
    private Subscriber<String> stdErrProcessor;
    private static final Pattern PATH_SPLITTER = Pattern.compile(System.getProperty("path.separator"));
    protected static final Object LOCK = new Object();

    public Exec() {
        this.appendLineSeparator = true;
    }

    public Exec(Path logPath) {
        this.appendLineSeparator = true;
        this.logPath = logPath;
    }

    public Exec(boolean appendLineSeparator) {
        this.appendLineSeparator = appendLineSeparator;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }

    public void setStdErrProcessor(Subscriber<String> stdErrProcessor) {
        this.stdErrProcessor = stdErrProcessor;
    }

    /**
     * Getter for stdOutput
     *
     * @return string stdOut
     */
    public String getStdOut() {
        return stdOut;
    }

    /**
     * Getter for stdErrorOutput
     *
     * @return string stdErr
     */
    public String getStdErr() {
        return stdErr;
    }

    public boolean isRunning() {
        return process.isAlive();
    }

    public int getRetCode() {
        if (isRunning()) {
            return -1;
        } else {
            return process.exitValue();
        }
    }

    /**
     * Method executes external command
     *
     * @param commands arguments for command
     * @return returns ecode of execution
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public int exec(List<String> commands) throws IOException, InterruptedException, ExecutionException {
        return exec(commands, 0);
    }

    /**
     * Method executes external command
     *
     * @param commands arguments for command
     * @param timeout  timeout in ms for kill
     * @return returns ecode of execution
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public int exec(List<String> commands, int timeout) throws IOException, InterruptedException, ExecutionException {
        return exec(null, commands, timeout);
    }

    private ProcessBuilder getProcessBuilder(List<String> commands) {
        ProcessBuilder builder = new ProcessBuilder()
                .command(commands)
                .directory(new File(System.getProperty("user.dir")));

        if (this.env != null) {
            for (Map.Entry<String, String> entry : this.env.entrySet()) {
                if (entry.getValue() != null) {
                    builder.environment().put(entry.getKey(), entry.getValue());
                } else {
                    builder.environment().remove(entry.getKey());
                }
            }
        }

        return builder;
    }

    private void processStdOut() throws ExecutionException, InterruptedException {
        Future<String> output = readStdOutput();

        try {
            stdOut = output.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            output.cancel(true);
            stdOut = stdOutReader.getData();
        }
    }

    private void processStdErr() throws ExecutionException, InterruptedException {
        Future<String> error = readStdError();

        try {
            stdErr = error.get(500, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            error.cancel(true);
            stdErr = stdErrReader.getData();
        }
    }

    /**
     * Method executes external command
     *
     * @param input    execute commands during execution
     * @param commands arguments for command
     * @param timeout  timeout in ms for kill
     * @return returns ecode of execution
     * @throws IOException
     * @throws InterruptedException
     * @throws ExecutionException
     */
    public int exec(
            String input, List<String> commands, int timeout
    ) throws IOException, InterruptedException, ExecutionException {
        LOGGER.debug("Running command - " + String.join(" ", commands.toArray(new String[0])));

        process = getProcessBuilder(commands).start();

        if (input != null) {
            try (Writer writer = new OutputStreamWriter(process.getOutputStream())) {
                writer.write(input);
            }
        }

        int retCode = 1;

        if (timeout > 0) {
            if (process.waitFor(timeout, TimeUnit.MILLISECONDS)) {
                retCode = process.exitValue();
            } else {
                process.destroyForcibly();
            }
        } else {
            retCode = process.waitFor();
        }

        processStdOut();

        processStdErr();

        storeOutputsToFile();

        return retCode;
    }

    /**
     * Method kills process
     *
     * @throws InterruptedException
     */
    public void stop() throws InterruptedException {
        if (!process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)) {
            LOGGER.warn("Process stop timeout elapsed");
        }
        stdOut = stdOutReader.getData();
        stdErr = stdErrReader.getData();
    }

    /**
     * Get standard output of execution
     *
     * @return future string output
     */
    private Future<String> readStdOutput() {
        stdOutReader = new StreamGobbler(process.getInputStream());
        return stdOutReader.read();
    }

    /**
     * Get standard error output of execution
     *
     * @return future string error output
     */
    private Future<String> readStdError() {
        stdErrReader = new StreamGobbler(process.getErrorStream());
        if (stdErrProcessor != null) {
            stdErrReader.subscribe(stdErrProcessor);
        }
        return stdErrReader.read();
    }

    /**
     * Get stdOut and stdErr and store it into files
     */
    private void storeOutputsToFile() {
        if (logPath != null) {
            try {
                Files.createDirectories(logPath);
                Files.write(Paths.get(logPath.toString(), "stdOutput.log"), stdOut.getBytes());
                Files.write(Paths.get(logPath.toString(), "stdError.log"), stdErr.getBytes());
            } catch (Exception ex) {
                LOGGER.warn("Cannot save output of execution", ex);
            }
        }
    }

    public static boolean isExecutableOnPath(String cmd) {
        for (String dir : PATH_SPLITTER.split(System.getenv("PATH"))) {
            if (new File(dir, cmd).canExecute()) {
                return true;
            }
        }
        return false;
    }

    public static ExecutionResultData executeAndCheck(String... commands) {
        return executeAndCheck(120_000, commands);
    }

    public static ExecutionResultData executeAndCheck(int timeout, String... commands) {
        return executeAndCheck(null, timeout, commands);
    }

    public static ExecutionResultData executeAndCheck(String input, List<String> commands) {
        return executeAndCheck(input, 60_000, commands.toArray(new String[0]));
    }

    public static ExecutionResultData executeAndCheck(String input, int timeout, String... commands) {
        ExecutionResultData results = execute(
                Arrays.asList(commands),
                timeout,
                true,
                true,
                null,
                input
        );

        if (!results.getRetCode()) {
            throw new IllegalStateException(results.getStdErr());
        }

        return results;
    }

    public static ExecutionResultData executeAndCheck(
            List<String> command, int timeout, boolean logToOutput, boolean appendLineSeparator, Map<String, String> env
    ) {
        ExecutionResultData results = execute(command, timeout, logToOutput, appendLineSeparator, env, null);
        if (!results.getRetCode()) {
            throw new IllegalStateException(results.getStdErr());
        }
        return results;
    }

    public static ExecutionResultData execute(List<String> command) {
        return execute(command, 60_000, true, true);
    }

    public static ExecutionResultData execute(String... command) {
        return execute(Arrays.asList(command), 60_000, true, true);
    }

    public static ExecutionResultData execute(List<String> command, boolean logToOutput) {
        return execute(command, 60_000, logToOutput, true);
    }

    public static ExecutionResultData execute(int timeout, boolean logToOutput, String... cmd) {
        return execute(Arrays.asList(cmd), timeout, logToOutput, true);
    }

    public static ExecutionResultData execute(List<String> command, int timeout, boolean logToOutput) {
        return execute(command, timeout, logToOutput, true);
    }

    public static ExecutionResultData execute(
            List<String> command, int timeout, boolean logToOutput, boolean appendLineSeparator
    ) {
        return execute(command, timeout, logToOutput, appendLineSeparator, null);
    }

    public static ExecutionResultData execute(
            List<String> command, int timeout, boolean logToOutput, boolean appendLineSeparator, Map<String, String> env
    ) {
        return execute(command, timeout, logToOutput, appendLineSeparator, env, null);
    }

    public static ExecutionResultData execute(
            List<String> command,
            int timeout,
            boolean logToOutput,
            boolean appendLineSeparator,
            Map<String, String> env,
            String input
    ) {
        Exec executor = new Exec(appendLineSeparator);
        try {
            executor.setEnv(env);
            int ret = executor.exec(input, command, timeout);
            synchronized (LOCK) {
                if (logToOutput) {
                    LOGGER.info("Command - " + String.join(" ", command.toArray(new String[0])));
                    LOGGER.info("Return code: {}", ret);
                    if (!executor.getStdOut().isEmpty()) {
                        LOGGER.info("======STDOUT START=======");
                        LOGGER.info("\n{}", executor.getStdOut());
                        LOGGER.info("======STDOUT END======");
                    }
                    if (!executor.getStdErr().isEmpty()) {
                        LOGGER.info("======STDERR START=======");
                        LOGGER.info("\n{}", executor.getStdErr());
                        LOGGER.info("======STDERR END======");
                    }
                }
            }
            return new ExecutionResultData(ret, executor.getStdOut(), executor.getStdErr());
        } catch (Exception ex) {
            return new ExecutionResultData(1, executor.getStdOut(), executor.getStdErr());
        }
    }

    /**
     * Class represent async reader
     */
    private class StreamGobbler implements Publisher<String> {
        private final InputStream is;
        private final StringBuilder data = new StringBuilder();
        private final Collection<Subscriber<? super String>> subscribers = new ConcurrentLinkedQueue<>();

        /**
         * Constructor of StreamGobbler
         *
         * @param is input stream for reading
         */
        StreamGobbler(InputStream is) {
            this.is = is;
        }

        /**
         * Return data from stream sync
         *
         * @return string of data
         */
        public String getData() {
            return data.toString();
        }

        /**
         * read method
         *
         * @return return future string of output
         */
        public Future<String> read() {
            return CompletableFuture.supplyAsync(() -> {
                try (Scanner scanner = new Scanner(is)) {
                    LOGGER.trace("Reading stream {}", is);
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        data.append(line);
                        if (appendLineSeparator) {
                            data.append(System.lineSeparator());
                        }
                        subscribers.forEach(sub -> sub.onNext(line));
                    }
                    scanner.close();
                    return data.toString();
                } catch (Exception e) {
                    subscribers.forEach(sub -> sub.onError(e));
                    throw new CompletionException(e);
                } finally {
                    subscribers.forEach(Subscriber::onComplete);
                }
            }, runnable -> new Thread(runnable).start());
        }

        @Override
        public void subscribe(Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(null);
            subscribers.add(subscriber);
        }

    }
}