import datetime
import json
import random
import boto3
import sys

DEFAULT_STREAM_NAME = "ExampleInputStream"

def get_data():
    return {
        'event_time': datetime.datetime.now().isoformat(),
        'ticker': random.choice(['AAPL', 'AMZN', 'MSFT', 'INTC', 'TBV']),
        'price': round(random.random() * 100, 2)	}


def generate(stream_name, kinesis_client):
    while True:
        data = get_data()
        print(data)
        kinesis_client.put_record(
            StreamName=stream_name,
            Data=json.dumps(data),
            PartitionKey="partitionkey")


if __name__ == '__main__':
    stream_name = DEFAULT_STREAM_NAME
    if len(sys.argv) == 2:
        stream_name = sys.argv[1]

    print(f"Sending data to '{stream_name}'")
    generate(stream_name, boto3.client('kinesis'))
