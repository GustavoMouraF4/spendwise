package com.spendwise.transaction.service;

import com.spendwise.transaction.dto.request.CreateAccountRequest;
import com.spendwise.transaction.dto.response.AccountResponse;
import com.spendwise.transaction.entity.Account;
import com.spendwise.transaction.exception.AccountNotFoundException;
import com.spendwise.transaction.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional
    public AccountResponse create(CreateAccountRequest request, UUID userId) {
        Account account = Account.builder()
                .userId(userId)
                .name(request.name())
                .availableOperationTypes(request.availableOperationTypes())
                .build();

        return toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listByUser(UUID userId) {
        return accountRepository.findAllByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Account account = accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new AccountNotFoundException(id));
        accountRepository.delete(account);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getAvailableOperationTypes(),
                account.getCreatedAt()
        );
    }
}
