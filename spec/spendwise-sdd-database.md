# Especificação: Padrões de Nomenclatura e Organização de Banco de Dados Relacional

> **Tipo de documento:** Spec-Driven Development (SDD)
> **Status:** Draft v1.1
> **Última atualização:** 2026-04-16
> **Stack:** Java (JPA/Hibernate) + Flyway
> **Escopo:** Bancos de dados relacionais (PostgreSQL, MySQL, SQL Server, Oracle)
> **Audiência:** Engenheiros de software, DBAs, arquitetos de dados

---

## 1. Objetivo

Definir um conjunto de regras determinísticas para nomenclatura e organização de objetos em bancos de dados relacionais, de modo que qualquer desenvolvedor (ou agente automatizado) possa:

- Criar novos objetos seguindo um padrão previsível.
- Identificar a função de um objeto apenas pelo seu nome.
- Reduzir ambiguidade em code reviews e migrations.
- Facilitar geração automática de código (ORMs, scaffolding, LLMs).
- Manter alinhamento direto entre entidades JPA e o schema físico.

---

## 2. Princípios Norteadores

1. **Consistência antes de preferência pessoal** — escolha um padrão e mantenha em todo o projeto.
2. **Legibilidade antes de brevidade** — `customer_address` é melhor que `cust_addr`.
3. **Portabilidade entre SGBDs** — evite recursos e caracteres que só funcionem em um fornecedor.
4. **Previsibilidade** — dado o nome de uma entidade, seja possível deduzir o nome de tabelas, colunas, FKs e índices relacionados.
5. **Alinhamento com o domínio Java** — cada tabela mapeia 1:1 com uma entidade `@Entity`.

---

## 3. Regras Gerais

### 3.1 Idioma

- **DEVE** usar um único idioma em todo o banco.
- **RECOMENDADO:** inglês (evita acentuação e caracteres especiais, e combina com nomes de classes Java).
- **NÃO DEVE** misturar idiomas (`customer_nome` é proibido).

### 3.2 Case e separadores

- **DEVE** usar `snake_case` (minúsculas com underscore) no banco.
- **DEVE** usar `PascalCase` para classes Java e `camelCase` para atributos.
- **NÃO DEVE** usar espaços, hífens ou acentos em identificadores do banco.
- **NÃO DEVE** usar palavras reservadas do SQL (`user`, `order`, `group`, `table`, `select`).
  - Se inevitável por domínio, **DEVE** ser renomeado semanticamente (`app_user`, `customer_order`).

### 3.3 Tamanho de identificadores

- **DEVE** respeitar o limite de 63 caracteres (PostgreSQL) ou 30 (Oracle tradicional).
- **RECOMENDADO:** manter abaixo de 30 caracteres para portabilidade.

---

## 4. Tabelas

### 4.1 Nome

- **DEVE** usar substantivo no **singular**: `customer`, `order`, `product`, `invoice`.
- **DEVE** espelhar o nome da entidade JPA em `snake_case`.
  - Exemplo: classe `Customer` → tabela `customer`.
  - Exemplo: classe `OrderItem` → tabela `order_item`.
- **DEVE** ser descritivo e não usar abreviações obscuras.
- **NÃO DEVE** usar prefixos do tipo `tb_` ou `tbl_`.
- **DEVE** declarar explicitamente o nome via `@Table(name = "customer")` para não depender da estratégia de naming do Hibernate.

### 4.2 Tabelas associativas (N:N)

- **DEVE** combinar os dois nomes envolvidos, em ordem alfabética, no singular separados por underscore: `course_student`, `role_user`.
- **PODE** adicionar sufixos quando houver múltiplas relações entre as mesmas entidades (`user_role_assignment`).

### 4.3 Tabelas de domínio / lookup

- **DEVE** usar sufixo `_type`, `_status` ou `_category` (no singular): `order_status`, `payment_type`.

---

## 5. Colunas

### 5.1 Regras gerais

- **DEVE** estar em `snake_case` e no **singular**.
- **DEVE** ser descritiva: `birth_date`, não `bd`.
- **DEVE** mapear via `@Column(name = "...")` sempre que o nome Java divergir do nome do banco.

### 5.2 Chave primária

- **DEVE** ser `id` do tipo `BIGINT` (`Long` em Java) ou `UUID`.
- **DEVE** usar `@Id` + `@GeneratedValue(strategy = GenerationType.IDENTITY)` para autoincremento, ou sequence nomeada (ver §7).
- **NÃO DEVE** usar chaves primárias compostas, exceto em tabelas associativas (onde se usa `@EmbeddedId` ou `@IdClass`).

### 5.3 Chaves estrangeiras

- **DEVE** seguir o padrão `<tabela_referenciada>_id` (a tabela já está no singular).
  - Exemplo: em `order`, a FK para `customer` é `customer_id`.
- **DEVE** preservar o tipo exato da coluna referenciada.
- **DEVE** ser mapeada em Java com `@ManyToOne` + `@JoinColumn(name = "customer_id")`.

### 5.4 Booleanos

- **DEVE** usar prefixo `is_`, `has_`, `can_` ou `should_`.
  - Exemplos: `is_active`, `has_discount`, `can_login`.
- **NÃO DEVE** usar valores ambíguos (`status` para representar booleano).
- Em Java, o atributo **DEVE** ser `boolean`/`Boolean` com o mesmo prefixo (`isActive`).

### 5.5 Datas e timestamps

| Semântica | Padrão banco | Tipo Java |
|---|---|---|
| Criação do registro | `created_at` | `Instant` / `OffsetDateTime` |
| Última modificação | `updated_at` | `Instant` / `OffsetDateTime` |
| Remoção lógica | `deleted_at` | `Instant` / `OffsetDateTime` |
| Datas de domínio | `<contexto>_date` (`birth_date`) | `LocalDate` |
| Timestamps de domínio | `<contexto>_at` (`published_at`) | `Instant` / `OffsetDateTime` |

- **RECOMENDADO:** usar `@CreationTimestamp` / `@UpdateTimestamp` (Hibernate) ou `@CreatedDate` / `@LastModifiedDate` (Spring Data Auditing) para popular automaticamente.

### 5.6 Valores monetários

- **DEVE** usar `DECIMAL(19,4)` no banco e `BigDecimal` em Java.
- **NUNCA** usar `float`/`double` para dinheiro.
- **DEVE** sufixar com `_amount` ou `_price`: `total_amount`, `unit_price`.
- **DEVE** armazenar moeda em coluna separada quando multi-moeda: `amount`, `currency_code`.

---

## 6. Constraints e Índices

### 6.1 Convenção de prefixos

| Tipo | Prefixo | Exemplo |
|---|---|---|
| Primary Key | `pk_` | `pk_customer` |
| Foreign Key | `fk_` | `fk_order_customer` |
| Unique | `uq_` | `uq_user_email` |
| Check | `ck_` | `ck_product_price_positive` |
| Index (btree comum) | `idx_` | `idx_order_created_at` |
| Index único | `uidx_` | `uidx_user_email` |
| Index parcial | `pidx_` | `pidx_order_active` |

### 6.2 Composição dos nomes

- **Foreign Key:** `fk_<tabela_origem>_<tabela_destino>` (ex.: `fk_order_customer`).
- **Index:** `idx_<tabela>_<coluna(s)>` (ex.: `idx_order_customer_id_created_at`).
- **Unique:** `uq_<tabela>_<coluna(s)>` (ex.: `uq_user_email`).

### 6.3 Declaração em JPA

- **DEVE** declarar índices e uniques via `@Table(indexes = ..., uniqueConstraints = ...)` para documentação, **mas** a fonte da verdade é o script Flyway.
- **NÃO DEVE** confiar em `hibernate.hbm2ddl.auto=update` em produção.

---

## 7. Views, Procedures, Functions, Triggers e Sequences

| Objeto | Prefixo | Exemplo |
|---|---|---|
| View | `vw_` | `vw_monthly_sales` |
| Materialized View | `mvw_` | `mvw_revenue_by_region` |
| Stored Procedure | `sp_` ou `usp_` | `sp_process_order` |
| Function | `fn_` | `fn_calculate_tax` |
| Trigger | `tg_` ou `trg_` | `tg_order_before_update` |
| Sequence | `seq_` | `seq_invoice_number` |

- Quando usar `GenerationType.SEQUENCE` em JPA, nomear a sequence como `seq_<tabela>`:
  ```java
  @SequenceGenerator(name = "seq_customer", sequenceName = "seq_customer", allocationSize = 50)
  ```

---

## 8. Schemas e Organização Lógica

### 8.1 Separação por domínio (Bounded Contexts)

- **DEVE** usar schemas para separar contextos funcionais em bancos com mais de ~20 tabelas:
  - `auth` — autenticação, usuários, sessões.
  - `sales` — pedidos, pagamentos, faturas.
  - `inventory` — produtos, estoque.
  - `reporting` — views e agregações analíticas.
- Em Java, **DEVE** refletir o schema via `@Table(schema = "sales", name = "order")`.

### 8.2 Separação OLTP x OLAP

- Tabelas analíticas (data warehouse) **DEVEM** estar em schema próprio e seguir convenções dimensionais:
  - `dim_<entidade>` para dimensões (`dim_customer`, `dim_product`).
  - `fact_<processo>` para tabelas fato (`fact_sales`, `fact_page_view`).

---

## 9. Normalização

- **DEVE** seguir no mínimo a **3ª Forma Normal (3NF)** em modelos transacionais.
- **PODE** denormalizar deliberadamente em contextos de performance ou BI, desde que documentado.
- Relacionamentos N:N **DEVEM** usar tabelas associativas (nunca colunas do tipo lista ou CSV).

---

## 10. Tipos de Dados e Mapeamento Java

| Semântica | Tipo SQL | Tipo Java |
|---|---|---|
| Identificador numérico | `BIGINT` | `Long` |
| Identificador único global | `UUID` | `java.util.UUID` |
| Texto curto (<255) | `VARCHAR(n)` dimensionado pelo domínio | `String` |
| Texto longo | `TEXT` | `String` + `@Lob` ou `columnDefinition = "TEXT"` |
| Valor monetário | `DECIMAL(19,4)` | `BigDecimal` |
| Data | `DATE` | `LocalDate` |
| Data e hora | `TIMESTAMP WITH TIME ZONE` | `Instant` ou `OffsetDateTime` |
| Booleano | `BOOLEAN` | `boolean` / `Boolean` |
| Enumerações | `VARCHAR(30)` com CHECK | `enum` + `@Enumerated(EnumType.STRING)` |
| JSON estruturado | `JSONB` (PostgreSQL) | `String` + converter, ou tipo customizado (Hypersistence Utils) |

- **NÃO DEVE** usar `VARCHAR(255)` como padrão universal.
- **DEVE** declarar `NOT NULL` sempre que o domínio permitir (e `nullable = false` na anotação JPA).
- **NUNCA** usar `@Enumerated(EnumType.ORDINAL)` — sempre `STRING`.

---

## 11. Versionamento com Flyway

### 11.1 Localização

- **DEVE** ficar em `src/main/resources/db/migration/`.
- **PODE** organizar subpastas por módulo quando o projeto crescer (`db/migration/auth/`, `db/migration/sales/`), desde que configurado em `spring.flyway.locations`.

### 11.2 Nomenclatura de arquivos

- **Versionadas:** `V<versão>__<descricao_em_snake_case>.sql`
  - Exemplo: `V2026_04_16_001__create_customer_table.sql`
  - Alternativa semver: `V1_0_0__initial_schema.sql`
- **Repetíveis** (views, functions, procedures): `R__<descricao>.sql`
  - Exemplo: `R__vw_monthly_sales.sql`
- **Undo** (Flyway Teams): `U<versão>__<descricao>.sql`
- **DEVE** usar exatamente dois underscores (`__`) entre o prefixo e a descrição.
- **NÃO DEVE** alterar um script já aplicado em ambientes compartilhados — criar novo script de migração.

### 11.3 Convenções de versão

- **RECOMENDADO:** timestamp `V<YYYY_MM_DD_NNN>` quando houver múltiplos desenvolvedores, para reduzir conflitos.
- **DEVE** ser monotonicamente crescente.

### 11.4 Idempotência com `IF NOT EXISTS`

Migrations **DEVEM** ser escritas de forma idempotente sempre que o SGBD suportar, para que uma re-execução acidental (e.g., falha de rede, rollback parcial, ambientes de CI sem estado) não quebre o banco.

**Regras obrigatórias:**

| DDL | Forma idempotente obrigatória |
|-----|-------------------------------|
| `CREATE TABLE` | `CREATE TABLE IF NOT EXISTS <tabela> (...)` |
| `CREATE INDEX` | `CREATE INDEX IF NOT EXISTS <nome> ON <tabela> (...)` |
| `CREATE SEQUENCE` | `CREATE SEQUENCE IF NOT EXISTS <nome>` |
| `CREATE SCHEMA` | `CREATE SCHEMA IF NOT EXISTS <nome>` |
| `CREATE TYPE` (enum) | `DO $$ BEGIN CREATE TYPE ... EXCEPTION WHEN duplicate_object THEN NULL; END $$;` |
| `ALTER TABLE ADD COLUMN` | `ALTER TABLE <t> ADD COLUMN IF NOT EXISTS <col> <tipo>` |
| `ALTER TABLE ADD CONSTRAINT` | Verificar em `information_schema` antes de adicionar (ver exemplo abaixo) |
| `DROP TABLE` | `DROP TABLE IF EXISTS <tabela>` |
| `DROP INDEX` | `DROP INDEX IF EXISTS <nome>` |
| `DROP COLUMN` | `ALTER TABLE <t> DROP COLUMN IF EXISTS <col>` |

**Exemplo — adicionar constraint de forma idempotente:**
```sql
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'ck_users_role'
          AND table_name = 'users'
    ) THEN
        ALTER TABLE users ADD CONSTRAINT ck_users_role CHECK (role IN ('USER', 'ADMIN'));
    END IF;
END $$;
```

**Por que isso importa:**
- Flyway versiona scripts pelo checksum — **não os re-executa** em condições normais. Porém, em ambientes de teste que recriam o banco, scripts de setup iniciais podem ser reexecutados.
- Em migrações de recuperação ou `baseline`, a idempotência evita erros fatais que abortam toda a inicialização da aplicação.
- Facilita testes de integração com `@Sql` ou `Testcontainers` que podem rodar a migration em banco limpo mais de uma vez por suite.

**NÃO DEVE** usar `CREATE OR REPLACE` para tabelas (não suportado em PostgreSQL) — use sempre `IF NOT EXISTS`.

---

### 11.5 Conteúdo do script

- **DEVE** conter apenas uma mudança lógica por migration (uma tabela, um conjunto coeso de alterações).
- **DEVE** incluir comentário no topo com autor, data e ticket/issue de referência:
  ```sql
  -- Author: gustavo
  -- Date:   2026-04-16
  -- Issue:  JIRA-1234
  -- Description: Cria tabela de clientes.
  ```

### 11.6 Configuração recomendada (application.yml)

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
    validate-on-migrate: true
    out-of-order: false
  jpa:
    hibernate:
      ddl-auto: validate   # nunca 'update' em produção
    properties:
      hibernate:
        jdbc.time_zone: UTC
```

---

## 12. Documentação

- **DEVE** adicionar comentários via `COMMENT ON TABLE` e `COMMENT ON COLUMN` para entidades não triviais dentro do próprio script Flyway.
- **DEVE** manter Javadoc nas entidades JPA explicando invariantes e regras de negócio.

---

## 13. Exemplo Canônico

### 13.1 Script Flyway

**Arquivo:** `src/main/resources/db/migration/V2026_04_16_001__create_customer_and_order.sql`

```sql
-- Author: gustavo
-- Date:   2026-04-16
-- Issue:  JIRA-1234
-- Description: Cria schema sales com tabelas customer e "order".

CREATE SCHEMA IF NOT EXISTS sales;

CREATE TABLE sales.customer (
    id            BIGSERIAL     PRIMARY KEY,
    email         VARCHAR(255)  NOT NULL,
    full_name     VARCHAR(150)  NOT NULL,
    birth_date    DATE,
    is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uq_customer_email UNIQUE (email),
    CONSTRAINT ck_customer_email_format CHECK (email LIKE '%@%')
);

COMMENT ON TABLE  sales.customer            IS 'Clientes cadastrados na plataforma.';
COMMENT ON COLUMN sales.customer.is_active  IS 'Falso após soft delete ou bloqueio.';

CREATE TABLE sales.customer_order (
    id            BIGSERIAL     PRIMARY KEY,
    customer_id   BIGINT        NOT NULL,
    total_amount  DECIMAL(19,4) NOT NULL,
    currency_code CHAR(3)       NOT NULL DEFAULT 'BRL',
    placed_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_customer_order_customer
        FOREIGN KEY (customer_id) REFERENCES sales.customer(id),
    CONSTRAINT ck_customer_order_total_amount_positive CHECK (total_amount >= 0)
);

CREATE INDEX idx_customer_order_customer_id_placed_at
    ON sales.customer_order (customer_id, placed_at DESC);
```

### 13.2 Entidade JPA correspondente

```java
@Entity
@Table(
    schema = "sales",
    name = "customer",
    uniqueConstraints = @UniqueConstraint(name = "uq_customer_email", columnNames = "email")
)
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // getters / setters / equals / hashCode
}

@Entity
@Table(
    schema = "sales",
    name = "customer_order",
    indexes = @Index(
        name = "idx_customer_order_customer_id_placed_at",
        columnList = "customer_id, placed_at"
    )
)
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "customer_id",
        foreignKey = @ForeignKey(name = "fk_customer_order_customer")
    )
    private Customer customer;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "BRL";

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    // getters / setters / equals / hashCode
}
```

---

## 14. Checklist de Conformidade

Use esta lista antes de aprovar qualquer PR que altere o schema:

- [ ] Todos os nomes estão em `snake_case` e no idioma padrão.
- [ ] Tabelas e colunas no **singular**.
- [ ] Nome da tabela = nome da classe JPA convertido para `snake_case`.
- [ ] Toda tabela possui chave primária `id`.
- [ ] Toda FK segue o padrão `<tabela>_id` e tem constraint nomeada `fk_...`.
- [ ] Colunas booleanas usam prefixo `is_`/`has_`/`can_`.
- [ ] Colunas de auditoria (`created_at`, `updated_at`) estão presentes quando aplicável.
- [ ] Índices e constraints têm prefixos corretos.
- [ ] Tipos de dados são adequados ao domínio (nada de `VARCHAR(255)` universal).
- [ ] Valores monetários usam `DECIMAL(19,4)` + `BigDecimal`.
- [ ] Enums usam `@Enumerated(EnumType.STRING)`.
- [ ] Script Flyway segue o padrão `V<versão>__<descricao>.sql` com cabeçalho de metadados.
- [ ] Todos os DDLs usam `IF NOT EXISTS` / `IF EXISTS` (idempotência — §11.4).
- [ ] Constraints adicionadas via `ALTER TABLE` verificam `information_schema` antes de inserir.
- [ ] Nenhum script Flyway previamente aplicado foi alterado.
- [ ] `ddl-auto` está configurado como `validate` (ou `none`) em produção.
- [ ] Comentários `COMMENT ON` foram adicionados em objetos não triviais.

---

## 15. Referências

- Flyway Documentation — Migrations and Naming.
- Hibernate ORM User Guide.
- PostgreSQL Documentation — Identifiers and Key Words.
- *SQL Antipatterns*, Bill Karwin.
- *Database Design for Mere Mortals*, Michael J. Hernandez.
- *The Data Warehouse Toolkit*, Ralph Kimball (para modelos dimensionais).

---

## 16. Histórico de Revisões

| Versão | Data | Autor | Descrição |
|---|---|---|---|
| 1.0 | 2026-04-16 | Gustavo | Versão inicial do spec. |
| 1.1 | 2026-04-16 | Gustavo | Ajuste para convenção singular; adição da stack Java/JPA/Flyway e exemplos de mapeamento. |
