# SpendWise — Sistema Financeiro Pessoal

Sistema de finanças pessoais com microserviços, Kafka, IA (Claude) e AWS.

## Estrutura do repositório

```
spendwise/
├── pom.xml                     ← Parent BOM (versões apenas, sem deps de runtime)
├── .github/
│   ├── workflows/
│   │   ├── ci.yml              ← CI inteligente (builda só o serviço alterado)
│   │   └── service-build.yml   ← Workflow reutilizável por serviço
│   └── ISSUE_TEMPLATE/
│       ├── feature.yml
│       └── bug.yml
│
├── auth-service/               ← Porta 8081
├── transaction-service/        ← Porta 8082
├── budget-service/             ← Porta 8083
├── chat-service/               ← Porta 8084
├── notification-service/       ← Porta 8085
└── report-service/             ← Porta 8086
```

Cada serviço contém:
```
<service>/
├── pom.xml                     ← Herda parent BOM, declara suas deps
├── Dockerfile
├── docker-compose.yml          ← Infra isolada para dev local
├── .env.example
└── src/
    ├── main/
    │   ├── java/com/spendwise/<pkg>/
    │   │   ├── controller/
    │   │   ├── service/
    │   │   ├── repository/
    │   │   ├── entity/
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   └── response/
    │   │   ├── config/
    │   │   └── exception/
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    └── test/
```

## Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.2 |
| Segurança | Spring Security + JWT (JJWT) |
| Mensageria | Apache Kafka (Amazon MSK) |
| Banco | PostgreSQL (Amazon RDS) |
| Cache | Redis (ElastiCache) |
| IA | Claude API via Spring AI |
| Deploy | AWS ECS Fargate + ECR |
| CI/CD | GitHub Actions |

## Serviços e responsabilidades

| Serviço | Porta | Responsabilidade | Kafka (producer) | Kafka (consumer) |
|---|---|---|---|---|
| auth-service | 8081 | Autenticação, JWT, refresh token | — | — |
| transaction-service | 8082 | Lançamentos financeiros | `transaction.created` | — |
| budget-service | 8083 | Orçamentos e metas | `budget.alert` | `transaction.created` |
| chat-service | 8084 | Chat com IA (Claude) | `chat.message` | — |
| notification-service | 8085 | Alertas email/push (SES, SNS) | — | `budget.alert` |
| report-service | 8086 | Relatórios assíncronos (S3) | — | `transaction.created`, `chat.message` |

## Rodando localmente

### Pré-requisitos
- Java 21, Maven 3.9+, Docker

### 1. Subir infra do serviço desejado
```bash
cd auth-service
docker-compose up -d
```

### 2. Configurar variáveis
```bash
cp .env.example .env
# edite o .env se necessário
```

### 3. Rodar o serviço
```bash
mvn spring-boot:run
```

### Build de todos os módulos
```bash
# a partir da raiz
mvn clean install -DskipTests
```

## GitHub Projects

Crie issues usando os templates disponíveis e vincule ao GitHub Project.
O CI detecta qual serviço foi alterado no PR e builda apenas ele.

## Convenção de commits

Seguir [Conventional Commits](https://www.conventionalcommits.org/):
- `feat(auth-service): adicionar refresh token`
- `fix(budget-service): corrigir cálculo de percentual`
- `chore: atualizar versão do Spring Boot`
