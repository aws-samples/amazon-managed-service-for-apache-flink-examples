## Simple KPL aggregating data generator

This module contains a simple random data generator which publishes stock prices records to a Kinesis Data Streams
using KPL aggregation.

1. Compile: `mvn package`
2. Run: `java -jar target/kpl-producer-1.0.jar --streamName <stream-name> --streamRegion <aws-region> --sleep 10`

Runtime parameters (all optional)

* `streamName`: default `ExampleInputStream`
* `stramRegion`: default `us-east-1`
* `sleep`: default 10 (milliseconds between records)


### Data example

```
{'event_time': '2024-05-28T19:53:17.497201', 'ticker': 'AMZN', 'price': 42.88}
```