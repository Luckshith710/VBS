package com.vbs.demo.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private int userId;

    @Column(nullable = false)
    private String type; // Personal, Home, Education, Vehicle

    @Column(nullable = false)
    private double amount;

    @Column(nullable = false)
    private double rate; // Annual interest rate

    @Column(nullable = false)
    private int tenure; // In months

    @Column(nullable = false)
    private double emi;

    @Column(nullable = false)
    private double totalPayable;

    @Column(nullable = false)
    private double remaining;

    @Column(nullable = false)
    private String purpose;

    @Column(nullable = false)
    private String status; // pending, active, rejected, closed

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime appliedDate;

    @Column
    private LocalDateTime actionDate; // When admin approved/rejected
}
