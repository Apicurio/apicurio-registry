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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;

import java.util.concurrent.CompletableFuture;

public class ResponseHandler<T> implements Handler<AsyncResult<HttpResponse<Buffer>>> {

    final CompletableFuture<T> resultHolder;

    public ResponseHandler(CompletableFuture<T> resultHolder) {
        this.resultHolder = resultHolder;
    }

    @Override
    public void handle(AsyncResult<HttpResponse<Buffer>> event) {

    }
}
