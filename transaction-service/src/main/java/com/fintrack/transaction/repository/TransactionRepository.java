package com.fintrack.transaction.repository;

import com.fintrack.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByUserIdOrderByTransactionDateDesc(UUID userId, Pageable pageable);

    Page<Transaction> findByUserIdAndTypeOrderByTransactionDateDesc(
            UUID userId, Transaction.TransactionType type, Pageable pageable);

    Page<Transaction> findByUserIdAndCategoryOrderByTransactionDateDesc(
            UUID userId, String category, Pageable pageable);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
        WHERE t.userId = :userId
        AND t.type = :type
        AND t.transactionDate BETWEEN :start AND :end
    """)
    BigDecimal sumByUserIdAndTypeAndDateBetween(
            UUID userId, Transaction.TransactionType type,
            LocalDate start, LocalDate end);
}
