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

package io.apicurio.registry.examples.simple.json;

import java.io.InputStream;
import java.util.Collections;

import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;

import io.apicurio.rest.client.JdkHttpClientProvider;
import io.apicurio.rest.client.error.ApicurioRestClientException;
import io.apicurio.rest.client.error.RestClientErrorHandler;
import io.apicurio.rest.client.request.Operation;
import io.apicurio.rest.client.request.Request;
import io.apicurio.rest.client.request.Request.RequestBuilder;
import io.apicurio.rest.client.spi.ApicurioHttpClient;

/**
 * @author eric.wittmann@gmail.com
 */
@SuppressWarnings("unchecked")
public class MessagePublisher {
    private static final ApicurioHttpClient httpClient;
    static {
        httpClient = new JdkHttpClientProvider().create("http://localhost:12345", Collections.EMPTY_MAP, null, new RestClientErrorHandler() {
            @Override
            @SuppressWarnings("serial")
            public ApicurioRestClientException parseInputSerializingError(JsonProcessingException ex) {
                return new ApicurioRestClientException(ex.getMessage()) {};
            }

            @Override
            @SuppressWarnings("serial")
            public ApicurioRestClientException parseError(Exception ex) {
                return new ApicurioRestClientException(ex.getMessage()) {};
            }

            @Override
            @SuppressWarnings("serial")
            public ApicurioRestClientException handleErrorResponse(InputStream body, int statusCode) {
                return new ApicurioRestClientException("Error with code: "+ statusCode)  {};
            }
        });
    }

    /**
     * @param message
     */
    @SuppressWarnings({ "rawtypes" })
    public void publishMessage(MessageBean message) {
        JSONObject messageObj = new JSONObject(message);
        String data = messageObj.toString();
        Request request = new RequestBuilder().operation(Operation.POST).data(data).responseType(new TypeReference<Void>() {}).build();
        httpClient.sendRequest(request);

        System.out.println("Produced message: " + message);
    }

}
