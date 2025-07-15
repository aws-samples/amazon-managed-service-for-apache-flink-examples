package com.amazonaws.services.msf.domain;

import com.github.javafaker.Faker;
import org.apache.flink.connector.datagen.source.GeneratorFunction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Generator function that creates realistic fake User objects using JavaFaker.
 * Implements GeneratorFunction to work with DataGeneratorSource.
 */
public class UserGeneratorFunction implements GeneratorFunction<Long, User> {
    
    // JavaFaker instance for generating fake data
    private static final Faker faker = new Faker(Locale.ENGLISH);
    
    // Date formatter for ISO format timestamps
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    @Override
    public User map(Long value) throws Exception {
        // Generate user ID based on the sequence value
        int userId = value.intValue();
        
        // Generate current timestamp in ISO format
        String createdAt = LocalDateTime.now().format(ISO_FORMATTER);
        
        // Generate fake personal information
        String firstName = faker.name().firstName();
        String lastName = faker.name().lastName();
        String email = faker.internet().emailAddress();
        String phoneNumber = faker.phoneNumber().phoneNumber();
        
        // Generate fake address information
        String address = faker.address().streetAddress();
        String city = faker.address().city();
        String country = faker.address().country();
        
        // Generate fake professional information
        String jobTitle = faker.job().title();
        String company = faker.company().name();
        
        // Generate fake date of birth (between 18 and 80 years ago)
        String dateOfBirth = faker.date()
                .past(365 * 62, TimeUnit.DAYS) // Up to 62 years ago
                .toInstant()
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .format(DATE_FORMATTER);
        
        // Ensure the person is at least 18 years old
        if (LocalDateTime.now().minusYears(18).toLocalDate()
                .isBefore(java.time.LocalDate.parse(dateOfBirth, DATE_FORMATTER))) {
            dateOfBirth = LocalDateTime.now().minusYears(18 + faker.number().numberBetween(0, 44))
                    .toLocalDate().format(DATE_FORMATTER);
        }
        
        return new User(userId, firstName, lastName, email, phoneNumber, address, 
                       city, country, jobTitle, company, dateOfBirth, createdAt);
    }
}
