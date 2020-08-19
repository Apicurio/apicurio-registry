package io.apicurio.registry.client.request;

import io.apicurio.registry.utils.ConcurrentUtil;
import retrofit2.Call;
import retrofit2.Callback;

import javax.ws.rs.WebApplicationException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestHandler {

    public <T> T execute(Call<T> call) {

        final ResultCallback<T> resultCallback = new ResultCallback<T>();

        call.enqueue(resultCallback);

        return resultCallback.getResult();
    }

    private static class ResultCallback<T> implements Callback<T> {

        private static final Logger logger = Logger.getLogger(ResultCallback.class.getName());

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
                return ConcurrentUtil.get(result);
            } catch (RuntimeException e) {
                handleError(e);
            }
            return null;
        }

        private void handleError(Throwable e) {
            if (e instanceof WebApplicationException) {
                throw (WebApplicationException) e;
            }
            logger.log(Level.SEVERE, "Error getting call result", e);
        }
    }
}
