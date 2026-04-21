package com.spendwise.transaction.kafka;

import com.spendwise.transaction.entity.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEventPublisher {

    private static final String TOPIC = "transaction.created";

    private final KafkaTemplate<String, TransactionCreatedEvent> kafkaTemplate;

    public void publishTransactionCreated(Transaction transaction) {
        TransactionCreatedEvent event = new TransactionCreatedEvent(
                transaction.getId(),
                transaction.getUserId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getCategory(),
                transaction.getDescription(),
                transaction.getTransactionDate(),
                LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC, transaction.getUserId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar transaction.created para transação {}: {}", transaction.getId(), ex.getMessage());
                    } else {
                        log.debug("Evento transaction.created publicado: transactionId={}", transaction.getId());
                    }
                });
    }
}
