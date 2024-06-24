## Sample data generator

Simple data generator application, publishing stock data to Kinesis Data Stream.
This data generator is used by some of the examples in this repository.

Requires `boto3`

### Usage

```
python stock.py <stream_name>
```
If not stream name is specified, `ExampleInputStream` will be used.

### Data example

```
{'event_time': '2024-05-28T19:53:17.497201', 'ticker': 'AMZN', 'price': 42.88}
```
