package com.amazonaws.services.kds.producer;

import com.amazonaws.services.kds.producer.model.StockPrice;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.kinesis.producer.KinesisProducer;
import software.amazon.kinesis.producer.KinesisProducerConfiguration;
import software.amazon.kinesis.producer.UserRecordResult;

import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple KPL producer publishing random StockRecords as JSON to a Kinesis stream
 */
public class KplAggregatingProducer {
    private static final Logger LOG = LoggerFactory.getLogger(KplAggregatingProducer.class);

    private static final String[] TICKERS = {"AAPL", "AMZN", "MSFT", "INTC", "TBV"};
    private static final Random RANDOM = new Random();
    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
                    .withZone(ZoneOffset.UTC);

    private final String streamName;
    private final String streamRegion;
    private final long sleepTimeBetweenRecords;

    private final AtomicLong queuedRecordCount = new AtomicLong();
    private final AtomicLong sentRecordCount = new AtomicLong();

    private static final String DEFAULT_STREAM_REGION = "us-east-1";
    private static final String DEFAULT_STREAM_NAME = "ExampleInputStream";
    private static final long DEFAULT_SLEEP_TIME_BETWEEN_RECORDS = 50L;

    public static void main(String[] args) throws Exception {
        Options options = new Options()
                .addOption(Option.builder()
                        .longOpt("streamName")
                        .hasArg()
                        .desc("Stream name (default: " + DEFAULT_STREAM_NAME + ")")
                        .build())
                .addOption(Option.builder()
                        .longOpt("streamRegion")
                        .hasArg()
                        .desc("Stream AWS region (default: " + DEFAULT_STREAM_REGION + ")")
                        .build())
                .addOption(Option.builder()
                        .longOpt("sleep")
                        .hasArg()
                        .desc("Sleep duration in seconds (default: " + DEFAULT_SLEEP_TIME_BETWEEN_RECORDS + ")")
                        .build());

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();

        try {
            CommandLine cmd = parser.parse(options, args);
            String streamNameValue = cmd.getOptionValue("streamName", DEFAULT_STREAM_NAME);
            String streamRegionValue = cmd.getOptionValue("region", DEFAULT_STREAM_REGION);
            long sleepTimeBetweenRecordsMillis = Long.parseLong(cmd.getOptionValue("sleep", String.valueOf(DEFAULT_SLEEP_TIME_BETWEEN_RECORDS)));

            LOG.info("StreamName: {}, region: {}", streamNameValue, streamNameValue);
            LOG.info("SleepTimeBetweenRecords: {} ms", sleepTimeBetweenRecordsMillis);

            KplAggregatingProducer instance = new KplAggregatingProducer(streamNameValue, streamRegionValue, sleepTimeBetweenRecordsMillis);
            instance.produce();

        } catch (ParseException e) {
            System.err.println("Error parsing command line arguments: " + e.getMessage());
            formatter.printHelp("KplAggregatingProducer", options);
            System.exit(1);
        } catch (NumberFormatException e) {
            System.err.println("Error: sleep parameter must be a valid integer");
            formatter.printHelp("KplAggregatingProducer", options);
            System.exit(1);
        }
    }

    public KplAggregatingProducer(String streamName, String streamRegion, long sleepTimeBetweenRecords) {
        this.streamName = streamName;
        this.streamRegion = streamRegion;
        this.sleepTimeBetweenRecords = sleepTimeBetweenRecords;
    }

    public void produce() {
        KinesisProducerConfiguration config = new KinesisProducerConfiguration()
                .setRegion(streamRegion)
                .setAggregationEnabled(true)
                .setRecordMaxBufferedTime(100)
                .setMaxConnections(4)
                .setRequestTimeout(60000);

        KinesisProducer producer = new KinesisProducer(config);
        ExecutorService callbackThreadPool = Executors.newCachedThreadPool();

        // Setup shutdown hook for cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down...");
            producer.flushSync();
            producer.destroy();
            callbackThreadPool.shutdown();
            try {
                callbackThreadPool.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOG.warn("Thread pool shutdown interrupted", e);
            }
        }));

        try {
            while (true) {
                StockPrice stockPrice = generateRandomStockPrice();
                sendStockPriceToKinesis(stockPrice, streamName, producer, callbackThreadPool);
                Thread.sleep(sleepTimeBetweenRecords); // Avoid flooding the stream
            }
        } catch (InterruptedException e) {
            LOG.warn("Producer interrupted" + e);
        }
    }

    private StockPrice generateRandomStockPrice() {
        String ticker = TICKERS[RANDOM.nextInt(TICKERS.length)];
        double price = RANDOM.nextDouble() * 100.0;
        String timestamp = LocalDateTime.now().format(ISO_FORMATTER);

        return new StockPrice(timestamp, ticker, price);
    }

    private void sendStockPriceToKinesis(StockPrice stockPrice, String streamName, KinesisProducer producer,
                                         ExecutorService callbackThreadPool) {
        try {
            String jsonPayload = String.format(
                    "{\"event_time\": \"%s\", \"ticker\": \"%s\", \"price\": %.2f}",
                    stockPrice.getEventTime(),
                    stockPrice.getTicker(),
                    stockPrice.getPrice()
            );

            byte[] bytes = jsonPayload.getBytes();
            LOG.trace("Sending stock price: {}", jsonPayload);


            ListenableFuture<UserRecordResult> future = producer.addUserRecord(
                    streamName,
                    stockPrice.getTicker(), // Use ticker as partition key
                    ByteBuffer.wrap(bytes));
            long queued = queuedRecordCount.incrementAndGet();
            if (queued % 1000 == 0) {
                long sent = sentRecordCount.get();
                LOG.info("Queued {} records. Sent {} records", queued, sent);
            }


            // Handle success/failure asynchronously
            Futures.addCallback(future, new FutureCallback<UserRecordResult>() {
                @Override
                public void onSuccess(UserRecordResult result) {
                    sentRecordCount.incrementAndGet();
                    LOG.trace("Successfully sent record: {}", result.getSequenceNumber());
                }

                @Override
                public void onFailure(Throwable t) {
                    LOG.error("Failed to send record" + t);
                }
            }, callbackThreadPool);
        } catch (Exception e) {
            LOG.error("Error serializing or sending stock price", e);
        }
    }
}
