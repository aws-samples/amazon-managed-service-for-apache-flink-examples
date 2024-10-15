package com.amazonaws.services.msf;

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
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ProcessingFunction extends RichAsyncFunction<IncomingEvent, ProcessedEvent> {

    public Properties endpointProperties;
    private transient AsyncHttpClient client;

    ProcessingFunction(Properties endpointProperties) {
        this.endpointProperties = endpointProperties;

    }

    private static final Logger LOG = LoggerFactory.getLogger(ProcessingFunction.class);

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
        ProcessedEvent processedEvent = new ProcessedEvent(
                incomingEvent.getMessage(),
                null);
        LOG.debug("new request {}", incomingEvent);

        // Defining API request
        Future<Response> future = client.prepareGet(endpointProperties.getProperty("api.url"))
                .setHeader("x-api-key", endpointProperties.getProperty("api.key"))
                .execute();

        // Asynchronously calling API and handling response via Completable Future
        CompletableFuture.supplyAsync(() -> {
            try {
                LOG.debug("trying to get response for {}", incomingEvent.getId());
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
            } else if (statusCode == 500) { // retryable error
                LOG.error("status code 500, retrying shortly...");
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
