package com.amazonaws.services.msf.domain;

import org.apache.flink.api.common.serialization.SerializerConfig;
import org.apache.flink.api.common.serialization.SerializerConfigImpl;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataInputViewStreamWrapper;
import org.apache.flink.core.memory.DataOutputView;
import org.apache.flink.core.memory.DataOutputViewStreamWrapper;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

/**
 * Utilities for testing Flink serialization.
 */
public class FlinkSerializationTestUtils {

    /**
     * Round-trip serialization-deserialization of a record, using Flink serializers.
     * Returns the deserialized record por fails, if Flink cannot serialize/deserialize based on the available TypeInfo
     * and serializer configuration.
     *
     * @param record           The record to serialize
     * @param clazz            The class of the record
     * @param serializerConfig The serializer configuration
     * @param <T>              The type of the record
     * @return The deserialized record
     * @throws IOException If an I/O error occurs during serialization or deserialization
     */
    public static <T> T serializeDeserialize(T record, Class<T> clazz, SerializerConfig serializerConfig) throws IOException {

        TypeInformation<T> typeInfo = TypeInformation.of(clazz);
        TypeSerializer<T> serializer = typeInfo.createSerializer(serializerConfig);

        PipedInputStream deserializerIn = new PipedInputStream();
        PipedOutputStream serializerOut = new PipedOutputStream(deserializerIn);

        // Serialize
        DataOutputView serializerOutput = new DataOutputViewStreamWrapper(serializerOut);
        serializer.serialize(record, serializerOutput);

        // Deserialize
        DataInputView deserializerInput = new DataInputViewStreamWrapper(deserializerIn);
        return serializer.deserialize(deserializerInput);
    }

    /**
     * Round-trip serialization-deserialization of a record, using Flink serializers, disabling "generic types",
     * i.e. disabling Kryo fallback.
     * The serialization will fail if Flink has to fallback to Kryo.
     * <p>
     * This method does more than PojoTestUtils.assertSerializedAsPojo(...) because it actually serialize and deserialize
     * the record, so you can verify that the content was correctly serialized/deserialized.
     * PojoTestUtils.assertSerializedAsPojo(...) only verifies that serialization does not fall-back to Kryo. However,
     * if you created an incorrect TypeInfo for the class, the serialization may succeed but missing some of the fields.
     *
     * @param record The record to serialize
     * @param clazz  The class of the record
     * @param <T>    The type of the record
     * @return The deserialized record
     * @throws IOException If an I/O error occurs during serialization or deserialization
     */
    public static <T> T serializeDeserializeNoKryo(T record, Class<T> clazz) throws IOException {
        SerializerConfig serializerConfig = new SerializerConfigImpl();
        serializerConfig.setGenericTypes(false); // Disable Kryo generic types
        return serializeDeserialize(record, clazz, new SerializerConfigImpl());
    }

}
