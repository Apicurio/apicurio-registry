package io.registry.client.callback;

import retrofit2.Call;
import retrofit2.Callback;

import javax.ws.rs.WebApplicationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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