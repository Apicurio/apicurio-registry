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

package io.apicurio.registry.rest.client.impl;


import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class Interceptor<T> {

    private final Function<HttpRequest, T> onRequest;

    private final BiConsumer<HttpResponse<?>, T> onResponse;

    private final BiConsumer<Throwable, T> onError;

    Interceptor(Function<HttpRequest, T> onRequest, BiConsumer<HttpResponse<?>, T> onResponse, BiConsumer<Throwable, T> onError) {
        this.onRequest = onRequest;
        this.onResponse = onResponse;
        this.onError = onError;
    }

    Function<HttpRequest, T> getOnRequest() {
        return onRequest;
    }

    BiConsumer<HttpResponse<?>, T> getOnResponse() {
        return onResponse;
    }

    BiConsumer<Throwable, T> getOnError() {
        return onError;
    }
}
