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


import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

public class InterceptableHttpClient extends HttpClient {

    private final HttpClient client;

    private final List<Interceptor<?>> interceptors;

    public InterceptableHttpClient(
            HttpClient client,
            List<Interceptor<?>> interceptors) {
        this.client = client;
        this.interceptors = interceptors;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return client.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return client.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return client.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return client.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return client.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return client.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return client.authenticator();
    }

    @Override
    public Version version() {
        return client.version();
    }

    @Override
    public Optional<Executor> executor() {
        return client.executor();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> HttpResponse<T> send(HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler) throws IOException, InterruptedException {
        if (interceptors.isEmpty()) {
            return client.send(httpRequest, bodyHandler);
        } else {
            List<Object> values = new ArrayList<>(interceptors.size());
            for (Interceptor<?> interceptor : interceptors) {
                try {
                    values.add(interceptor.getOnRequest().apply(httpRequest));
                } catch (Throwable ignored) {
                }
            }
            try {
                HttpResponse<T> response = client.send(httpRequest, bodyHandler);
                for (int index = 0; index < interceptors.size(); index++) {
                    BiConsumer onResponse = interceptors.get(index).getOnResponse();
                    try {
                        onResponse.accept(response, values.get(index));
                    } catch (Throwable ignored) {
                    }
                }
                return response;
            } catch (IOException exception) {
                for (int index = 0; index < interceptors.size(); index++) {
                    BiConsumer onResponse = interceptors.get(index).getOnError();
                    try {
                        onResponse.accept(exception, values.get(index));
                    } catch (Throwable ignored) {
                    }
                }
                throw exception;
            }
        }
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler) {
        return sendAsync(httpRequest, bodyHandler, null);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest httpRequest, HttpResponse.BodyHandler<T> bodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        if (interceptors.isEmpty()) {
            return client.sendAsync(httpRequest, bodyHandler);
        } else {
            List<Object> values = new ArrayList<>(interceptors.size());
            for (Interceptor<?> interceptor : interceptors) {
                values.add(interceptor.getOnRequest().apply(httpRequest));
            }
            return client.sendAsync(httpRequest, bodyHandler, pushPromiseHandler).handle((response, throwable) -> {
                for (int index = 0; index < interceptors.size(); index++) {
                    if (throwable == null) {
                        BiConsumer onResponse = interceptors.get(index).getOnResponse();
                        onResponse.accept(response, values.get(index));
                    } else {
                        BiConsumer onResponse = interceptors.get(index).getOnError();
                        onResponse.accept(throwable, values.get(index));
                    }
                }
                return response;
            });
        }
    }
}
