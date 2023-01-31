package com.manko.telega.models;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Entity(name = "user")
@Data
public class User {
    @Id

    private Long chatId;


    private String firstName;


    private String lastName;


    private String userName;

    private LocalDateTime registeredAt;
}
