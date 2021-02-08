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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.util.function.Supplier;

/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class JsonBodyHandler<W> implements HttpResponse.BodyHandler<Supplier<W>> {

	private final Class<W> wClass;

	public JsonBodyHandler(Class<W> wClass) {
		this.wClass = wClass;
	}

	@Override
	public HttpResponse.BodySubscriber<Supplier<W>> apply(HttpResponse.ResponseInfo responseInfo) {
		return asJSON(wClass);
	}

	public static <W> HttpResponse.BodySubscriber<Supplier<W>> asJSON(Class<W> targetType) {
		HttpResponse.BodySubscriber<InputStream> upstream = HttpResponse.BodySubscribers.ofInputStream();

		return HttpResponse.BodySubscribers.mapping(
				upstream,
				inputStream -> toSupplierOfType(inputStream, targetType));
	}

	public static <W> Supplier<W> toSupplierOfType(InputStream inputStream, Class<W> targetType) {
		return () -> {
			try (InputStream stream = inputStream) {
				ObjectMapper objectMapper = new ObjectMapper();
				return objectMapper.readValue(stream, targetType);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		};
	}
}
