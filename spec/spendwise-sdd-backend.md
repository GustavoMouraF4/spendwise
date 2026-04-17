# SpendWise — Especificação de Comportamento — Backend

> Versão 1.0 | Abril 2026
> Repositório: `spendwise`
> Stack: Java 21 · Spring Boot 3.2 · PostgreSQL · Kafka · Redis · AWS

---

## Como ler este documento

```
GIVEN   → estado inicial / pré-condições
WHEN    → ação ou evento que dispara o comportamento
THEN    → resultado esperado, verificável e mensurável
```

Regras de negócio são numeradas (`RN-XXX`) e referenciadas nos cenários.
Critérios de aceitação (`CA-XXX`) são os testes de aceite de cada funcionalidade.

---

## Sumário

1. [AUTH — Autenticação e autorização](#1-auth--autenticação-e-autorização)
2. [TRANSACTION — Lançamentos financeiros](#2-transaction--lançamentos-financeiros)
3. [BUDGET — Orçamentos e metas](#3-budget--orçamentos-e-metas)
4. [CHAT — Assistente financeiro com IA](#4-chat--assistente-financeiro-com-ia)
5. [NOTIFICATION — Alertas e notificações](#5-notification--alertas-e-notificações)
6. [REPORT — Relatórios financeiros](#6-report--relatórios-financeiros)
7. [EVENTS — Contratos de eventos Kafka](#7-events--contratos-de-eventos-kafka)
8. [CROSS-CUTTING — Regras transversais](#8-cross-cutting--regras-transversais)

---

## 1. AUTH — Autenticação e autorização

### Contexto

O `auth-service` (porta `8081`) é o único serviço que emite tokens JWT. Todos os demais validam o token localmente via Spring OAuth2 Resource Server, sem consultar o `auth-service` a cada requisição.

**Banco:** `spendwise_auth` (PostgreSQL)
**Cache:** Redis — blacklist de tokens invalidados
**Dependências exclusivas:** JJWT 0.12.x, Spring Data Redis

---

### 1.1 Registro de usuário

**Regras de negócio:**

- `RN-AUTH-01` O e-mail deve ser único no sistema. Dois usuários não podem compartilhar o mesmo endereço.
- `RN-AUTH-02` A senha deve ter no mínimo 8 caracteres.
- `RN-AUTH-03` A senha é armazenada como hash BCrypt — nunca em texto puro.
- `RN-AUTH-04` Todo usuário criado recebe a role `USER` por padrão.
- `RN-AUTH-05` O registro bem-sucedido retorna tokens imediatamente, sem exigir login adicional.

**Contrato:**

```
POST /api/v1/auth/register
Content-Type: application/json

Request:
{
  "name":     "string (2–100 chars, obrigatório)",
  "email":    "string (e-mail válido, obrigatório)",
  "password": "string (mínimo 8 chars, obrigatório)"
}

Response 201 Created:
{
  "accessToken":  "string (JWT)",
  "refreshToken": "string (JWT)",
  "tokenType":    "Bearer",
  "expiresIn":    3600000,
  "user": {
    "id":    "uuid",
    "name":  "string",
    "email": "string",
    "role":  "USER"
  }
}

Response 409 Conflict:   e-mail já cadastrado       (viola RN-AUTH-01)
Response 400 Bad Request: campos inválidos ou ausentes
```

**Cenários:**

```gherkin
Scenario: Registro bem-sucedido
  GIVEN que nenhum usuário com "joao@email.com" existe
  WHEN  POST /api/v1/auth/register com name, email e password válidos
  THEN  retorna 201 com accessToken, refreshToken e dados do usuário
  AND   a senha salva no banco é um hash BCrypt
  AND   o usuário recebe role = "USER"

Scenario: E-mail duplicado
  GIVEN que já existe usuário com "joao@email.com"
  WHEN  POST /api/v1/auth/register com o mesmo e-mail
  THEN  retorna 409 com title "Email já cadastrado"
  AND   nenhum novo usuário é persistido

Scenario: Senha muito curta
  GIVEN qualquer e-mail não cadastrado
  WHEN  POST /api/v1/auth/register com password de 7 caracteres
  THEN  retorna 400 com erro de validação no campo "password"
```

**Critérios de aceitação:**

- [ ] CA-AUTH-01: Registro válido retorna 201 com tokens e dados do usuário
- [ ] CA-AUTH-02: E-mail duplicado retorna 409
- [ ] CA-AUTH-03: Senha com menos de 8 chars retorna 400
- [ ] CA-AUTH-04: Senha salva no banco nunca é o texto original
- [ ] CA-AUTH-05: Token retornado é validável pelo `transaction-service`

---

### 1.2 Login

**Regras de negócio:**

- `RN-AUTH-06` Credenciais inválidas sempre retornam a mesma mensagem genérica — sem indicar se o e-mail existe.
- `RN-AUTH-07` Usuários com `active = false` não podem fazer login.
- `RN-AUTH-08` O `access_token` expira em 1 hora (3.600.000 ms).
- `RN-AUTH-09` O `refresh_token` expira em 7 dias (604.800.000 ms).

**Contrato:**

```
POST /api/v1/auth/login
Content-Type: application/json

Request:
{
  "email":    "string (obrigatório)",
  "password": "string (obrigatório)"
}

Response 200 OK:        mesma estrutura de /register
Response 401 Unauthorized: credenciais inválidas ou conta inativa
```

**Cenários:**

```gherkin
Scenario: Login bem-sucedido
  GIVEN usuário ativo com email "joao@email.com" e senha correta
  WHEN  POST /api/v1/auth/login com as credenciais corretas
  THEN  retorna 200 com accessToken (exp 1h) e refreshToken (exp 7d)

Scenario: Senha incorreta
  GIVEN usuário existente com "joao@email.com"
  WHEN  POST /api/v1/auth/login com senha errada
  THEN  retorna 401 com mensagem "Credenciais inválidas"
  AND   a mensagem não indica se o e-mail existe (RN-AUTH-06)

Scenario: Conta inativa
  GIVEN usuário com active = false
  WHEN  POST /api/v1/auth/login com credenciais corretas
  THEN  retorna 401 com mensagem "Conta desativada"
```

**Critérios de aceitação:**

- [ ] CA-AUTH-06: Login válido retorna 200 com tokens corretos
- [ ] CA-AUTH-07: Senha errada retorna 401 sem revelar existência do e-mail
- [ ] CA-AUTH-08: Conta inativa retorna 401

---

### 1.3 Logout

**Regras de negócio:**

- `RN-AUTH-10` O logout invalida o `access_token` imediatamente via blacklist no Redis.
- `RN-AUTH-11` Token na blacklist deve ser rejeitado por todos os microserviços.
- `RN-AUTH-12` A entrada na blacklist expira automaticamente no mesmo TTL do token.

**Contrato:**

```
POST /api/v1/auth/logout
Authorization: Bearer {access_token}

Response 204 No Content
Response 401 Unauthorized: token ausente ou inválido
```

**Cenários:**

```gherkin
Scenario: Logout bem-sucedido
  GIVEN usuário com access_token válido
  WHEN  POST /api/v1/auth/logout com token no header
  THEN  retorna 204
  AND   token é inserido na blacklist do Redis com TTL igual à expiração restante
  AND   qualquer chamada subsequente com esse token retorna 401

Scenario: Token usado após logout
  GIVEN usuário que fez logout com sucesso
  WHEN  GET /api/v1/transactions com o mesmo token
  THEN  retorna 401 com "Token inválido"
```

**Critérios de aceitação:**

- [ ] CA-AUTH-09: Logout retorna 204
- [ ] CA-AUTH-10: Token pós-logout é rejeitado com 401 em qualquer serviço
- [ ] CA-AUTH-11: Entrada na blacklist expira automaticamente

---

### 1.4 Renovação de token

**Regras de negócio:**

- `RN-AUTH-13` Apenas `refresh_token` válido e não expirado gera novo `access_token`.
- `RN-AUTH-14` Cada refresh gera novos `access_token` e `refresh_token` (rotação de tokens).

**Contrato:**

```
POST /api/v1/auth/refresh
X-Refresh-Token: {refresh_token}

Response 200 OK:         novos access_token e refresh_token
Response 401 Unauthorized: refresh_token inválido ou expirado
```

**Critérios de aceitação:**

- [ ] CA-AUTH-12: Refresh com token válido retorna 200 com novos tokens
- [ ] CA-AUTH-13: Refresh com token expirado retorna 401
- [ ] CA-AUTH-14: Token antigo não é reutilizável após rotação

---

## 2. TRANSACTION — Lançamentos financeiros

### Contexto

O `transaction-service` (porta `8082`) registra toda movimentação financeira. Ao criar uma transação, publica `transaction.created` no Kafka para que `budget-service` e `report-service` reajam de forma assíncrona.

**Banco:** `spendwise_transaction` (PostgreSQL)
**Kafka producer:** `transaction.created`
**Auth:** Spring OAuth2 Resource Server — valida JWT localmente

---

### Regras de negócio globais

- `RN-TRX-01` Cada transação pertence a um único usuário e só pode ser acessada por ele.
- `RN-TRX-02` O valor (`amount`) deve ser sempre positivo. O `type` define se é entrada ou saída.
- `RN-TRX-03` A data da transação pode ser retroativa, mas não futura.
- `RN-TRX-04` `recurring = true` exige `recurrenceType` preenchido.
- `RN-TRX-05` A exclusão de uma transação não reverte o gasto já computado no `budget-service`.
- `RN-TRX-06` O `userId` é extraído do JWT — nunca do body ou parâmetros da requisição.

---

### 2.1 Criar transação

**Contrato:**

```
POST /api/v1/transactions
Authorization: Bearer {access_token}

Request:
{
  "amount":          number  (positivo, obrigatório),
  "type":            "INCOME | EXPENSE | TRANSFER" (obrigatório),
  "category":        "string (max 100 chars, obrigatório)",
  "description":     "string (max 255 chars, obrigatório)",
  "transactionDate": "YYYY-MM-DD (obrigatório, não futura)",
  "tags":            "string (max 100 chars, opcional)",
  "recurring":       boolean (obrigatório),
  "recurrenceType":  "DAILY | WEEKLY | MONTHLY | YEARLY (obrigatório se recurring=true)"
}

Response 201 Created:
{
  "id":              "uuid",
  "amount":          number,
  "type":            "string",
  "category":        "string",
  "description":     "string",
  "transactionDate": "YYYY-MM-DD",
  "tags":            "string | null",
  "recurring":       boolean,
  "recurrenceType":  "string | null",
  "createdAt":       "ISO 8601"
}

Response 400: campos inválidos
Response 401: token ausente ou inválido
```

**Cenários:**

```gherkin
Scenario: Criar despesa bem-sucedida
  GIVEN usuário autenticado
  WHEN  POST /api/v1/transactions com type="EXPENSE", amount=150.00, category="Alimentação"
  THEN  retorna 201 com id gerado e dados da transação
  AND   publica evento "transaction.created" no Kafka com userId do token

Scenario: Transação recorrente sem recurrenceType
  GIVEN usuário autenticado
  WHEN  POST com recurring=true e recurrenceType ausente
  THEN  retorna 400 com erro no campo "recurrenceType"

Scenario: Data futura rejeitada
  GIVEN usuário autenticado
  WHEN  POST com transactionDate = amanhã
  THEN  retorna 400 com erro no campo "transactionDate"

Scenario: Isolamento de dados entre usuários
  GIVEN transações dos usuários A e B existem no banco
  WHEN  usuário A faz GET /api/v1/transactions/{id_da_transacao_de_B}
  THEN  retorna 404 (não expõe que o recurso existe)
```

**Critérios de aceitação:**

- [ ] CA-TRX-01: Criação válida retorna 201 e evento Kafka é publicado
- [ ] CA-TRX-02: `recurring=true` sem `recurrenceType` retorna 400
- [ ] CA-TRX-03: Data futura retorna 400
- [ ] CA-TRX-04: Valor negativo ou zero retorna 400
- [ ] CA-TRX-05: Usuário só acessa suas próprias transações
- [ ] CA-TRX-06: `userId` vem do JWT, não do body

---

### 2.2 Listar transações

**Contrato:**

```
GET /api/v1/transactions?page=0&size=20&sort=transactionDate,desc&type=EXPENSE
Authorization: Bearer {access_token}

Response 200 OK:
{
  "content":       [ ...transações ],
  "totalElements": number,
  "totalPages":    number,
  "number":        number,
  "size":          number,
  "first":         boolean,
  "last":          boolean
}
```

**Critérios de aceitação:**

- [ ] CA-TRX-07: Retorna apenas transações do usuário autenticado
- [ ] CA-TRX-08: Paginação e ordenação funcionam corretamente
- [ ] CA-TRX-09: Filtro por `type` retorna apenas o tipo solicitado

---

### 2.3 Atualizar e excluir transação

**Contrato:**

```
PUT    /api/v1/transactions/{id}   → 200 com transação atualizada
DELETE /api/v1/transactions/{id}   → 204 No Content

Response 404: transação não encontrada ou não pertence ao usuário
Response 401: token inválido
```

**Critérios de aceitação:**

- [ ] CA-TRX-10: Atualização válida retorna 200
- [ ] CA-TRX-11: Exclusão retorna 204
- [ ] CA-TRX-12: Operações em transações de outro usuário retornam 404

---

## 3. BUDGET — Orçamentos e metas

### Contexto

O `budget-service` (porta `8083`) gerencia limites de gastos por categoria e mês. Consome `transaction.created` via Kafka para atualizar o valor gasto automaticamente e emite `budget.alert` quando os limites são atingidos.

**Banco:** `spendwise_budget` (PostgreSQL)
**Kafka consumer:** `transaction.created` (group-id: `budget-service`)
**Kafka producer:** `budget.alert`

---

### Regras de negócio globais

- `RN-BDG-01` Só pode existir um orçamento por `(userId, category, year, month)`.
- `RN-BDG-02` O `spentAmount` é atualizado apenas via evento Kafka — nunca via API.
- `RN-BDG-03` Apenas eventos com `type = "EXPENSE"` atualizam o orçamento. `INCOME` e `TRANSFER` são ignorados.
- `RN-BDG-04` `usagePercentage >= 80%` → status `WARNING` → emite `budget.alert`.
- `RN-BDG-05` `usagePercentage >= 100%` → status `EXCEEDED` → emite `budget.alert`.
- `RN-BDG-06` O status volta a `OK` somente quando `spentAmount` cair abaixo de 80% do `limitAmount`.
- `RN-BDG-07` Alerta é emitido apenas na **transição** de status — não a cada evento recebido.

---

### 3.1 Criar orçamento

**Contrato:**

```
POST /api/v1/budgets
Authorization: Bearer {access_token}

Request:
{
  "category":    "string (max 100 chars, obrigatório)",
  "limitAmount": number (positivo, obrigatório),
  "year":        number (2020–2100, obrigatório),
  "month":       number (1–12, obrigatório)
}

Response 201 Created:
{
  "id":               "uuid",
  "category":         "string",
  "limitAmount":      number,
  "spentAmount":      0.00,
  "remainingAmount":  number,
  "usagePercentage":  0.0,
  "year":             number,
  "month":            number,
  "status":           "OK",
  "createdAt":        "ISO 8601"
}

Response 409 Conflict:   orçamento já existe para categoria/mês (RN-BDG-01)
Response 400 Bad Request: campos inválidos
```

**Cenários:**

```gherkin
Scenario: Criar orçamento bem-sucedido
  GIVEN não existe orçamento para "Alimentação" em 04/2026 do usuário
  WHEN  POST /api/v1/budgets com category="Alimentação", limitAmount=500, year=2026, month=4
  THEN  retorna 201 com spentAmount=0, usagePercentage=0.0 e status="OK"

Scenario: Orçamento duplicado
  GIVEN já existe orçamento para "Alimentação" em 04/2026
  WHEN  POST com a mesma categoria/mês/ano
  THEN  retorna 409

Scenario: Atualização automática via evento — sem alerta
  GIVEN orçamento "Alimentação" 04/2026 com limit=500, spent=0
  WHEN  evento "transaction.created" chega com type=EXPENSE, category="Alimentação", amount=300
  THEN  spentAmount=300, usagePercentage=60.0%, status="OK"
  AND   nenhum evento budget.alert é emitido

Scenario: Transição para WARNING
  GIVEN orçamento "Alimentação" com limit=500, spent=350, status="OK"
  WHEN  evento chega com amount=80 (total: 430 = 86%)
  THEN  status muda para "WARNING"
  AND   evento "budget.alert" é publicado no Kafka com status="WARNING"

Scenario: Transição para EXCEEDED
  GIVEN orçamento "Alimentação" com limit=500, spent=480, status="WARNING"
  WHEN  evento chega com amount=50 (total: 530 = 106%)
  THEN  status muda para "EXCEEDED"
  AND   evento "budget.alert" é publicado com status="EXCEEDED"

Scenario: Evento WARNING não reenvia alerta
  GIVEN orçamento já com status="WARNING"
  WHEN  novo evento EXPENSE chega (permanece em WARNING)
  THEN  status permanece "WARNING"
  AND   nenhum novo evento budget.alert é emitido (RN-BDG-07)

Scenario: Evento INCOME é ignorado
  GIVEN qualquer orçamento existente
  WHEN  evento "transaction.created" chega com type="INCOME"
  THEN  o orçamento NÃO é atualizado (RN-BDG-03)
```

**Critérios de aceitação:**

- [ ] CA-BDG-01: Criação válida retorna 201 com spentAmount=0
- [ ] CA-BDG-02: Duplicata retorna 409
- [ ] CA-BDG-03: Evento EXPENSE atualiza spentAmount e usagePercentage
- [ ] CA-BDG-04: Evento INCOME não altera orçamento
- [ ] CA-BDG-05: Status muda para WARNING em >= 80%
- [ ] CA-BDG-06: Status muda para EXCEEDED em >= 100%
- [ ] CA-BDG-07: budget.alert emitido apenas na transição de status
- [ ] CA-BDG-08: Usuário acessa apenas seus próprios orçamentos

---

## 4. CHAT — Assistente financeiro com IA

### Contexto

O `chat-service` (porta `8084`) oferece um assistente conversacional com Claude (Anthropic). Antes de cada chamada, monta um **contexto financeiro** com dados reais do usuário. A resposta pode ser entregue em modo **completo** ou em **streaming** (SSE).

**Banco:** `spendwise_chat` (PostgreSQL — histórico de conversas)
**Cache:** Redis — contexto financeiro do usuário (TTL: 15 min)
**Kafka producer:** `chat.message`
**IA:** Claude API via `spring-ai-anthropic-spring-boot-starter`
**Modelo:** `claude-sonnet-4-20250514`

---

### Regras de negócio globais

- `RN-CHT-01` Toda chamada ao Claude inclui um system prompt com o contexto financeiro atual do usuário.
- `RN-CHT-02` O contexto financeiro contém: saldo do mês atual, top 5 categorias de maior gasto, orçamentos ativos e percentual utilizado.
- `RN-CHT-03` As últimas 10 mensagens do histórico são enviadas como contexto conversacional.
- `RN-CHT-04` Cada interação (mensagem + resposta) é persistida no banco após conclusão.
- `RN-CHT-05` O contexto financeiro é cacheado no Redis por 15 minutos por `userId`.
- `RN-CHT-06` Cada interação gera um evento `chat.message` no Kafka.
- `RN-CHT-07` Indisponibilidade da Claude API retorna 503 — nunca 500.

---

### 4.1 Enviar mensagem (resposta completa)

**Contrato:**

```
POST /api/v1/chat/messages
Authorization: Bearer {access_token}

Request:
{
  "message": "string (1–2000 chars, obrigatório)"
}

Response 200 OK:
{
  "id":        "uuid",
  "content":   "string (resposta do Claude)",
  "role":      "ASSISTANT",
  "createdAt": "ISO 8601"
}

Response 400: mensagem ausente ou inválida
Response 503: Claude API indisponível
```

---

### 4.2 Enviar mensagem (streaming SSE)

**Contrato:**

```
POST /api/v1/chat/stream
Authorization: Bearer {access_token}
Accept: text/event-stream

Request: { "message": "string" }

Response: Server-Sent Events
  data: {"chunk": "Olá! "}
  data: {"chunk": "Analisando seus gastos..."}
  data: [DONE]

Response 401: token inválido
Response 503: Claude API indisponível
```

---

### 4.3 Histórico de mensagens

**Contrato:**

```
GET /api/v1/chat/history?page=0&size=20
Authorization: Bearer {access_token}

Response 200 OK:
{
  "content": [
    { "id": "uuid", "content": "string", "role": "USER | ASSISTANT", "createdAt": "ISO 8601" }
  ],
  ...paginação
}
```

**Cenários:**

```gherkin
Scenario: Pergunta com contexto financeiro
  GIVEN usuário com R$1.200 em despesas em Abril/2026
  AND   orçamento "Alimentação" em 75% de uso
  WHEN  POST /api/v1/chat/messages com "Como estão meus gastos?"
  THEN  o system prompt enviado ao Claude contém o contexto financeiro real
  AND   retorna resposta do Claude referenciando os dados do usuário
  AND   persiste mensagem e resposta no banco
  AND   publica evento "chat.message" no Kafka

Scenario: Contexto servido do cache Redis
  GIVEN contexto financeiro gerado há 5 minutos para o usuário
  WHEN  usuário envia nova mensagem
  THEN  contexto é lido do Redis sem nova consulta ao banco

Scenario: Cache expirado — reconstituição
  GIVEN contexto financeiro gerado há 16 minutos
  WHEN  usuário envia nova mensagem
  THEN  contexto é reconstruído consultando o banco
  AND   cache é atualizado no Redis com TTL de 15 min

Scenario: Claude API indisponível
  GIVEN que a Claude API retorna erro
  WHEN  usuário envia mensagem
  THEN  retorna 503 com mensagem "Serviço de IA temporariamente indisponível"
  AND   a mensagem do usuário NÃO é persistida
```

**Critérios de aceitação:**

- [ ] CA-CHT-01: System prompt inclui saldo, top categorias e orçamentos
- [ ] CA-CHT-02: Últimas 10 mensagens do histórico são enviadas ao Claude
- [ ] CA-CHT-03: Contexto é cacheado no Redis (TTL 15 min)
- [ ] CA-CHT-04: Interação é persistida no banco após conclusão
- [ ] CA-CHT-05: Evento chat.message publicado no Kafka
- [ ] CA-CHT-06: Streaming entrega chunks via SSE progressivamente
- [ ] CA-CHT-07: Indisponibilidade da Claude API retorna 503

---

## 5. NOTIFICATION — Alertas e notificações

### Contexto

O `notification-service` (porta `8085`) é **puramente reativo** — sem API REST própria e sem banco de dados. Consome eventos Kafka e despacha notificações via AWS SES (e-mail) e AWS SNS (push).

**Kafka consumer:** `budget.alert` (group-id: `notification-service`)
**AWS:** SES (e-mail), SNS (push)

---

### Regras de negócio globais

- `RN-NOT-01` Toda notificação deve ser idempotente — o mesmo evento processado duas vezes não gera dois envios.
- `RN-NOT-02` Falhas de envio são retentadas até 3 vezes com backoff exponencial.
- `RN-NOT-03` Após 3 falhas, o evento vai para Dead Letter Queue (DLQ).
- `RN-NOT-04` O remetente é sempre `noreply@spendwise.com`.

---

### 5.1 Alerta de orçamento via e-mail

**Trigger:** evento `budget.alert` no Kafka

**Cenários:**

```gherkin
Scenario: Alerta WARNING por e-mail
  GIVEN evento "budget.alert" com status="WARNING", category="Alimentação", usagePercentage=86%
  WHEN  o serviço processa o evento
  THEN  envia e-mail ao usuário
  AND   assunto contém "Alerta de orçamento — Alimentação (86%)"

Scenario: Alerta EXCEEDED por e-mail
  GIVEN evento com status="EXCEEDED", usagePercentage=106%
  WHEN  o serviço processa o evento
  THEN  envia e-mail com assunto "Orçamento excedido — Alimentação (106%)"

Scenario: Idempotência — segundo evento não reenvia
  GIVEN o mesmo budgetAlertId já processado anteriormente
  WHEN  o evento é recebido novamente (reentrega Kafka)
  THEN  o e-mail NÃO é enviado novamente (RN-NOT-01)

Scenario: Retry em falha do SES
  GIVEN que AWS SES retorna erro temporário
  WHEN  o serviço tenta enviar
  THEN  realiza até 3 tentativas com backoff exponencial
  AND   após 3 falhas, evento vai para DLQ
```

**Critérios de aceitação:**

- [ ] CA-NOT-01: E-mail enviado ao receber evento WARNING
- [ ] CA-NOT-02: E-mail enviado ao receber evento EXCEEDED
- [ ] CA-NOT-03: Mesmo evento não gera dois envios
- [ ] CA-NOT-04: Após 3 falhas, evento vai para DLQ
- [ ] CA-NOT-05: Remetente é sempre `noreply@spendwise.com`

---

## 6. REPORT — Relatórios financeiros

### Contexto

O `report-service` (porta `8086`) gera relatórios de forma **assíncrona** com Spring Batch. O PDF é armazenado no S3 e o usuário acessa via signed URL.

**Banco:** `spendwise_report` (PostgreSQL)
**Kafka consumer:** `transaction.created` (group-id: `report-service`)
**AWS:** S3 — armazenamento dos PDFs gerados

---

### Regras de negócio globais

- `RN-RPT-01` Geração é sempre assíncrona — a requisição retorna 202 imediatamente.
- `RN-RPT-02` O PDF fica disponível no S3 por 7 dias após a geração.
- `RN-RPT-03` A URL de download é uma **signed URL** do S3 com validade de 1 hora por acesso.
- `RN-RPT-04` `type = MONTHLY` exige `year` e `month`. `type = ANNUAL` exige apenas `year`.
- `RN-RPT-05` Usuário não acessa relatórios de outros usuários.

---

### 6.1 Solicitar e acompanhar relatório

**Contrato:**

```
POST /api/v1/reports
Authorization: Bearer {access_token}

Request:
{
  "type":  "MONTHLY | ANNUAL | CATEGORY",
  "year":  number (obrigatório),
  "month": number (1–12, obrigatório se type=MONTHLY)
}

Response 202 Accepted:
{
  "id":          "uuid",
  "type":        "string",
  "status":      "PENDING",
  "year":        number,
  "month":       number | null,
  "createdAt":   "ISO 8601"
}

Response 400: type=MONTHLY sem month

---

GET /api/v1/reports/{id}/download
Authorization: Bearer {access_token}

Response 200 OK (status=DONE):    { "downloadUrl": "https://s3.signed-url..." }
Response 409 Conflict (PENDING):  { "detail": "Relatório ainda não está pronto" }
Response 404: relatório não encontrado ou de outro usuário
```

**Cenários:**

```gherkin
Scenario: Solicitação de relatório mensal
  GIVEN usuário autenticado
  WHEN  POST /api/v1/reports com type="MONTHLY", year=2026, month=4
  THEN  retorna 202 com status="PENDING" imediatamente
  AND   processamento ocorre em background via Spring Batch

Scenario: Status do relatório pronto
  GIVEN relatório com status="DONE" e PDF no S3
  WHEN  GET /api/v1/reports/{id}/download
  THEN  retorna 200 com signed URL válida por 1 hora

Scenario: Tentativa de download de relatório pendente
  GIVEN relatório com status="PENDING" ou "PROCESSING"
  WHEN  GET /api/v1/reports/{id}/download
  THEN  retorna 409 com "Relatório ainda não está pronto"

Scenario: type=MONTHLY sem month
  GIVEN usuário autenticado
  WHEN  POST /api/v1/reports com type="MONTHLY" sem month
  THEN  retorna 400 com erro no campo "month"
```

**Critérios de aceitação:**

- [ ] CA-RPT-01: Solicitação retorna 202 imediatamente (não bloqueia)
- [ ] CA-RPT-02: MONTHLY sem month retorna 400
- [ ] CA-RPT-03: Download de relatório DONE retorna signed URL
- [ ] CA-RPT-04: Download de relatório PENDING retorna 409
- [ ] CA-RPT-05: Signed URL expira em 1 hora
- [ ] CA-RPT-06: Relatórios de outro usuário retornam 404

---

## 7. EVENTS — Contratos de eventos Kafka

### 7.1 `transaction.created`

**Producer:** `transaction-service`
**Consumers:** `budget-service`, `report-service`
**Key:** `userId` (garante ordering por usuário na mesma partition)

```json
{
  "transactionId":   "uuid",
  "userId":          "uuid",
  "amount":          150.00,
  "type":            "EXPENSE",
  "category":        "Alimentação",
  "description":     "Supermercado Extra",
  "transactionDate": "2026-04-08",
  "occurredAt":      "2026-04-08T14:30:00"
}
```

---

### 7.2 `budget.alert`

**Producer:** `budget-service`
**Consumers:** `notification-service`
**Gatilho:** transição de status para `WARNING` ou `EXCEEDED`

```json
{
  "budgetId":        "uuid",
  "userId":          "uuid",
  "category":        "Alimentação",
  "limitAmount":     500.00,
  "spentAmount":     430.00,
  "usagePercentage": 86.0,
  "status":          "WARNING",
  "year":            2026,
  "month":           4,
  "occurredAt":      "2026-04-08T15:00:00"
}
```

---

### 7.3 `chat.message`

**Producer:** `chat-service`
**Consumers:** `report-service`

```json
{
  "messageId":         "uuid",
  "userId":            "uuid",
  "userMessage":       "Como estão meus gastos?",
  "assistantResponse": "Seus gastos em Abril...",
  "occurredAt":        "2026-04-08T16:00:00"
}
```

---

### 7.4 Garantias dos producers

```yaml
acks: all
retries: 3
enable.idempotence: true
key-serializer:   StringSerializer
value-serializer: JsonSerializer
```

### 7.5 Garantias dos consumers

```yaml
auto-offset-reset: earliest
group-id: <nome-do-servico>
key-deserializer:   StringDeserializer
value-deserializer: JsonDeserializer
spring.json.trusted.packages: "com.spendwise.*"
```

---

## 8. CROSS-CUTTING — Regras transversais

### 8.1 Segurança

- `RN-SEC-01` Todos os endpoints (exceto `/auth/register`, `/auth/login`, `/actuator/health`, `/swagger-ui/**`) exigem `Authorization: Bearer {token}`.
- `RN-SEC-02` O JWT é validado localmente em cada serviço via JWKS. Nenhum serviço consulta o `auth-service` a cada requisição.
- `RN-SEC-03` Tokens na blacklist do Redis são rejeitados mesmo que ainda não tenham expirado.
- `RN-SEC-04` Variáveis sensíveis (`JWT_SECRET`, `ANTHROPIC_API_KEY`, senhas de banco) nunca são commitadas. Em produção, vêm do AWS Secrets Manager.
- `RN-SEC-05` Toda query ao banco filtra por `userId` extraído do JWT — nunca por parâmetro de URL ou body.

### 8.2 Tratamento de erros

- `RN-ERR-01` Todos os erros seguem o formato `ProblemDetail` (RFC 7807).
- `RN-ERR-02` Mensagens de erro nunca expõem detalhes internos (stack trace, nomes de tabela).
- `RN-ERR-03` Erros de validação retornam 400 com mapa de campos e mensagens.

**Formato padrão:**

```json
{
  "type":     "about:blank",
  "title":    "Erro de validação",
  "status":   400,
  "detail":   "Dados inválidos",
  "instance": "/api/v1/transactions",
  "errors": {
    "amount":   "deve ser positivo",
    "category": "não pode estar em branco"
  }
}
```

### 8.3 Observabilidade

- `RN-OBS-01` Todos os serviços expõem `/actuator/health` sem autenticação.
- `RN-OBS-02` Logs estruturados em JSON com campos: `timestamp`, `level`, `service`, `userId`, `traceId`, `message`.
- `RN-OBS-03` O `userId` aparece nos logs de toda operação autenticada.

### 8.4 Kafka

- `RN-KFK-01` Producers usam `acks=all` e `enable.idempotence=true`.
- `RN-KFK-02` Consumers usam `auto-offset-reset=earliest`.
- `RN-KFK-03` Cada consumer group tem `group-id` igual ao nome do serviço.
- `RN-KFK-04` Eventos com falha no processamento são retentados e, após esgotar tentativas, vão para DLQ.

### 8.5 Java 21 — Virtual Threads

- `RN-JVM-01` Todos os serviços habilitam Virtual Threads via `spring.threads.virtual.enabled=true`.
- `RN-JVM-02` O modelo de programação permanece imperativo/bloqueante — sem WebFlux.

---

*SpendWise Backend SDD v1.0 — Abril 2026*
