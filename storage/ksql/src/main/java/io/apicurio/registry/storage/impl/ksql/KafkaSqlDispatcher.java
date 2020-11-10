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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.apache.commons.codec.binary.Base64;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.storage.RegistryStorage;
import io.apicurio.registry.storage.RegistryStorageException;
import io.apicurio.registry.types.RegistryException;

/**
 * Responsible for dispatching a {@link JournalRecord} to the storage.  This basically means analyzing the {@link JournalRecord}
 * to determine which method on the storage should be invoked, unmarshalling the arguments, and then invoking the correct 
 * storage method.  The proper value (or exception) should then be returned.
 * @author eric.wittmann@gmail.com
 */
@ApplicationScoped
public class KafkaSqlDispatcher {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Dispatch the given journal record back to the storage.
     * @param record
     * @param storage
     */
    public Object dispatchTo(JournalRecord record, RegistryStorage storage) {
        try {
            Class<?> [] types = new Class<?> [record.getArguments().size()];
            Object [] args = new Object[record.getArguments().size()];
            List<JournalRecordArgument> arguments = record.getArguments();
            int idx = 0;
            for (JournalRecordArgument argument : arguments) {
                Class<?> c = Class.forName(argument.getClassName());
                types[idx] = c;
                if (c.equals(ContentHandle.class)) {
                    String b64Data = mapper.treeToValue(argument.getValue(), String.class);
                    byte[] data = Base64.decodeBase64(b64Data);
                    args[idx] = ContentHandle.create(data);
                } else {
                    args[idx] = mapper.treeToValue(argument.getValue(), c);
                }
                idx++;
            }
            Method method = storage.getClass().getMethod(record.getMethod(), types);
            return method.invoke(storage, args);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException e) {
            return new RegistryException(e);
        } catch (InvocationTargetException e) {
            return unwrapInvocationTargetException(e);
        } catch (Throwable t) {
            return new RegistryException(t);
        }
    }

    private static RegistryException unwrapInvocationTargetException(InvocationTargetException e) {
        Throwable cause = e;
        while (!(cause instanceof RegistryException) && cause != null) {
            cause = e.getCause();
        }
        if (cause != null) {
            return (RegistryException) cause;
        }
        return new RegistryStorageException(e.getCause());
    }

}
