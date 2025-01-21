import json
import sys
import time

from kafka import KafkaProducer
from stock import get_data
from traceback import print_exc

DEFAULT_STREAM_NAME = "StockInputTopic"
DEFAULT_BOOTSTRAP_SERVERS = ['localhost:9092']

def generate_kafka_data(topic_name, bootstrap_servers):
    try:
        # Create producer instance
        producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,  # Replace with your Kafka broker(s)
            value_serializer=lambda x: json.dumps(x).encode('utf-8')  # Serialize data to JSON
        )
        while True:
            data = get_data()
            future = producer.send(topic_name, data, key=data['ticker'].encode('utf-8'))
            future.get(timeout=60)
        
            print(f"{data} sent to {topic_name}")
            time.sleep(1)
        
    except Exception as e:
        print_exc()
        print(f"Error sending message: {e}")
        
    finally:
        # Close the producer
        producer.close()


if __name__ == '__main__':
    topic_name = DEFAULT_STREAM_NAME
    bootstrap_servers = DEFAULT_BOOTSTRAP_SERVERS
    if len(sys.argv) == 2:
        topic_name = sys.argv[1]

    print(f"Sending data to '{topic_name}'")
    generate_kafka_data(topic_name, bootstrap_servers)
