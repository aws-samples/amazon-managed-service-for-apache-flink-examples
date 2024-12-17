package com.amazonaws.services.msf

import com.amazonaws.services.kinesisanalytics.runtime.KinesisAnalyticsRuntime
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.connector.kinesis.sink.KinesisStreamsSink
import org.apache.flink.streaming.api.environment.{LocalStreamEnvironment, StreamExecutionEnvironment}
import org.apache.flink.streaming.connectors.kinesis.FlinkKinesisConsumer

import java.util
import java.util.Properties


object BasicStreamingJob {

  private val streamNameKey = "stream.name"
  private val defaultInputStreamName = "ExampleInputStream"
  private val defaultOutputStreamName = "ExampleOutputStream"
  private val localApplicationPropertiesResource = "/flink-application-properties-dev.json"

  private def getApplicationProperties(env: StreamExecutionEnvironment): util.Map[String, Properties] = {
    env match {
      case localEnv: LocalStreamEnvironment =>
        KinesisAnalyticsRuntime.getApplicationProperties(getClass.getResource(localApplicationPropertiesResource).getPath)
      case _ =>
        KinesisAnalyticsRuntime.getApplicationProperties
    }
  }

  private def createSource(env: StreamExecutionEnvironment): FlinkKinesisConsumer[String] = {
    val applicationProperties = getApplicationProperties(env)
    val inputProperties = applicationProperties.get("InputStream0")

    new FlinkKinesisConsumer[String](inputProperties.getProperty(streamNameKey, defaultInputStreamName),
      new SimpleStringSchema, inputProperties)
  }

  private def createSink(env: StreamExecutionEnvironment): KinesisStreamsSink[String] = {
    val applicationProperties = getApplicationProperties(env)
    val outputProperties = applicationProperties.get("OutputStream0")

    KinesisStreamsSink.builder[String]
      .setKinesisClientProperties(outputProperties)
      .setSerializationSchema(new SimpleStringSchema)
      .setStreamName(outputProperties.getProperty(streamNameKey, defaultOutputStreamName))
      .setPartitionKeyGenerator((element: String) => String.valueOf(element.hashCode))
      .build
  }

  def main(args: Array[String]): Unit = {
    val environment = StreamExecutionEnvironment.getExecutionEnvironment
    
    environment
      .addSource(createSource(environment))
      .sinkTo(createSink(environment))
    environment.execute("Flink Streaming Scala Example")
  }
}