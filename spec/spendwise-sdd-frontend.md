# SpendWise — Especificação de Comportamento — Frontend

> Versão 1.0 | Abril 2026
> Repositório: `spendwise-web`
> Stack: Next.js 15 · React 19 · TypeScript · Tailwind CSS · NextAuth v5 · TanStack Query · Zustand

---

## Como ler este documento

```
GIVEN   → estado inicial / pré-condições
WHEN    → ação do usuário ou evento que dispara o comportamento
THEN    → resultado esperado, verificável e mensurável
```

Regras de negócio são numeradas (`RN-FE-XXX`) e referenciadas nos cenários.
Critérios de aceitação (`CA-FE-XXX`) são os testes de aceite de cada funcionalidade.

---

## Sumário

1. [Arquitetura do frontend](#1-arquitetura-do-frontend)
2. [AUTH — Autenticação e sessão](#2-auth--autenticação-e-sessão)
3. [DASHBOARD — Visão geral financeira](#3-dashboard--visão-geral-financeira)
4. [TRANSACTIONS — Lançamentos](#4-transactions--lançamentos)
5. [BUDGETS — Orçamentos](#5-budgets--orçamentos)
6. [CHAT — Assistente com IA](#6-chat--assistente-com-ia)
7. [REPORTS — Relatórios](#7-reports--relatórios)
8. [LAYOUT — Navegação e estrutura](#8-layout--navegação-e-estrutura)
9. [CROSS-CUTTING — Regras transversais](#9-cross-cutting--regras-transversais)

---

## 1. Arquitetura do frontend

### 1.1 Estrutura de rotas

| Rota | Grupo | Autenticação | Descrição |
|---|---|---|---|
| `/login` | `(auth)` | Pública | Tela de login |
| `/register` | `(auth)` | Pública | Cadastro de novo usuário |
| `/` | `(dashboard)` | Privada | Dashboard com visão geral |
| `/transactions` | `(dashboard)` | Privada | Lista e gestão de transações |
| `/budgets` | `(dashboard)` | Privada | Orçamentos mensais |
| `/chat` | `(dashboard)` | Privada | Chat com assistente IA |
| `/reports` | `(dashboard)` | Privada | Relatórios e exportações |
| `/settings` | `(dashboard)` | Privada | Perfil e configurações |

### 1.2 Proxy reverso — regra fundamental

- `RN-FE-01` O browser **nunca** chama os microserviços diretamente.
- `RN-FE-02` Todas as chamadas passam pelo proxy interno em `app/api/[...path]/route.ts`, que injeta o JWT e roteia para o serviço correto.

| Prefixo no frontend | Microserviço de destino |
|---|---|
| `/api/auth/*` | `auth-service:8081` |
| `/api/transactions/*` | `transaction-service:8082` |
| `/api/budgets/*` | `budget-service:8083` |
| `/api/chat/*` | `chat-service:8084` |
| `/api/reports/*` | `report-service:8086` |

### 1.3 Camadas da aplicação

| Camada | Tecnologia | Responsabilidade |
|---|---|---|
| Roteamento / SSR | Next.js 15 App Router | Páginas, layouts, rotas de API |
| Autenticação | NextAuth v5 | Sessão, JWT, proteção de rotas |
| Server state | TanStack Query | Cache, loading, refetch automático |
| Client state | Zustand | UI (sidebar, tema) — persiste no localStorage |
| Formulários | React Hook Form + Zod | Validação espelhando DTOs do backend |
| HTTP | Axios + interceptors | Injeção de JWT, tratamento de 401 |
| Componentes base | shadcn/ui (Radix UI) | Acessível, customizável |
| Gráficos | Recharts | Visualizações financeiras |
| Estilo | Tailwind CSS 4 | Utilitários, tema com CSS variables |
| Testes | Vitest + Testing Library | Unitários e de componentes |

---

## 2. AUTH — Autenticação e sessão

### Contexto

O NextAuth v5 gerencia a sessão com provider `Credentials`. O token JWT emitido pelo `auth-service` é armazenado na sessão do NextAuth e injetado automaticamente em todas as chamadas via Axios interceptor.

---

### Regras de negócio

- `RN-FE-02` Rotas do grupo `(dashboard)` exigem sessão ativa. Sem sessão → redireciona para `/login?callbackUrl=<rota>`.
- `RN-FE-03` Usuário autenticado que acessa `/login` ou `/register` é redirecionado para `/`.
- `RN-FE-04` Ao receber 401 da API (token expirado), o Axios interceptor chama `signOut()` e redireciona para `/login`.
- `RN-FE-05` O JWT é armazenado na sessão do NextAuth — **nunca** no `localStorage` ou `sessionStorage`.
- `RN-FE-06` O middleware `middleware.ts` executa a verificação de autenticação antes de renderizar qualquer rota privada.

---

### 2.1 Tela de login (`/login`)

**Campos do formulário:**

| Campo | Tipo | Validação |
|---|---|---|
| E-mail | `input[type=email]` | obrigatório, formato válido |
| Senha | `input[type=password]` | obrigatório, mínimo 8 chars |

**Cenários:**

```gherkin
Scenario: Login bem-sucedido
  GIVEN usuário acessa /login sem sessão ativa
  WHEN  preenche e-mail e senha válidos e clica em "Entrar"
  THEN  o sistema chama POST /api/auth/login via NextAuth
  AND   redireciona para / (ou callbackUrl se presente na URL)
  AND   sidebar e header exibem o nome do usuário

Scenario: Credenciais inválidas
  GIVEN usuário preenche senha errada
  WHEN  clica em "Entrar"
  THEN  exibe mensagem de erro "E-mail ou senha incorretos" no formulário
  AND   o formulário permanece na tela (não redireciona)
  AND   o campo senha é limpo

Scenario: Validação local antes de enviar
  GIVEN campo de e-mail com valor "nao-e-email"
  WHEN  clica em "Entrar" (sem submeter ao servidor)
  THEN  exibe erro de validação inline "E-mail inválido" abaixo do campo
  AND   nenhuma chamada de rede é feita

Scenario: Loading durante autenticação
  GIVEN usuário clicou em "Entrar" com dados válidos
  WHEN  a requisição está em andamento
  THEN  o botão exibe estado de loading ("Entrando...")
  AND   o botão fica desabilitado durante o processamento

Scenario: Usuário já autenticado acessa /login
  GIVEN sessão ativa existente
  WHEN  acessa /login diretamente
  THEN  é redirecionado para / imediatamente (RN-FE-03)
```

**Critérios de aceitação:**

- [ ] CA-FE-01: Login válido redireciona para / ou callbackUrl
- [ ] CA-FE-02: Credenciais inválidas exibem erro inline, sem redirecionar
- [ ] CA-FE-03: Validação local dispara antes de chamar o servidor
- [ ] CA-FE-04: Botão desabilitado durante loading
- [ ] CA-FE-05: Usuário autenticado é redirecionado de /login para /

---

### 2.2 Tela de cadastro (`/register`)

**Campos do formulário:**

| Campo | Tipo | Validação |
|---|---|---|
| Nome | `input[type=text]` | obrigatório, 2–100 chars |
| E-mail | `input[type=email]` | obrigatório, formato válido |
| Senha | `input[type=password]` | obrigatório, mínimo 8 chars |
| Confirmar senha | `input[type=password]` | deve ser igual à senha |

**Cenários:**

```gherkin
Scenario: Cadastro bem-sucedido
  GIVEN usuário preenche todos os campos válidos
  WHEN  clica em "Criar conta"
  THEN  chama POST /api/auth/register
  AND   faz login automático com os tokens retornados
  AND   redireciona para /

Scenario: Senhas não conferem
  GIVEN campos senha="abc12345" e confirmar="abc12346"
  WHEN  clica em "Criar conta"
  THEN  exibe erro "As senhas não conferem" no campo confirmar
  AND   nenhuma chamada de rede é feita

Scenario: E-mail já cadastrado
  GIVEN POST /api/auth/register retorna 409
  WHEN  formulário é submetido
  THEN  exibe mensagem "Este e-mail já está cadastrado"
  AND   foca no campo de e-mail
```

**Critérios de aceitação:**

- [ ] CA-FE-06: Cadastro válido autentica e redireciona para /
- [ ] CA-FE-07: Senhas divergentes exibem erro sem chamada de rede
- [ ] CA-FE-08: E-mail duplicado exibe mensagem de erro inline

---

### 2.3 Logout

**Cenários:**

```gherkin
Scenario: Logout pelo header
  GIVEN usuário autenticado
  WHEN  clica no botão "Sair" no header
  THEN  chama POST /api/auth/logout no backend
  AND   chama signOut() do NextAuth
  AND   redireciona para /login
  AND   sessão é destruída (JWT não reutilizável)

Scenario: Token expirado durante uso
  GIVEN usuário autenticado com sessão ativa
  AND   access_token expirou
  WHEN  qualquer chamada à API retorna 401
  THEN  Axios interceptor captura o erro
  AND   chama signOut() automaticamente
  AND   redireciona para /login
```

**Critérios de aceitação:**

- [ ] CA-FE-09: Logout destrói sessão e redireciona para /login
- [ ] CA-FE-10: 401 da API dispara logout automático

---

## 3. DASHBOARD — Visão geral financeira

### Contexto

O dashboard é a tela inicial após login. Exibe um resumo do mês atual: saldo, receitas, despesas e estado dos orçamentos.

---

### Regras de negócio

- `RN-FE-07` Os dados do dashboard refletem sempre o **mês atual** por padrão.
- `RN-FE-08` Os dados são carregados via TanStack Query com `staleTime` de 60 segundos.
- `RN-FE-09` Valores monetários são exibidos sempre em Real Brasileiro (R$) formatado com `Intl.NumberFormat`.

---

### 3.1 Cards de resumo

**Dados exibidos:**

| Card | Fonte | Cálculo |
|---|---|---|
| Saldo do mês | `transaction-service` | soma(INCOME) - soma(EXPENSE) |
| Total de receitas | `transaction-service` | soma(INCOME) do mês |
| Total de despesas | `transaction-service` | soma(EXPENSE) do mês |
| Orçamentos em alerta | `budget-service` | count(status = WARNING ou EXCEEDED) |

**Cenários:**

```gherkin
Scenario: Dashboard carregado com sucesso
  GIVEN usuário autenticado acessa /
  WHEN  a página é renderizada
  THEN  exibe os 4 cards de resumo com dados do mês atual
  AND   exibe skeleton/loading enquanto os dados chegam
  AND   cada valor monetário é formatado como "R$ 1.250,00"

Scenario: Sem transações no mês
  GIVEN usuário sem transações no mês atual
  WHEN  acessa o dashboard
  THEN  cards exibem "R$ 0,00" com estado vazio
  AND   exibe call-to-action "Registre sua primeira transação"
```

**Critérios de aceitação:**

- [ ] CA-FE-11: Cards exibem dados do mês atual
- [ ] CA-FE-12: Skeleton visível durante carregamento
- [ ] CA-FE-13: Valores formatados como moeda brasileira
- [ ] CA-FE-14: Estado vazio exibido sem transações

---

## 4. TRANSACTIONS — Lançamentos

### 4.1 Listagem

**Regras de negócio:**

- `RN-FE-10` A lista é paginada com 20 itens por página, ordenada por data decrescente.
- `RN-FE-11` Filtros disponíveis: tipo (`INCOME`, `EXPENSE`, `TRANSFER`) e categoria.
- `RN-FE-12` Transações do tipo `EXPENSE` são exibidas em vermelho, `INCOME` em verde.

**Cenários:**

```gherkin
Scenario: Listar transações
  GIVEN usuário acessa /transactions
  WHEN  a página carrega
  THEN  exibe lista paginada ordenada por data decrescente
  AND   despesas em vermelho, receitas em verde

Scenario: Filtrar por tipo
  GIVEN lista carregada
  WHEN  usuário seleciona filtro "Despesas"
  THEN  lista atualiza exibindo apenas transações do tipo EXPENSE
  AND   URL atualiza com parâmetro ?type=EXPENSE (shallow routing)

Scenario: Navegar para próxima página
  GIVEN lista com mais de 20 itens
  WHEN  usuário clica em "Próxima"
  THEN  carrega próxima página sem recarregar a tela
  AND   scroll retorna ao topo da lista
```

**Critérios de aceitação:**

- [ ] CA-FE-15: Lista paginada com 20 itens por página
- [ ] CA-FE-16: Despesas em vermelho, receitas em verde
- [ ] CA-FE-17: Filtro por tipo funciona sem recarregar a página
- [ ] CA-FE-18: Paginação funciona e reseta o scroll

---

### 4.2 Criar / editar transação

**Campos do formulário:**

| Campo | Tipo | Validação |
|---|---|---|
| Valor | `input[type=number]` | obrigatório, positivo |
| Tipo | `select` | INCOME / EXPENSE / TRANSFER |
| Categoria | `input[type=text]` | obrigatório, max 100 |
| Descrição | `input[type=text]` | obrigatório, max 255 |
| Data | `input[type=date]` | obrigatório, não futura |
| Tags | `input[type=text]` | opcional |
| Recorrente | `checkbox` | — |
| Frequência | `select` | visível apenas se recorrente=true |

**Cenários:**

```gherkin
Scenario: Criar transação com sucesso
  GIVEN usuário abre modal/formulário de nova transação
  WHEN  preenche todos os campos obrigatórios e submete
  THEN  chama POST /api/transactions
  AND   fecha o modal
  AND   lista de transações é atualizada automaticamente (TanStack Query invalidation)
  AND   exibe toast de sucesso "Transação criada com sucesso"

Scenario: Campo frequência condicional
  GIVEN formulário de transação aberto
  WHEN  usuário marca o checkbox "Recorrente"
  THEN  campo "Frequência" aparece no formulário
  WHEN  usuário desmarca o checkbox
  THEN  campo "Frequência" desaparece e seu valor é limpo

Scenario: Data futura bloqueada
  GIVEN campo data do formulário
  WHEN  usuário seleciona data futura
  THEN  formulário exibe erro "Data não pode ser futura"
  AND   submissão é bloqueada

Scenario: Editar transação existente
  GIVEN usuário clica em "Editar" em uma transação
  WHEN  modal abre com dados pré-preenchidos
  AND   usuário altera o valor e salva
  THEN  chama PUT /api/transactions/{id}
  AND   lista atualiza com os novos dados
  AND   toast de sucesso exibido
```

**Critérios de aceitação:**

- [ ] CA-FE-19: Criação válida fecha modal e atualiza lista
- [ ] CA-FE-20: Toast de sucesso exibido após criação/edição
- [ ] CA-FE-21: Campo frequência visível apenas quando recorrente=true
- [ ] CA-FE-22: Data futura bloqueia submissão
- [ ] CA-FE-23: Edição pré-preenche o formulário com dados atuais

---

### 4.3 Excluir transação

**Cenários:**

```gherkin
Scenario: Excluir com confirmação
  GIVEN usuário clica em "Excluir" em uma transação
  WHEN  dialog de confirmação aparece
  AND   usuário confirma
  THEN  chama DELETE /api/transactions/{id}
  AND   transação é removida da lista sem recarregar a página
  AND   toast "Transação excluída" exibido

Scenario: Cancelar exclusão
  GIVEN dialog de confirmação aberto
  WHEN  usuário clica em "Cancelar"
  THEN  dialog fecha sem excluir nada
```

**Critérios de aceitação:**

- [ ] CA-FE-24: Exclusão exige confirmação antes de executar
- [ ] CA-FE-25: Cancelamento não altera os dados
- [ ] CA-FE-26: Lista atualiza após exclusão sem recarregar

---

## 5. BUDGETS — Orçamentos

### 5.1 Listagem e status visual

**Regras de negócio:**

- `RN-FE-13` O percentual de uso é exibido com barra de progresso colorida por status.
- `RN-FE-14` `OK` → verde, `WARNING` → amarelo, `EXCEEDED` → vermelho.
- `RN-FE-15` Valor restante negativo (EXCEEDED) é exibido em vermelho com prefixo "-".
- `RN-FE-16` Por padrão, a tela exibe os orçamentos do **mês atual**. O usuário pode navegar entre meses.

**Cenários:**

```gherkin
Scenario: Exibir orçamentos do mês atual
  GIVEN usuário acessa /budgets
  WHEN  a página carrega
  THEN  exibe orçamentos do mês atual com barra de progresso
  AND   orçamentos OK em verde, WARNING em amarelo, EXCEEDED em vermelho

Scenario: Orçamento excedido
  GIVEN orçamento "Alimentação" com spentAmount=530, limitAmount=500
  WHEN  usuário visualiza o card do orçamento
  THEN  barra de progresso está em vermelho e cheia (100%)
  AND   valor restante exibe "-R$ 30,00" em vermelho
  AND   badge "Excedido" é exibido

Scenario: Navegar entre meses
  GIVEN usuário está no mês atual
  WHEN  clica no botão "Mês anterior"
  THEN  a lista atualiza com os orçamentos do mês anterior
  AND   a navegação exibe "Março 2026"
```

**Critérios de aceitação:**

- [ ] CA-FE-27: Barra de progresso verde para OK (< 80%)
- [ ] CA-FE-28: Barra amarela para WARNING (80%–99%)
- [ ] CA-FE-29: Barra vermelha para EXCEEDED (>= 100%)
- [ ] CA-FE-30: Valor restante negativo exibido em vermelho
- [ ] CA-FE-31: Navegação entre meses funciona sem recarregar

---

### 5.2 Criar / editar orçamento

**Campos do formulário:**

| Campo | Tipo | Validação |
|---|---|---|
| Categoria | `input[type=text]` | obrigatório, max 100 |
| Limite (R$) | `input[type=number]` | obrigatório, positivo |
| Mês | `select` (1–12) | obrigatório |
| Ano | `input[type=number]` | obrigatório |

**Cenários:**

```gherkin
Scenario: Criar orçamento com sucesso
  GIVEN usuário abre formulário de novo orçamento
  WHEN  preenche os campos e submete
  THEN  chama POST /api/budgets
  AND   lista de orçamentos é atualizada
  AND   toast "Orçamento criado" exibido

Scenario: Orçamento duplicado
  GIVEN já existe orçamento para "Alimentação" em 04/2026
  WHEN  usuário tenta criar o mesmo
  THEN  API retorna 409
  AND   formulário exibe "Já existe orçamento para esta categoria neste mês"
```

**Critérios de aceitação:**

- [ ] CA-FE-32: Criação válida atualiza lista e exibe toast
- [ ] CA-FE-33: Erro 409 exibe mensagem amigável no formulário

---

## 6. CHAT — Assistente com IA

### Contexto

A tela de chat consome o endpoint de streaming SSE do `chat-service`. Os chunks chegam incrementalmente e são renderizados em tempo real (efeito de digitação). O hook `useChatStream` gerencia o estado local de mensagens e o stream.

---

### Regras de negócio

- `RN-FE-17` A resposta do assistente é exibida progressivamente via streaming SSE.
- `RN-FE-18` O input de mensagem fica desabilitado durante o streaming.
- `RN-FE-19` O scroll da conversa acompanha automaticamente as novas mensagens.
- `RN-FE-20` Erros de streaming exibem mensagem inline na conversa — não um toast genérico.
- `RN-FE-21` O histórico de mensagens anteriores é carregado ao abrir a tela (GET /api/chat/history).

---

### 6.1 Envio de mensagem com streaming

**Cenários:**

```gherkin
Scenario: Envio e streaming bem-sucedido
  GIVEN usuário está na tela /chat
  WHEN  digita uma mensagem e pressiona Enter ou clica em "Enviar"
  THEN  a mensagem do usuário aparece imediatamente na conversa (role=USER)
  AND   indicador de "digitando..." aparece na bolha do assistente
  AND   chunks chegam via SSE e são renderizados progressivamente
  AND   o input fica desabilitado durante o streaming
  AND   ao receber [DONE], o input é reabilitado e cursor posicionado nele
  AND   o scroll permanece no fundo da conversa

Scenario: Mensagem vazia bloqueada
  GIVEN campo de input vazio
  WHEN  usuário pressiona Enter ou clica em "Enviar"
  THEN  nenhuma chamada é feita
  AND   foco permanece no input

Scenario: Erro durante streaming
  GIVEN streaming iniciado
  WHEN  conexão é interrompida
  THEN  a bolha do assistente exibe "Ocorreu um erro. Tente novamente."
  AND   o input é reabilitado
  AND   botão "Tentar novamente" aparece na mensagem com erro

Scenario: Carregamento do histórico
  GIVEN usuário abre /chat pela primeira vez na sessão
  WHEN  a página carrega
  THEN  histórico das conversas anteriores é carregado e exibido
  AND   scroll posicionado no fundo (mensagem mais recente visível)
```

**Critérios de aceitação:**

- [ ] CA-FE-34: Mensagem do usuário aparece imediatamente ao enviar
- [ ] CA-FE-35: Chunks SSE aparecem progressivamente na tela
- [ ] CA-FE-36: Input desabilitado durante streaming
- [ ] CA-FE-37: Input reabilitado ao receber [DONE]
- [ ] CA-FE-38: Scroll acompanha novas mensagens automaticamente
- [ ] CA-FE-39: Erro exibe mensagem inline com opção de tentar novamente
- [ ] CA-FE-40: Histórico carregado ao abrir a tela
- [ ] CA-FE-41: Mensagem vazia não dispara chamada

---

## 7. REPORTS — Relatórios

### 7.1 Solicitar e acompanhar relatório

**Regras de negócio:**

- `RN-FE-22` Ao solicitar um relatório, a tela exibe o status `PENDING` imediatamente.
- `RN-FE-23` O status é atualizado via polling a cada 5 segundos enquanto `PENDING` ou `PROCESSING`.
- `RN-FE-24` Quando `DONE`, o botão "Download" é habilitado.
- `RN-FE-25` O download abre a signed URL em nova aba — não faz download via blob.

**Cenários:**

```gherkin
Scenario: Solicitar relatório mensal
  GIVEN usuário acessa /reports
  WHEN  seleciona type="MONTHLY", year=2026, month=4 e clica em "Gerar"
  THEN  chama POST /api/reports
  AND   relatório aparece na lista com status "Processando..."
  AND   polling inicia a cada 5 segundos para atualizar o status

Scenario: Relatório pronto
  GIVEN relatório com status="PENDING" sendo acompanhado
  WHEN  polling detecta status="DONE"
  THEN  linha do relatório atualiza para "Pronto"
  AND   botão "Download" aparece habilitado
  AND   polling para

Scenario: Download
  GIVEN relatório com status="DONE"
  WHEN  usuário clica em "Download"
  THEN  chama GET /api/reports/{id}/download
  AND   signed URL é aberta em nova aba do navegador

Scenario: Relatório com falha
  GIVEN relatório com status="FAILED"
  WHEN  polling detecta o status
  THEN  exibe "Falha na geração" com botão "Tentar novamente"
```

**Critérios de aceitação:**

- [ ] CA-FE-42: Solicitação exibe status PENDING imediatamente
- [ ] CA-FE-43: Polling atualiza status a cada 5 segundos
- [ ] CA-FE-44: Botão Download habilitado apenas para status DONE
- [ ] CA-FE-45: Download abre signed URL em nova aba
- [ ] CA-FE-46: Status FAILED exibe opção de tentar novamente
- [ ] CA-FE-47: Polling para quando status é DONE ou FAILED

---

## 8. LAYOUT — Navegação e estrutura

### 8.1 Sidebar

**Regras de negócio:**

- `RN-FE-26` O item de navegação ativo é destacado visualmente com cor primária.
- `RN-FE-27` A sidebar pode ser recolhida em telas menores (responsividade).
- `RN-FE-28` O estado aberto/fechado da sidebar persiste via Zustand no localStorage.

**Itens de navegação:**

| Item | Rota | Ícone |
|---|---|---|
| Dashboard | `/` | LayoutDashboard |
| Transações | `/transactions` | ArrowLeftRight |
| Orçamentos | `/budgets` | PieChart |
| Chat IA | `/chat` | MessageSquare |
| Relatórios | `/reports` | FileText |
| Configurações | `/settings` | Settings |

**Cenários:**

```gherkin
Scenario: Item ativo destacado
  GIVEN usuário está na rota /transactions
  WHEN  visualiza a sidebar
  THEN  o item "Transações" está destacado com cor primária
  AND   todos os outros itens estão no estado padrão

Scenario: Persistência do estado da sidebar
  GIVEN usuário recolheu a sidebar
  WHEN  recarrega a página
  THEN  a sidebar permanece recolhida
```

**Critérios de aceitação:**

- [ ] CA-FE-48: Item da rota atual exibido como ativo
- [ ] CA-FE-49: Estado da sidebar persiste entre sessões de navegação

---

### 8.2 Header

**Elementos:**

- Nome do usuário autenticado (lido da sessão NextAuth)
- Botão "Sair" que executa logout

---

## 9. CROSS-CUTTING — Regras transversais

### 9.1 Formatação e internacionalização

- `RN-FE-29` Todos os valores monetários usam `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`.
- `RN-FE-30` Datas são formatadas com `date-fns` e locale `pt-BR`.
- `RN-FE-31` Percentuais são exibidos com 1 casa decimal (ex: `86.0%`).
- `RN-FE-32` Nunca exibir valores brutos da API sem formatação.

### 9.2 Loading states

- `RN-FE-33` Toda chamada de leitura (GET) exibe skeleton enquanto carrega — não spinner genérico.
- `RN-FE-34` Toda chamada de escrita (POST/PUT/DELETE) exibe o botão em estado de loading e desabilitado.
- `RN-FE-35` Erros de rede exibem mensagem amigável com opção de tentar novamente — nunca mensagem técnica.

### 9.3 Toasts e feedback

- `RN-FE-36` Operações de escrita bem-sucedidas exibem toast de sucesso por 3 segundos.
- `RN-FE-37` Erros de validação do servidor (400) são mapeados para os campos correspondentes no formulário.
- `RN-FE-38` Erros genéricos do servidor (500, 503) exibem toast de erro com mensagem amigável.

### 9.4 TanStack Query — invalidação de cache

| Operação | Cache invalidado |
|---|---|
| Criar transação | `['transactions', 'list']` |
| Editar/excluir transação | `['transactions', 'list']` |
| Criar orçamento | `['budgets']` (todos) |
| Editar/excluir orçamento | `['budgets']` (todos) |
| Gerar relatório | `['reports', 'list']` |

### 9.5 Acessibilidade

- `RN-FE-39` Todos os componentes de formulário têm `label` associado via `htmlFor`.
- `RN-FE-40` Erros de validação são associados ao campo via `aria-describedby`.
- `RN-FE-41` Modais e dialogs têm foco gerenciado (focus trap).
- `RN-FE-42` Contraste mínimo WCAG AA em todos os textos.

### 9.6 Segurança no frontend

- `RN-FE-43` JWT armazenado apenas na sessão do NextAuth — nunca em `localStorage` ou cookie não-httpOnly.
- `RN-FE-44` URLs dos microserviços são variáveis de servidor (`process.env.`) — nunca expostas ao browser (`NEXT_PUBLIC_`).
- `RN-FE-45` O proxy reverso em `app/api/[...path]/route.ts` remove o header `host` antes de repassar ao microserviço.

---

*SpendWise Frontend SDD v1.0 — Abril 2026*
