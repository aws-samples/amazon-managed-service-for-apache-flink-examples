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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProcessingFunction extends RichAsyncFunction<IncomingEvent, ProcessedEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessingFunction.class);

    private final String apiUrl;
    private final String apiKey;
    private final long shutdownWaitTS;
    private final int threadPoolSize;

    private transient AsyncHttpClient client;
    private transient ExecutorService executorService;

    public ProcessingFunction(String apiUrl, String apiKey) {
        Preconditions.checkNotNull(apiUrl, "API URL must not be null");
        Preconditions.checkNotNull(apiKey, "API key must not be null");
        Preconditions.checkArgument(!apiUrl.isEmpty(), "API URL must not be empty");
        Preconditions.checkArgument(!apiKey.isEmpty(), "API key must not be empty");

        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.shutdownWaitTS = 20000;     // Max time (ms) to wait for ExecutorService shutdown before forcing termination
        this.threadPoolSize = 30;       // Number of threads in the ExecutorService pool for async operations
    }


     // Instantiate the connection to an async client here to use within asyncInvoke
    @Override
    public void open(Configuration parameters) throws Exception {
        DefaultAsyncHttpClientConfig.Builder clientBuilder = Dsl.config().setConnectTimeout(Duration.ofSeconds(10));
        client = Dsl.asyncHttpClient(clientBuilder);
        executorService = Executors.newFixedThreadPool(threadPoolSize);
        LOG.info("Initialized ExecutorService with {} threads", threadPoolSize);
    }

    // close Async Client, Executor Service on shutdown
    @Override
    public void close() throws Exception {
        if (client != null) {
            client.close();
        }
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(shutdownWaitTS, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    @Override
    public void asyncInvoke(final IncomingEvent incomingEvent, final ResultFuture<ProcessedEvent> resultFuture) {
        // Create a new ProcessedEvent instance
        ProcessedEvent processedEvent = new ProcessedEvent(incomingEvent.getMessage());
        LOG.debug("New request: {}", incomingEvent);

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    LOG.debug("Trying to get response for {}", incomingEvent.getId());
                    Response response = client.prepareGet(apiUrl)
                            .setHeader("x-api-key", apiKey)
                            .execute()
                            .get();

                    int statusCode = response.getStatusCode();

                    if (statusCode == 200) {
                        LOG.debug("Success! {}", incomingEvent.getId());
                        resultFuture.complete(Collections.singleton(processedEvent));
                    } else if (statusCode == 500) { // Retryable error
                        LOG.error("Status code 500, retrying shortly...");
                        resultFuture.completeExceptionally(new Throwable(String.valueOf(statusCode)));
                    } else {
                        LOG.error("Unexpected status code: {}", statusCode);
                        resultFuture.completeExceptionally(new Throwable(String.valueOf(statusCode)));
                    }
                } catch (Exception e) {
                    LOG.error("Error during async HTTP call: {}", e.getMessage());
                    resultFuture.completeExceptionally(e);
                }
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
