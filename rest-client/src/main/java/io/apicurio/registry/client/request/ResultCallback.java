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

package io.apicurio.registry.client.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.client.exception.RestClientException;
import io.apicurio.registry.client.response.ExceptionMapper;
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.beans.Error;
import io.apicurio.registry.utils.ConcurrentUtil;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Carles Arnal <carles.arnal@redhat.com>
 */
public class ResultCallback<T> implements Callback<T> {

    private static final Logger logger = Logger.getLogger(ResultCallback.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final CompletableFuture<Response<T>> result;

    public ResultCallback() {
        this.result = new CompletableFuture<>();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public void onResponse(Call<T> call, retrofit2.Response<T> response) {
        if (response.isSuccessful()) {
            result.complete(response);
        } else {
            try {
                result.completeExceptionally(new RestClientException(objectMapper.readValue(response.errorBody().byteStream(), Error.class)));
            } catch (IOException | NullPointerException e) {

                logger.log(Level.SEVERE, "Error trying to parse error response message", e);
                final Error error = new Error();
                error.setName(e.getClass().getSimpleName());
                error.setMessage(e.getMessage());
                error.setDetail(getStackTrace(e));
                error.setErrorCode(0);
                result.completeExceptionally(new RestClientException(error));
            }
        }
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        result.completeExceptionally(t);
    }

    public T getResult() throws RestClientException {
        try {
            final Response<T> callResult = ConcurrentUtil.get(result);
            checkIfDeprecated(callResult.headers());
            return callResult.body();
        } catch (RestClientException ex) {
            throw ExceptionMapper.map(ex);
        }
    }

    private static void checkIfDeprecated(okhttp3.Headers headers) {
        String isDeprecated = headers.get(Headers.DEPRECATED);
        if (isDeprecated != null && Boolean.getBoolean(isDeprecated)) {
            String id = headers.get(Headers.ARTIFACT_ID);
            String version = headers.get(Headers.VERSION);
            logger.warning(String.format("Artifact %s [%s] is deprecated", id, version));
        }
    }

    /**
     * Gets the full stack trace for the given exception and returns it as a
     * string.
     * @param t
     */
    private static String getStackTrace(Throwable t) {
        try (StringWriter writer = new StringWriter()) {
            t.printStackTrace(new PrintWriter(writer));
            return writer.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
