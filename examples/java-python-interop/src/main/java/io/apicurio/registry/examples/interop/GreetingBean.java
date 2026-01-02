/*
 * Copyright 2024 Red Hat Inc
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

package io.apicurio.registry.examples.interop;

/**
 * A simple POJO representing a greeting message.
 * Used with JSON Schema serialization in the Java-Python interoperability example.
 */
public class GreetingBean {

    private String message;
    private long time;
    private String sender;
    private String source;

    public GreetingBean() {
    }

    public GreetingBean(String message, long time, String sender, String source) {
        this.message = message;
        this.time = time;
        this.sender = sender;
        this.source = source;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @Override
    public String toString() {
        return "GreetingBean{" +
                "message='" + message + '\'' +
                ", time=" + time +
                ", sender='" + sender + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
