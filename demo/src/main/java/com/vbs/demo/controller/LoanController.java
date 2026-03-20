package com.vbs.demo.controller;

import com.vbs.demo.dto.LoanDto;
import com.vbs.demo.dto.TransactionDto;
import com.vbs.demo.models.History;
import com.vbs.demo.models.Loan;
import com.vbs.demo.models.Transaction;
import com.vbs.demo.models.User;
import com.vbs.demo.repositories.HistoryRepo;
import com.vbs.demo.repositories.LoanRepo;
import com.vbs.demo.repositories.TransactionRepo;
import com.vbs.demo.repositories.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/loan")
public class LoanController {

    @Autowired LoanRepo loanRepo;
    @Autowired UserRepo userRepo;
    @Autowired TransactionRepo transactionRepo;
    @Autowired HistoryRepo historyRepo;

    @PostMapping("/apply")
    public String apply(@RequestBody LoanDto dto) {
        User user = userRepo.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User Not Found"));

        List<Transaction> userTx = transactionRepo.findAllByUserId(dto.getUserId());
        int txCount = userTx.size();
        boolean isActiveUser = txCount >= 3;
        boolean has10Percent = user.getBalance() >= dto.getAmount() * 0.10;

        if (!isActiveUser && !has10Percent) {
            double required = dto.getAmount() * 0.10;
            return "INSUFFICIENT_BALANCE:" + required;
        }

        List<Loan> existing = loanRepo.findAllByUserId(dto.getUserId());
        boolean hasActiveOrPending = existing.stream()
                .anyMatch(l -> l.getStatus().equals("active") || l.getStatus().equals("pending"));
        if (hasActiveOrPending) return "EXISTING_LOAN";

        double r = (dto.getRate() / 100.0) / 12.0;
        double emi = (r == 0) ? dto.getAmount() / dto.getTenure()
                : dto.getAmount() * r * Math.pow(1+r, dto.getTenure()) / (Math.pow(1+r, dto.getTenure())-1);
        double totalPayable = Math.round(emi * dto.getTenure() * 100.0) / 100.0;
        emi = Math.round(emi * 100.0) / 100.0;

        Loan loan = new Loan();
        loan.setUserId(dto.getUserId());
        loan.setType(dto.getType());
        loan.setAmount(dto.getAmount());
        loan.setRate(dto.getRate());
        loan.setTenure(dto.getTenure());
        loan.setPurpose(dto.getPurpose());
        loan.setEmi(emi);
        loan.setTotalPayable(totalPayable);
        loan.setRemaining(totalPayable);
        loan.setStatus("pending");
        loanRepo.save(loan);

        History h = new History();
        h.setDescription("Loan applied by " + user.getUsername()
                + " Rs" + dto.getAmount() + " (" + dto.getType() + ")"
                + (isActiveUser ? " [Active: " + txCount + " tx]" : " [10% rule]"));
        historyRepo.save(h);

        return "APPLIED:" + loan.getId();
    }

    @GetMapping("/user/{userId}")
    public List<Loan> getUserLoans(@PathVariable int userId) {
        return loanRepo.findAllByUserId(userId);
    }

    @GetMapping("/pending")
    public List<Loan> getPendingLoans() {
        return loanRepo.findAllByStatus("pending");
    }

    @GetMapping("/all")
    public List<Loan> getAllLoans() {
        return loanRepo.findAll();
    }

    @PostMapping("/approve/{loanId}/admin/{adminId}")
    public String approve(@PathVariable int loanId, @PathVariable int adminId) {
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new RuntimeException("Loan Not Found"));
        if (!loan.getStatus().equals("pending")) return "Loan is not in pending state";

        User user = userRepo.findById(loan.getUserId()).orElseThrow(() -> new RuntimeException("User Not Found"));
        double newBalance = user.getBalance() + loan.getAmount();
        user.setBalance(newBalance);
        userRepo.save(user);

        Transaction t = new Transaction();
        t.setAmount(loan.getAmount());
        t.setCurrBalance(newBalance);
        t.setDescription("Loan Disbursed: Rs" + loan.getAmount() + " (" + loan.getType() + " Loan #" + loan.getId() + ")");
        t.setUserId(loan.getUserId());
        transactionRepo.save(t);

        loan.setStatus("active");
        loan.setActionDate(LocalDateTime.now());
        loanRepo.save(loan);

        History h = new History();
        h.setDescription("Loan #" + loanId + " approved by Admin " + adminId + " for " + user.getUsername() + " Rs" + loan.getAmount());
        historyRepo.save(h);
        return "Loan Approved Successfully";
    }

    @PostMapping("/reject/{loanId}/admin/{adminId}")
    public String reject(@PathVariable int loanId, @PathVariable int adminId) {
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new RuntimeException("Loan Not Found"));
        if (!loan.getStatus().equals("pending")) return "Loan is not in pending state";

        User user = userRepo.findById(loan.getUserId()).orElse(null);
        loan.setStatus("rejected");
        loan.setActionDate(LocalDateTime.now());
        loanRepo.save(loan);

        History h = new History();
        h.setDescription("Loan #" + loanId + " rejected by Admin " + adminId
                + (user != null ? " for user " + user.getUsername() : ""));
        historyRepo.save(h);
        return "Loan Rejected";
    }

    @PostMapping("/repay/{loanId}")
    public String repay(@PathVariable int loanId, @RequestBody TransactionDto dto) {
        Loan loan = loanRepo.findById(loanId).orElseThrow(() -> new RuntimeException("Loan Not Found"));
        if (!loan.getStatus().equals("active")) return "Loan is not active";

        User user = userRepo.findById(dto.getId()).orElseThrow(() -> new RuntimeException("User Not Found"));
        if (user.getBalance() < dto.getAmount()) return "Insufficient Balance";
        if (dto.getAmount() > loan.getRemaining()) return "Amount exceeds remaining balance of Rs" + loan.getRemaining();

        double newBalance = user.getBalance() - dto.getAmount();
        user.setBalance(newBalance);
        userRepo.save(user);

        Transaction t = new Transaction();
        t.setAmount(dto.getAmount());
        t.setCurrBalance(newBalance);
        t.setDescription("Loan Repayment: Rs" + dto.getAmount() + " for " + loan.getType() + " Loan #" + loanId);
        t.setUserId(dto.getId());
        transactionRepo.save(t);

        double newRemaining = Math.round((loan.getRemaining() - dto.getAmount()) * 100.0) / 100.0;
        loan.setRemaining(newRemaining);

        if (newRemaining <= 0) {
            loan.setRemaining(0);
            loan.setStatus("closed");
            loanRepo.save(loan);
            History h = new History();
            h.setDescription("Loan #" + loanId + " fully repaid by " + user.getUsername());
            historyRepo.save(h);
            return "CLOSED:Loan fully repaid! Congratulations!";
        }
        loanRepo.save(loan);
        return "REPAID:" + newRemaining;
    }
}
