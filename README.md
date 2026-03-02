# Garage API — Estapar Backend Test

Sistema backend para gerenciamento de estacionamento: controle de vagas, entrada/saída de veículos e cálculo de receita por setor.

---

## Tecnologias

- Java 21
- Spring Boot 4.x
- Hibernate / Spring Data JPA
- MySQL 8
- Docker
- Maven (Wrapper incluso)

---

## Pré-requisitos

- Java 21 instalado
- Docker Desktop instalado e rodando
- Portas `3000`, `3003` e `3306` disponíveis na máquina

---

## Arquitetura

```
┌─────────────────────────────────────────────────────┐
│  Docker                                             │
│  ┌──────────────┐      ┌──────────────────────────┐ │
│  │  garage-sim  │      │      garage-mysql         │ │
│  │  :3000       │      │      :3306                │ │
│  └──────┬───────┘      └──────────────────────────┘ │
│         │ webhook POST localhost:3003                │
└─────────┼───────────────────────────────────────────┘
          │
┌─────────▼────────────────────────────────────────────┐
│  Aplicação Spring Boot (rodando no host)              │
│  :3003                                                │
│  - GET localhost:3000/garage  (bootstrap)             │
│  - POST /webhook              (recebe eventos)        │
│  - GET  /revenue              (consulta receita)      │
└──────────────────────────────────────────────────────┘
```

> **Importante:** a aplicação deve rodar **fora do Docker** (`mvnw spring-boot:run`) porque o simulador envia webhooks para `localhost:3003` de forma hardcoded, ignorando variáveis de ambiente.

---

## Como rodar

### 1. Crie a rede Docker

```bash
docker network create garage-net
```

> Se já existir, ignore o erro.

---

### 2. Suba o MySQL

```bash
docker run -d \
  --name garage-mysql \
  --network garage-net \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=garage \
  -p 3306:3306 \
  mysql:8.0
```

> Se o container já existir: `docker start garage-mysql`

Aguarde ~15 segundos para o MySQL inicializar completamente antes do próximo passo.

---

### 3. Suba o simulador

```bash
docker run -d \
  --name garage-sim \
  --network garage-net \
  --add-host=localhost:host-gateway \
  -p 3000:3000 \
  cfontes0estapar/garage-sim:1.0.0
```

O flag `--add-host=localhost:host-gateway` faz com que `localhost` dentro do container resolva para o IP da máquina host, onde a aplicação estará rodando.

> Se o container já existir: `docker start garage-sim`

---

### 4. Compile e suba a aplicação

```bash
# Windows CMD
mvnw spring-boot:run

# Windows PowerShell
.\mvnw spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

A aplicação sobe na porta **3003**. No startup, ela busca automaticamente a configuração da garagem do simulador e persiste as vagas no banco.

Log esperado:
```
Bootstrap OK: inserted 30 spots from simulator.
```

---

### 5. Inicie a simulação

Após a aplicação subir, chame o endpoint do simulador para iniciar o envio de webhooks:

```bash
curl http://localhost:3000/garage
```

A partir desse momento o simulador começará a enviar eventos `ENTRY`, `PARKED` e `EXIT` automaticamente para `http://localhost:3003/webhook`.

---

## Limpeza entre execuções

Se precisar reiniciar do zero (limpar banco + reiniciar simulador):

```bash
# Limpa o banco
docker exec -it garage-mysql mysql -u root -proot garage -e \
  "SET FOREIGN_KEY_CHECKS=0; \
   TRUNCATE TABLE parking_session_events; \
   TRUNCATE TABLE parking_sessions; \
   TRUNCATE TABLE parking_spots; \
   SET FOREIGN_KEY_CHECKS=1;"

# Reinicia o simulador
docker restart garage-sim
```

Depois pare e suba a aplicação novamente (`Ctrl+C` + `mvnw spring-boot:run`), e chame `curl http://localhost:3000/garage` para iniciar a simulação.

---

## API

### Webhook — recebe eventos do simulador

**POST** `http://localhost:3003/webhook`

#### ENTRY — veículo entrou na garagem
```json
{
  "license_plate": "ZUL0001",
  "entry_time": "2025-01-01T12:00:00.000Z",
  "event_type": "ENTRY"
}
```

#### PARKED — veículo estacionou em uma vaga
```json
{
  "license_plate": "ZUL0001",
  "lat": -23.561684,
  "lng": -46.655981,
  "event_type": "PARKED"
}
```

#### EXIT — veículo saiu da garagem
```json
{
  "license_plate": "ZUL0001",
  "exit_time": "2025-01-01T13:30:00.000Z",
  "event_type": "EXIT"
}
```

Todos retornam `HTTP 200` em caso de sucesso.

---

### Receita por setor e data

**GET** `http://localhost:3003/revenue?sector=A&date=2025-01-01`

#### Response
```json
{
  "amount": 150.00,
  "currency": "BRL",
  "timestamp": "2025-01-01T12:00:00.000Z"
}
```

Também aceita requisição via body:

```bash
curl -X GET http://localhost:3003/revenue \
  -H "Content-Type: application/json" \
  -d '{"sector": "A", "date": "2025-01-01"}'
```

---

## Regras de negócio

### Cobrança
- Primeiros **30 minutos são gratuitos**
- Após 30 minutos, cobra-se uma **tarifa por hora** com arredondamento para cima (ceiling)
- O valor base por hora vem do campo `basePrice` configurado por setor no simulador

**Exemplo:** entrada às 00:00, saída às 02:00
- 120 minutos total − 30 minutos gratuitos = 90 minutos faturáveis
- Arredondado para cima = **2 horas**
- Setor A (base R$ 40,50) com ocupação baixa (×0,90) = **R$ 72,90**

### Preço dinâmico

O multiplicador é calculado com base na **ocupação do setor no momento do PARKED**:

| Ocupação do setor | Multiplicador |
|---|---|
| < 25% | 0.90 (desconto de 10%) |
| 25% a 50% | 1.00 (sem alteração) |
| 50% a 75% | 1.10 (acréscimo de 10%) |
| 75% a 100% | 1.25 (acréscimo de 25%) |

### Lotação
- Com **100% de ocupação** no setor, novas entradas são recusadas com `HTTP 409` até que uma vaga seja liberada

### Idempotência
- Eventos duplicados (mesma placa + tipo + timestamp) são ignorados silenciosamente via constraint único na tabela `parking_session_events`

---

## Testes

```bash
# Windows CMD/PowerShell
mvnw test

# Linux / macOS
./mvnw test
```

**26 testes** cobrindo:
- Eventos ENTRY, PARKED e EXIT (sucesso e falhas)
- Idempotência de eventos duplicados
- Cálculo de cobrança (gratuidade, arredondamento, multiplicadores)
- Preço dinâmico nas 4 faixas de ocupação
- Casos de borda: placa nula, `event_type` inválido, timestamps com e sem timezone, setor lotado

---

## Variáveis de configuração

Definidas em `src/main/resources/application.properties`:

| Propriedade | Padrão | Descrição |
|---|---|---|
| `server.port` | `3003` | Porta da aplicação |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/garage` | URL do banco |
| `spring.datasource.username` | `root` | Usuário do banco |
| `spring.datasource.password` | `root` | Senha do banco |
| `garage.simulator.base-url` | `http://localhost:3000` | URL base do simulador |
| `garage.bootstrap.enabled` | `true` | Habilita carga inicial das vagas |
| `spring.jpa.hibernate.ddl-auto` | `update` | Gerenciamento do schema (cria/atualiza tabelas automaticamente) |

---

## Notas de implementação

- O simulador (`garage-sim`) tem a URL do webhook **hardcoded** como `http://localhost:3003/webhook` e **ignora** a variável de ambiente `EXTERNAL_API_URL`. Por isso a aplicação precisa rodar no host e não em container.
- O `ddl-auto=update` garante que alterações na entidade (como tornar colunas nullable) sejam aplicadas automaticamente no banco ao subir a aplicação.
- O campo `price_multiplier_applied` é gravado no evento **PARKED** (não no ENTRY) pois a ocupação do setor só é relevante quando o veículo efetivamente ocupa a vaga.
- Os campos `spot_id` e `price_multiplier_applied` são nullable na sessão pois no evento ENTRY ainda não se sabe qual vaga será ocupada nem qual multiplicador será aplicado.