package com.spendwise.transaction.service;

import com.spendwise.transaction.dto.request.CreateTransactionRequest;
import com.spendwise.transaction.dto.request.UpdateTransactionRequest;
import com.spendwise.transaction.dto.response.TransactionResponse;
import com.spendwise.transaction.dto.response.TransactionSummaryResponse;
import com.spendwise.transaction.entity.Account;
import com.spendwise.transaction.entity.Transaction;
import com.spendwise.transaction.enums.TransactionType;
import com.spendwise.transaction.exception.AccountNotFoundException;
import com.spendwise.transaction.exception.RecurrenceTypeRequiredException;
import com.spendwise.transaction.exception.TransactionNotFoundException;
import com.spendwise.transaction.kafka.TransactionEventPublisher;
import com.spendwise.transaction.repository.AccountRepository;
import com.spendwise.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final TransactionEventPublisher eventPublisher;

    @Transactional
    public TransactionResponse create(CreateTransactionRequest request, UUID userId) {
        validateRecurrence(request.recurring(), request.recurrenceType());

        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .account(account)
                .amount(request.amount())
                .type(request.type())
                .operationType(request.operationType())
                .category(request.category())
                .description(request.description())
                .transactionDate(request.transactionDate())
                .tags(request.tags())
                .recurring(request.recurring())
                .recurrenceType(request.recurrenceType())
                .build();

        Transaction saved = transactionRepository.save(transaction);
        eventPublisher.publishTransactionCreated(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> list(UUID userId, TransactionType type, Pageable pageable) {
        Page<Transaction> page = (type != null)
                ? transactionRepository.findAllByUserIdAndType(userId, type, pageable)
                : transactionRepository.findAllByUserId(userId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(UUID id, UUID userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional
    public TransactionResponse update(UUID id, UpdateTransactionRequest request, UUID userId) {
        validateRecurrence(request.recurring(), request.recurrenceType());

        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException(id));

        Account account = accountRepository.findByIdAndUserId(request.accountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException(request.accountId()));

        transaction.setAccount(account);
        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setOperationType(request.operationType());
        transaction.setCategory(request.category());
        transaction.setDescription(request.description());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setTags(request.tags());
        transaction.setRecurring(request.recurring());
        transaction.setRecurrenceType(request.recurrenceType());

        return toResponse(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Transaction transaction = transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new TransactionNotFoundException(id));
        transactionRepository.delete(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionSummaryResponse summary(UUID userId) {
        BigDecimal totalIncome = transactionRepository.sumAmountByUserIdAndType(userId, TransactionType.INCOME);
        BigDecimal totalExpense = transactionRepository.sumAmountByUserIdAndType(userId, TransactionType.EXPENSE);
        BigDecimal balance = totalIncome.subtract(totalExpense);
        return new TransactionSummaryResponse(totalIncome, totalExpense, balance);
    }

    private void validateRecurrence(Boolean recurring, Object recurrenceType) {
        if (Boolean.TRUE.equals(recurring) && recurrenceType == null) {
            throw new RecurrenceTypeRequiredException();
        }
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(),
                t.getAccount().getId(),
                t.getAccount().getName(),
                t.getAmount(),
                t.getType(),
                t.getOperationType(),
                t.getCategory(),
                t.getDescription(),
                t.getTransactionDate(),
                t.getTags(),
                t.isRecurring(),
                t.getRecurrenceType(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
