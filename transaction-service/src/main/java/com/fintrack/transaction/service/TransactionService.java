package com.fintrack.transaction.service;

import com.fintrack.transaction.dto.request.TransactionRequest;
import com.fintrack.transaction.dto.response.TransactionResponse;
import com.fintrack.transaction.entity.Transaction;
import com.fintrack.transaction.event.TransactionCreatedEvent;
import com.fintrack.transaction.repository.TransactionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private static final String TOPIC_TRANSACTION_CREATED = "transaction.created";

    private final TransactionRepository transactionRepository;
    private final KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        Transaction transaction = Transaction.builder()
                .userId(userId)
                .amount(request.amount())
                .type(request.type())
                .category(request.category())
                .description(request.description())
                .transactionDate(request.transactionDate())
                .tags(request.tags())
                .recurring(request.recurring())
                .recurrenceType(request.recurrenceType())
                .build();

        transactionRepository.save(transaction);

        kafkaTemplate.send(TOPIC_TRANSACTION_CREATED, userId.toString(),
                TransactionCreatedEvent.from(transaction));

        log.info("Transação criada: {} para usuário: {}", transaction.getId(), userId);
        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAll(UUID userId, Pageable pageable) {
        return transactionRepository
                .findByUserIdOrderByTransactionDateDesc(userId, pageable)
                .map(TransactionResponse::from);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(UUID userId, UUID transactionId) {
        return transactionRepository.findById(transactionId)
                .filter(t -> t.getUserId().equals(userId))
                .map(TransactionResponse::from)
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada: " + transactionId));
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID transactionId, TransactionRequest request) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada: " + transactionId));

        transaction.setAmount(request.amount());
        transaction.setType(request.type());
        transaction.setCategory(request.category());
        transaction.setDescription(request.description());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setTags(request.tags());
        transaction.setRecurring(request.recurring());
        transaction.setRecurrenceType(request.recurrenceType());

        return TransactionResponse.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Transação não encontrada: " + transactionId));

        transactionRepository.delete(transaction);
        log.info("Transação deletada: {}", transactionId);
    }
}
