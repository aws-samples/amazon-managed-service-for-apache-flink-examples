package com.amazonaws.services.msf;

import com.google.common.base.Preconditions;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.async.ResultFuture;
import org.apache.flink.streaming.api.functions.async.RichAsyncFunction;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ProcessingFunction extends RichAsyncFunction<IncomingEvent, ProcessedEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingFunction.class);

    private final String apiUrl;
    private final String apiKey;

    private transient AsyncHttpClient client;

    public ProcessingFunction(String apiUrl, String apiKey) {
        Preconditions.checkNotNull(apiUrl, "API URL must not be null");
        Preconditions.checkNotNull(apiKey, "API key must not be null");
        Preconditions.checkArgument(!apiUrl.isEmpty(), "API URL must not be empty");
        Preconditions.checkArgument(!apiKey.isEmpty(), "API key must not be empty");

        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
    }
    /**
     * Instantiate the connection to an async client here to use within asyncInvoke
     */

    @Override
    public void open(Configuration parameters) throws Exception {
        DefaultAsyncHttpClientConfig.Builder clientBuilder = Dsl.config().setConnectTimeout(Duration.ofSeconds(10));
        client = Dsl.asyncHttpClient(clientBuilder);
    }

    @Override
    public void asyncInvoke(IncomingEvent incomingEvent, ResultFuture<ProcessedEvent> resultFuture) {

        // Create a new ProcessedEvent instance
        ProcessedEvent processedEvent = new ProcessedEvent(incomingEvent.getMessage(), null);
        LOG.debug("New request: {}", incomingEvent);

        Future<Response> future = client.prepareGet(apiUrl)
                .setHeader("x-api-key", apiKey)
                .execute();

        // Asynchronously calling API and handling response via Completable Future
        CompletableFuture.supplyAsync(() -> {
            try {
                LOG.debug("Trying to get response for {}", incomingEvent.getId());
                Response response = future.get();
                return response.getStatusCode();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error("Error during async HTTP call: {}", e.getMessage());
                return -1;
            }
        }).thenAccept(statusCode -> {
            if (statusCode == 200) {
                processedEvent.setProcessed("SUCCESS");
                LOG.debug("Success! {}", incomingEvent.getId());
                resultFuture.complete(Collections.singleton(processedEvent));
            } else if (statusCode == 500) { // Retryable error
                LOG.error("Status code 500, retrying shortly...");
                processedEvent.setProcessed("FAIL");
                resultFuture.completeExceptionally(new Throwable(statusCode.toString()));
            } else {
                LOG.error("Unexpected status code: {}", statusCode);
                processedEvent.setProcessed("FAIL");
                resultFuture.completeExceptionally(new Throwable(statusCode.toString()));
            }
        });
    }

    // Defining what gets returned if API times out
    @Override
    public void timeout(IncomingEvent input, ResultFuture<ProcessedEvent> resultFuture) throws Exception {
        LOG.error("{} Timed out! Will retry", input.getId());
        resultFuture.complete(Collections.emptyList());
    }
}
