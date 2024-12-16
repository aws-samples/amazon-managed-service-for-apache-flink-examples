package com.amazonaws.services.msf.domain;

import org.apache.flink.api.common.typeinfo.TypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInfoFactory;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.types.PojoTestUtils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This test collects different examples of record object that will or will not fall-back to Kryo for serialization.
 * <p>
 * Serialization tests are included for all examples. Those tests that are supposed to fail are disabled.
 * Remove the @Disabled annotation to observe them actually failing.
 * <p>
 * Refer Data Types & Serialization in Flink documentation for further details
 * https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/serialization/types_serialization/#data-types--serialization
 */
public class MoreKryoSerializationExamplesTest {

    /**
     * This POJO contains a Collection field.
     * It falls back to Kryo unless you add a @TypeInfo
     */
    public static class PojoWithCollection {
        private List<String> elements = new ArrayList<>();
        private Map<String, Integer> mapOfElements = new HashMap<>();

        // Flink does not have a org.apache.flink.api.java.typeutils.SetTypeInfo<T>.
        // You cannot easily define a TypeInfoFactory for a Set
        private Set<String> setOfElements = new HashSet<>();

        public List<String> getElements() {
            return elements;
        }

        public void setElements(List<String> elements) {
            this.elements = elements;
        }

        public Map<String, Integer> getMapOfElements() {
            return mapOfElements;
        }

        public void setMapOfElements(Map<String, Integer> mapOfElements) {
            this.mapOfElements = mapOfElements;
        }

        public Set<String> getSetOfElements() {
            return setOfElements;
        }

        public void setSetOfElements(Set<String> setOfElements) {
            this.setOfElements = setOfElements;
        }
    }

    // THIS TEST IS SUPPOSED TO FAIL
    @Test
    @Disabled
    void pojoWithCollectionShouldNotFallbackToKryo() {
        PojoTestUtils.assertSerializedAsPojoWithoutKryo(PojoWithCollection.class);
    }


    //---------------------------------------------------------------------

    /// Not all non-basic types fall back to Kryo.
    /// For example, java.time.Instant serializes nicely

    // POJO containing a java.time.Instant field
    public static class PojoWithInstant {
        private Instant timestamp;

        public Instant getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
        }
    }

    // Serialization of Instant field does not fall back Kryo
    @Test
    void pojoWithInstantShouldNotFallbackToKryo() {
        PojoTestUtils.assertSerializedAsPojoWithoutKryo(PojoWithInstant.class);
    }

    // Serialization of Instant field works correctly
    @Test
    void pojoWithInstantShouldSerializeAndDeserializeWithoutKryo() throws IOException {
        PojoWithInstant record = new PojoWithInstant();
        record.setTimestamp(Instant.now());

        PojoWithInstant deserialized = FlinkSerializationTestUtils.serializeDeserializeNoKryo(record, PojoWithInstant.class);

        assertEquals(record.getTimestamp(), deserialized.getTimestamp());
    }

    //---------------------------------------------------------------------

    /// Not all java.time classes serialize without Kryo.
    /// ZonedDateTime and Duration, for example, require falliing back to Kryo

    // Pojo with a java.time.ZonedDateTime, java.time.LocalDateTime, and java.time.Duration
    public static class PojoWithNonDirectlySerializableTimeFields {
        private ZonedDateTime zonedDateTime;
        private Duration duration;
        private LocalDateTime localDateTime;

        public ZonedDateTime getZonedDateTime() {
            return zonedDateTime;
        }

        public void setZonedDateTime(ZonedDateTime zonedDateTime) {
            this.zonedDateTime = zonedDateTime;
        }

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }


        public LocalDateTime getLocalDateTime() {
            return localDateTime;
        }

        public void setLocalDateTime(LocalDateTime localDateTime) {
            this.localDateTime = localDateTime;
        }
    }


    // THIS TEST WILL FAIL
    @Test
    @Disabled
    void pojoWithZonedDateTimeShouldNotFallbackToKryo() {
        PojoTestUtils.assertSerializedAsPojoWithoutKryo(PojoWithNonDirectlySerializableTimeFields.class);
    }


    //---------------------------------------------------------------------

    /// Nested POJOs have no issue being serialized without Kryo,
    /// as long as all components are individually serializable without Kryo.

    public static class NestedPojo {
        String name;
        Address address;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Address getAddress() {
            return address;
        }

        public void setAddress(Address address) {
            this.address = address;
        }
    }

    public static class Address {
        private String street;
        private String postCode;

        public String getStreet() {
            return street;
        }

        public void setStreet(String street) {
            this.street = street;
        }

        public String getPostCode() {
            return postCode;
        }

        public void setPostCode(String postCode) {
            this.postCode = postCode;
        }
    }

    // Nested POJO serialization does not fall-back to Kryo
    @Test
    void nestedPojosShouldNotFallbackToKryo() {
        PojoTestUtils.assertSerializedAsPojoWithoutKryo(NestedPojo.class);
    }


    // Nested POJOs serialize and deserialize nicely
    @Test
    void nestedPojoSerializeAndDeserializeWithoutKryo() throws IOException {
        NestedPojo record = new NestedPojo();
        record.setName("My Name");
        Address address = new Address();
        address.setStreet("Long Str");
        address.setPostCode("XYZ123");
        record.setAddress(address);

        NestedPojo deserialized = FlinkSerializationTestUtils.serializeDeserializeNoKryo(record, NestedPojo.class);

        assertEquals(record.getName(), deserialized.getName());
        assertEquals(record.getAddress().getPostCode(), deserialized.getAddress().getPostCode());
        assertEquals(record.getAddress().getStreet(), deserialized.getAddress().getStreet());
    }
}
