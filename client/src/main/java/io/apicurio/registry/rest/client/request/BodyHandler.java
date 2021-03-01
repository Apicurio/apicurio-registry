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

package io.apicurio.registry.rest.client.request;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.function.Supplier;

/**
 * @author Carles Arnal 'carnalca@redhat.com'
 */
public class BodyHandler<W> implements HttpResponse.BodyHandler<Supplier<W>> {

    private final TypeReference<W> wClass;
    private static final ObjectMapper mapper = new ObjectMapper();

    public BodyHandler(TypeReference<W> wClass) {
        this.wClass = wClass;
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public HttpResponse.BodySubscriber<Supplier<W>> apply(HttpResponse.ResponseInfo responseInfo) {
        return asJSON(wClass, responseInfo);
    }

    public static <W> HttpResponse.BodySubscriber<Supplier<W>> asJSON(TypeReference<W> targetType, HttpResponse.ResponseInfo responseInfo) {
        HttpResponse.BodySubscriber<InputStream> upstream = HttpResponse.BodySubscribers.ofInputStream();
        return HttpResponse.BodySubscribers.mapping(
                upstream,
                inputStream -> toSupplierOfType(inputStream, targetType, responseInfo));
    }

    @SuppressWarnings("unchecked")
    public static <W> Supplier<W> toSupplierOfType(InputStream body, TypeReference<W> targetType, HttpResponse.ResponseInfo responseInfo) {
        return () -> {
            try {
                if (isFailure(responseInfo)) {
                    throw ErrorHandler.handleErrorResponse(body, responseInfo);
                } else {
                    //TODO think of a better solution to this
                    final String typeName = targetType.getType().getTypeName();
                    if (typeName.contains("InputStream")) {
                        return (W) body;
                    } else if (typeName.contains("Void")) {
                        //Intended null return
                        return null;
                    } else {
                        return mapper.readValue(body, targetType);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static boolean isFailure(HttpResponse.ResponseInfo responseInfo) {
        return responseInfo.statusCode() / 100 != 2;
    }
}