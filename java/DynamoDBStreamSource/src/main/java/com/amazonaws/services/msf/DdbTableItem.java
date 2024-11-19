package com.amazonaws.services.msf;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class DdbTableItem {

    private String partitionKey;
    private String sortKey;
    private String fieldOne;
    private String fieldTwo;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("partition-key")
    public String getPartitionKey() {
        return partitionKey;
    }


    public void setPartitionKey(String partitionKey) {
        this.partitionKey = partitionKey;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("sort-key")
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    public String getFieldOne() {
        return fieldOne;
    }

    public void setFieldOne(String fieldOne) {
        this.fieldOne = fieldOne;
    }

    public String getFieldTwo() {
        return fieldTwo;
    }

    public void setFieldTwo(String fieldTwo) {
        this.fieldTwo = fieldTwo;
    }

    @Override
    public String toString() {
        return "DdbTableSchema{" +
                "partitionKey='" + partitionKey + '\'' +
                ", sortKey='" + sortKey + '\'' +
                ", fieldOne='" + fieldOne + '\'' +
                ", fieldTwo='" + fieldTwo + '\'' +
                '}';
    }
}
