package com.vbs.demo.repositories;

import com.vbs.demo.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepo extends JpaRepository<Transaction,Integer> {

    List<Transaction> findAllByUserId(int id);
}
