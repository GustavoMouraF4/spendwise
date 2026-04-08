# FinTrack — Sistema Financeiro Pessoal

Sistema de finanças pessoais com microserviços, Kafka e IA para auxílio na tomada de decisões financeiras.

## Arquitetura

```
fintrack/
├── auth-service/          # Autenticação e JWT (porta 8081)
├── transaction-service/   # Lançamentos financeiros (porta 8082)
├── budget-service/        # Orçamentos e metas (porta 8083)
├── chat-service/          # Chat com IA - Claude API (porta 8084)
├── notification-service/  # Alertas por email/push (porta 8085)
├── report-service/        # Relatórios assíncronos (porta 8086)
├── docker/
│   └── init-databases.sql
├── docker-compose.yml
└── pom.xml
```

### Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 + Spring Boot 3.2 |
| Mensageria | Apache Kafka (Amazon MSK) |
| Banco de dados | PostgreSQL (Amazon RDS) |
| Cache / Sessões | Redis (ElastiCache) |
| IA / Chat | Claude API via Spring AI |
| Deploy | AWS ECS Fargate + ECR |
| CI/CD | GitHub Actions |
| IaC | Terraform |

## Rodando localmente

### Pré-requisitos

- Java 21
- Maven 3.9+
- Docker e Docker Compose

### 1. Subir infraestrutura local

```bash
docker-compose up -d
```

Isso sobe PostgreSQL, Redis, Kafka e Kafka UI (http://localhost:9090).

### 2. Buildar todos os serviços

```bash
mvn clean install -DskipTests
```

### 3. Rodar um serviço específico

```bash
cd auth-service
mvn spring-boot:run
```

### Portas locais

| Serviço | Porta | Swagger |
|---|---|---|
| auth-service | 8081 | http://localhost:8081/swagger-ui.html |
| transaction-service | 8082 | http://localhost:8082/swagger-ui.html |
| budget-service | 8083 | http://localhost:8083/swagger-ui.html |
| chat-service | 8084 | http://localhost:8084/swagger-ui.html |
| notification-service | 8085 | — |
| report-service | 8086 | http://localhost:8086/swagger-ui.html |
| Kafka UI | 9090 | http://localhost:9090 |

## Tópicos Kafka

| Tópico | Produtor | Consumidores |
|---|---|---|
| `transaction.created` | transaction-service | budget-service, report-service |
| `budget.alert` | budget-service | notification-service |
| `chat.message` | chat-service | report-service |
| `report.requested` | report-service | notification-service |

## Variáveis de ambiente

Cada serviço possui um `application.yml` com valores padrão para desenvolvimento local.  
Para produção, configure via AWS Secrets Manager ou variáveis de ambiente no ECS.

| Variável | Descrição |
|---|---|
| `DB_URL` | URL do PostgreSQL |
| `DB_USER` / `DB_PASSWORD` | Credenciais do banco |
| `REDIS_HOST` / `REDIS_PORT` | Endereço do Redis |
| `KAFKA_BROKERS` | Bootstrap servers do Kafka |
| `JWT_SECRET` | Chave secreta para assinar JWT (base64) |
| `ANTHROPIC_API_KEY` | Chave da API do Claude (chat-service) |
| `AWS_REGION` | Região da AWS |

## GitHub Projects

Este repositório usa **GitHub Projects** para gestão do desenvolvimento.  
Cada issue deve ser vinculada ao projeto e categorizada por serviço usando as labels disponíveis.

## Contribuindo

1. Crie uma branch a partir de `develop`: `git checkout -b feature/nome-da-feature`
2. Faça commits descritivos seguindo [Conventional Commits](https://www.conventionalcommits.org/)
3. Abra um Pull Request para `develop`
4. O CI roda automaticamente apenas nos serviços alterados
