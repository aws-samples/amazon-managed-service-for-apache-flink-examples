## Using custom TypeInformation to avoid serialization falling back to Kryo

This example shows how to define custom TypeInformation for your record objects to prevent Flink from falling back to 
Kryo serialization.

* Flink version: 1.20
* Flink API: DataStream API
* Language: Java (11)
* Flink connectors: Kinesis Sink

The application generates random data internally and, after an aggregation, writes the result to Kinesis.

### Flink serialization

This example illustrates how to define custom TypeInfo for the objects used internally in the application or stored in 
the application state.

#### Background

Every object handed over between operators or stored in application state is serialized using
Flink serialization mechanism. Flink is able to efficiently serialize most of Java simple types, and POJOs where fields are basic types.

When Flink cannot natively serialize an object, it falls back to [Kryo](https://github.com/EsotericSoftware/kryo).
Unfortunately, Kryo serialization is less efficient and has a considerable impact on performance, in particular on CPU
utilization.

One important case where Flink cannot natively serialize a field is with Collections. Due to Java type erasure, at runtime Flink 
cannot discover the type collections' elements, forcing it to fall back to the less efficient Kryo serialization.

You can easily prevent using Kryo for collections, adding a `TypeInfo` to the class and defining a custom TypeInfoFactory that 
describe the content of the collection.

#### Defining a TypeInfo

To prevent this, you can explicitly define the collection'a elements type using the `@TypeInfo` annotation and 
defining a custom `TypeInfoFactory`.
This is demonstrated in these two record classes: 
[`VehicleEvent`](src/main/java/com/amazonaws/services/msf/domain/VehicleEvent.java)
and [`AgggregateVehicleEvent`](src/main/java/com/amazonaws/services/msf/domain/AggregateVehicleEvent.java)

```java
public class VehicleEvent {
    //...

    @TypeInfo(SensorDataTypeInfoFactory.class)
    private Map<String, Long> sensorData = new HashMap<>();

    //...

    public static class SensorDataTypeInfoFactory extends TypeInfoFactory<Map<String, Long>> {
        @Override
        public TypeInformation<Map<String, Long>> createTypeInfo(Type t, Map<String, TypeInformation<?>> genericParameters) {
            return new MapTypeInfo<>(Types.STRING, Types.LONG);
        }
    }
}
```

For more details about Flink serialization, see [Data Types & Serialization](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/serialization/types_serialization/#data-types--serialization) in Flink documentation.

#### Testing serialization

You can test that the serialization of your objects does not fall back to Kryo.

You can use the `PojoTestUtils.assertSerializedAsPojoWithoutKryo(...)` method that is part of Flink test-utils 
(`org.apache.flink:flink-test-utils` dependency).

This assertion succeeds only if the serialization does NOT fall back to Kryo.

However, if you make a mistake in the definition of `TypeInfoFactory`, the assertion above may succeed, but the actual
serialization may fail or, even worse, have unpredictable results.

To prevent this type of bugs, you can test that the serialization actually works.

These tests are demonstrated in two unit tests: 
[`VehicleEventSerializationTest`](src/test/java/com/amazonaws/services/msf/domain/VehicleEventSerializationTest.java)
and [`AggregateVehicleEventSerializationTest`](src/test/java/com/amazonaws/services/msf/domain/AggregateVehicleEventSerializationTest.java).

These tests use a test utility class that can be reused to test any record class:
[`FlinkSerializationTestUtils`](src/test/java/com/amazonaws/services/msf/domain/FlinkSerializationTestUtils.java)

#### More serialization cases 

The test class [MoreKryoSerializationExamplesTest](src/test/java/com/amazonaws/services/msf/domain/MoreKryoSerializationExamplesTest.java)
illustrates more cases where serialization does or does not fall back to Kryo.

Serialization tests are `@Disabled` for those cases where Kryo is used. You can enable these tests to observe how they
actually catch the Kryo fallback.

In particular:
* Any Collection falls back tpo Kryo. You can create custom `TypeInfoFactory` for `List` and `Map`. There is no SetTypeInfo<T> though.
* Some non-basic types like `java.time.Instant` serialize nicely without Kryo
* Most of `java.time.*` types require Kryo
* Nested POJO serialze without Kryo, as long as each component does not require Kryo


### Runtime configuration

When running on Amazon Managed Service for Apache Flink the runtime configuration is read from *Runtime Properties*.

When running locally, the configuration is read from the [`resources/flink-application-properties-dev.json`](resources/flink-application-properties-dev.json) file located in the resources folder.

Runtime parameters:

| Group ID        | Key               | Description                                                                                                                                       | 
|-----------------|-------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `DataGen`       | `records.per.sec` | (optional) Number of generated records per second. Default = 100.                                                                                 
| `OutputStream0` | `stream.arn`      | ARN of the output stream                                                                                                                          |
| `OutputStream0` | `aws.region`      | Region of the output stream. If not specified, it will use the application region or the default region of the AWS profile, when running locally. |

All parameters are case-sensitive.

## Running locally in IntelliJ

> Due to MSK VPC networking, to run this example on your machine you need to set up network connectivity to the VPC where MSK is deployed, for example with a VPN.
> Setting this connectivity depends on your set up and is out of scope for this example.

You can run this example directly in IntelliJ, without any local Flink cluster or local Flink installation.

See [Running examples locally](../running-examples-locally.md) for details.

