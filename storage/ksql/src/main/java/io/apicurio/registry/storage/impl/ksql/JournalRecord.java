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

package io.apicurio.registry.storage.impl.ksql;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;

/**
 * @author eric.wittmann@gmail.com
 */
public class JournalRecord {
    
    private static ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates a journal entry for the given method name and arguments.
     * @param methodName
     * @param arguments
     */
    public static JournalRecord create(String methodName, Object ...arguments) {
        JournalRecord record = new JournalRecord();
        record.setMethod(methodName);
        if (arguments != null && arguments.length > 0) {
            List<JournalRecordArgument> recordArgs = new ArrayList<>(arguments.length);
            for (Object argument : arguments) {
                JournalRecordArgument jra = new JournalRecordArgument();
                // Special handling for ContentHandle
                if (argument instanceof ContentHandle) {
                    byte[] bytes = ((ContentHandle) argument).bytes();
                    String b64 = Base64.encodeBase64String(bytes);
                    jra.setClassName(ContentHandle.class.getName());
                    jra.setValue(mapper.valueToTree(b64));
                } else {
                    jra.setClassName(argument.getClass().getName());
                    jra.setValue(mapper.valueToTree(argument));
                }
                recordArgs.add(jra);
            }
            record.setArguments(recordArgs);
        } else {
            record.setArguments(Collections.emptyList());
        }
        return record;
    }
    
    private String method;
    private List<JournalRecordArgument> arguments;

    /**
     * Constructor.
     */
    public JournalRecord() {
    }

    /**
     * @return the method
     */
    public String getMethod() {
        return method;
    }

    /**
     * @param method the method to set
     */
    public void setMethod(String method) {
        this.method = method;
    }

    /**
     * @return the arguments
     */
    public List<JournalRecordArgument> getArguments() {
        return arguments;
    }

    /**
     * @param arguments the arguments to set
     */
    public void setArguments(List<JournalRecordArgument> arguments) {
        this.arguments = arguments;
    }
    
}
