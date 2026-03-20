package com.vbs.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoanDto {
    private int userId;
    private String type;
    private double amount;
    private double rate;
    private int tenure;
    private String purpose;
}
