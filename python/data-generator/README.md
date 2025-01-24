## Sample data generator

Simple data generator application, publishing stock data to Kinesis or Kafka Data Stream.
This data generator is used by some of the examples in this repository.

Requires `boto3` and `kafka-python-ng`.
> `kafka-python-ng` is used instead of `kafka-python` because `kafka-python` has not been updated for Python 3.8 and above.

### Installation Instructions
Install the required dependencies with the following command:

```bash
python -m pip install -r requirements.txt
```

### Kinesis Usage

```
python stock.py <stream_name>
```
If not stream name is specified, `ExampleInputStream` will be used.

### Kafka Usage

```
python stock_kafka.py <topic_name> <bootstrap_server>
```

If no topic name is specified, `StockInputTopic` will be used.
If no bootstrap server is specified, `localhost:9092` will be used.

### Data example

```
{'event_time': '2024-05-28T19:53:17.497201', 'ticker': 'AMZN', 'price': 42.88}
```
