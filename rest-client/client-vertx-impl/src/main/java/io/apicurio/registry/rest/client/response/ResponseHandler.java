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

package io.apicurio.registry.rest.client.response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.rest.client.impl.ErrorHandler;
import io.apicurio.registry.utils.IoUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

import java.util.concurrent.CompletableFuture;

public class ResponseHandler<T> implements Handler<AsyncResult<HttpResponse<Buffer>>> {

    final CompletableFuture<T> resultHolder;
    final TypeReference<T> targetType;
    private static final ObjectMapper mapper = new ObjectMapper();

    public ResponseHandler(CompletableFuture<T> resultHolder, TypeReference<T> targetType) {
        this.resultHolder = resultHolder;
        this.targetType = targetType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(AsyncResult<HttpResponse<Buffer>> event) {
        try {
            if (event.result() != null && isFailure(event.result().statusCode())) {
                if (event.result().body() != null) {
                    resultHolder.completeExceptionally(ErrorHandler.handleErrorResponse(IoUtil.toStream(event.result().body().getBytes()), event.result().statusCode()));
                } else {
                    resultHolder.completeExceptionally(ErrorHandler.handleErrorResponse(null, event.result().statusCode()));
                }
            } else if (event.succeeded()) {
                final HttpResponse<Buffer> result = event.result();
                final String typeName = targetType.getType().getTypeName();
                if (typeName.contains("InputStream")) {
                    resultHolder.complete((T) IoUtil.toStream(result.body().getBytes()));
                } else if (typeName.contains("Void")) {
                    //Intended null return
                    resultHolder.complete(null);
                } else {
                    resultHolder.complete(mapper.readValue(result.body().getBytes(), targetType));
                }
            } else {
                resultHolder.completeExceptionally(event.cause());
            }
        } catch (Exception e) {
            resultHolder.completeExceptionally(e);
        }
    }

    private static boolean isFailure(int statusCode) {
        return statusCode / 100 != 2;
    }
}
