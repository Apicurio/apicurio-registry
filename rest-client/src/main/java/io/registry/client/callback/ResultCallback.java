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
package io.registry.client.callback;

import retrofit2.Call;
import retrofit2.Callback;

import javax.ws.rs.WebApplicationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;


/**
 * @author Carles Arnal <carnalca@redhat.com>
 */
public class ResultCallback<T> implements Callback<T> {

    private final CompletableFuture<T> result;

    public ResultCallback() {
        this.result = new CompletableFuture<>();
    }

    @Override
    public void onResponse(Call<T> call, retrofit2.Response<T> response) {
        if (response.isSuccessful()) {
            result.complete(response.body());
        } else {
            result.completeExceptionally(new WebApplicationException(response.message(), response.code()));
        }
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        result.completeExceptionally(t);
    }

    public T getResult() {
        try {
            return result.get();
        } catch (InterruptedException | ExecutionException e) {
            handleError(e.getCause());
        }
        return null;
    }

    private void handleError(Throwable e) {
        if (e instanceof WebApplicationException) {
            throw (WebApplicationException) e;
        }
    }
}