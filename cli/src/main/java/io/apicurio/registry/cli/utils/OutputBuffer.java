package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.utils.Utils.Function0Ex;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class OutputBuffer {

    private final Output stdOut;
    private final Output stdErr;
    private final StringBuilder buffer = new StringBuilder();
    private final List<Chunk> chunks = new ArrayList<>();
    private int printedUpTo = 0;

    public OutputBuffer(Output stdOut, Output stdErr) {
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }

    public void writeStdOutLine(String value) {
        writeStdOutChunk(o -> o.append(value).append('\n'));
    }

    public void writeStdOutChunk(Utils.Function0<StringBuilder> action) {
        action.run(buffer);
        chunks.add(new Chunk(buffer.toString(), Chunk.Target.STDOUT, Severity.INFO));
        buffer.setLength(0);
    }

    @SuppressWarnings("unchecked")
    public <E extends Exception> void writeStdOutChunkWithException(Function0Ex<StringBuilder, E> action) throws E {
        try {
            writeStdOutChunk(o -> {
                try {
                    action.run(o);
                } catch (RuntimeException ex) {
                    throw ex;
                } catch (Exception ex) {
                    throw new WrappedException(ex);
                }
            });
        } catch (WrappedException wex) {
            throw (E) wex.getWrapped();
        }
    }

    public void writeStdErrChunk(Consumer<StringBuilder> action) {
        action.accept(buffer);
        chunks.add(new Chunk(buffer.toString(), Chunk.Target.STDERR, Severity.INFO));
        buffer.setLength(0);
    }

    public void writeSuccess(Consumer<StringBuilder> action) {
        action.accept(buffer);
        chunks.add(new Chunk(buffer.toString(), Chunk.Target.STDOUT, Severity.SUCCESS));
        buffer.setLength(0);
    }

    public void writeWarning(Consumer<StringBuilder> action) {
        action.accept(buffer);
        chunks.add(new Chunk(buffer.toString(), Chunk.Target.STDERR, Severity.WARNING));
        buffer.setLength(0);
    }

    public void writeError(Consumer<StringBuilder> action) {
        action.accept(buffer);
        chunks.add(new Chunk(buffer.toString(), Chunk.Target.STDERR, Severity.ERROR));
        buffer.setLength(0);
    }

    public void print() {
        for (int i = printedUpTo; i < chunks.size(); i++) {
            var chunk = chunks.get(i);
            String outText = chunk.getOutput();

            switch (chunk.getSeverity()) {
                case SUCCESS -> outText = ColorUtil.colorizeSuccess(outText);
                case WARNING -> outText = ColorUtil.colorizeWarning(outText);
                case ERROR -> outText = ColorUtil.colorizeError(outText);
                case INFO, NONE -> {

                }
            }

            switch (chunk.getTarget()) {
                case STDOUT -> stdOut.print(outText);
                case STDERR -> stdErr.print(outText);
            }
        }
        printedUpTo = chunks.size();
    }

    @AllArgsConstructor
    @Getter
    private static class Chunk {
        private String output;
        private Target target;
        private Severity severity;

        private enum Target {
            STDOUT,
            STDERR
        }
    }

    private enum Severity {
        NONE, INFO, SUCCESS, WARNING, ERROR
    }

    @AllArgsConstructor
    private static class WrappedException extends RuntimeException {
        @Getter
        private final Exception wrapped;
    }
}