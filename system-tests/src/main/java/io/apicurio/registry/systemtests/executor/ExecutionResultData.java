/*
 * Copyright 2018, EnMasse authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.apicurio.registry.systemtests.executor;

public class ExecutionResultData {
    private final boolean retCode;
    private final String stdOut;
    private final String stdErr;

    public ExecutionResultData(int retCode, String stdOut, String stdErr) {
        this.retCode = retCode == 0;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }

    public boolean getRetCode() {
        return retCode;
    }

    public String getStdOut() {
        return stdOut;
    }

    public String getTrimmedStdOut() {
        return stdOut.trim().replace("'", "");
    }

    public String getStdErr() {
        return stdErr;
    }
}