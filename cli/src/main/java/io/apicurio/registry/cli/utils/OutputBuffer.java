package io.apicurio.registry.cli.utils;

import io.apicurio.registry.cli.utils.Utils.Function0Ex;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class OutputBuffer {

    private final Output stdOut;

    private final Output stdErr;

    private final StringBuilder buffer = new StringBuilder();

    private final List<Chunk> chunks = new ArrayList<>();

    public OutputBuffer(Output stdOut, Output stdErr) {
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }

    public void writeStdOutLine(String value) {
        writeStdOutChunk(o -> o.append(value).append('\n'));
    }

    public void writeStdOutChunk(Utils.Function0<StringBuilder> action) {
        action.run(buffer);
        chunks.add(new Chunk(buffer.toString(), Chunk.Target.STDOUT));
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
        chunks.add(new Chunk(buffer.toString(), Chunk.Target.STDERR));
        buffer.setLength(0);
    }

    public void print() {
        for (Chunk chunk : chunks) {
            switch (chunk.getTarget()) {
                case STDOUT -> stdOut.print(chunk.getOutput());
                case STDERR -> stdErr.print(chunk.getOutput());
            }
        }
    }

    @AllArgsConstructor
    @Getter
    private static class Chunk {

        private String output;

        private Target target;

        private enum Target {
            STDOUT,
            STDERR
        }
    }

    @AllArgsConstructor
    private static class WrappedException extends RuntimeException {

        @Getter
        private final Exception wrapped;
    }
}
