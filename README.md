# ICEIBank - Sprint 2 (Java / Spring Boot)

Implementacao em **Java 17 + Spring Boot 3**: API REST/MVC de contas particionadas em
3 agencias, relógio vetorial, transferências remotas assíncronas com RabbitMQ, JWT e
frontend web simples servido pelo próprio Spring Boot. Mensagens não processadas após
tentativas são encaminhadas para uma dead-letter queue (DLQ) por agência.

## Requisitos

- JDK 17+
- Maven 3.9+ (ou use o `mvnw` se preferir gerar um wrapper)
- RabbitMQ 3 (local ou instância CloudAMQP)

## Estrutura

```
iceibank-java/
├── agencia/                     (projeto Maven - o mesmo codigo roda como as 3 agencias)
│   ├── pom.xml
│   └── src/main/java/com/iceibank/
│       ├── IceibankApplication.java
│       ├── MesclarLogs.java             (linha do tempo e concorrência causal)
│       ├── config/                      (AppConfig = particionamento, WebConfig = RestTemplate)
│       ├── security/                    (JwtUtil, JwtAuthFilter, SecurityConfig)
│       ├── model/                       (Conta, Evento)
│       ├── service/                     (RelogioVetorial, mensageria e serviços)
│       └── controller/                  (ContasController, TransferenciasController,
│                                          AuthController, ExtraController)
│   └── src/main/resources/
│       ├── application.properties
│       └── static/index.html            (Parte G - frontend)
├── evidencias/sprint1/                  (prints de teste - a preencher)
└── RESPOSTAS.md
```

## Como rodar as 3 agências

Cada agencia e o **mesmo jar**, diferenciado pela variavel de ambiente `AGENCIA_ID`
(0, 1 ou 2) e pela porta.

### Opcao A - com Maven (3 terminais)

```powershell
# Terminal 1 - Agencia 0
cd iceibank-java/agencia
$env:AGENCIA_ID=0; $env:PORTA=4000; mvn spring-boot:run

# Terminal 2 - Agencia 1
cd iceibank-java/agencia
$env:AGENCIA_ID=1; $env:PORTA=4001; mvn spring-boot:run

# Terminal 3 - Agencia 2
cd iceibank-java/agencia
$env:AGENCIA_ID=2; $env:PORTA=4002; mvn spring-boot:run
```

### Opcao B - empacotando o jar uma vez e rodando 3 instancias

```powershell
cd iceibank-java/agencia
mvn clean package -DskipTests

# Terminal 1
$env:AGENCIA_ID=0; $env:PORTA=4000; java -jar target/agencia.jar

# Terminal 2
$env:AGENCIA_ID=1; $env:PORTA=4001; java -jar target/agencia.jar

# Terminal 3
$env:AGENCIA_ID=2; $env:PORTA=4002; java -jar target/agencia.jar
```

Cada agencia grava seus eventos em `agencia/data/eventos-agencia-<id>.jsonl`
(pasta criada automaticamente, ja ignorada pelo `.gitignore`).

Defina `RABBITMQ_URL` em cada terminal, ou utilize a URL local padrão
`amqp://guest:guest@localhost:5672/`. Por exemplo, com PowerShell:

```powershell
$env:RABBITMQ_URL="amqp://guest:guest@localhost:5672/"
$env:AGENCIA_ID=0; $env:PORTA=4000; mvn spring-boot:run
```

As instâncias declaram a exchange topic `iceibank.eventos`, sua fila durável
`fila-agencia-<id>` e a fila de mensagens mortas `fila-agencia-<id>.dlq`. Uma
transferência remota responde quando a publicação é aceita; o crédito será aplicado
depois pelo consumidor. Se a agência destino reiniciar, suas contas em memória somem;
a mensagem não processada vai para a DLQ após as tentativas. Não há transação
distribuída nem persistência de contas nesta etapa.

Com JWT, `GET /mensageria/dlq/status` mostra a quantidade de mensagens em quarentena
da agência. Para inspecionar ou republicar mensagens depois de corrigir a causa,
use RabbitMQ Manager.

## Testando a API (antes do login, use Invoke-RestMethod/Postman com o token)

```powershell
# 1) Login
$login = Invoke-RestMethod -Uri "http://localhost:4001/auth/login" -Method Post `
  -ContentType "application/json" -Body '{"usuario":"aluno","senha":"senha123"}'
$token = $login.token

# 2) Criar conta 0 na Agencia 0 (0 % 3 == 0)
Invoke-RestMethod -Uri "http://localhost:4001/contas" -Method Post `
  -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" `
  -Body '{"id":1,"nomeAluno":"Douglas","saldoInicial":100}'

# 3) Consultar saldo
Invoke-RestMethod -Uri "http://localhost:4000/contas/0" -Method Get `
  -Headers @{ Authorization = "Bearer $token" }

# 4) Depositar
Invoke-RestMethod -Uri "http://localhost:4000/contas/0/depositar" -Method Post `
  -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body '{"valor":25}'

# 5) Transferencia entre agencias (crie a conta 1 na Agencia 1 antes)
Invoke-RestMethod -Uri "http://localhost:4000/transferencias" -Method Post `
  -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" `
  -Body '{"idOrigem":0,"idDestino":1,"valor":30}'
```

## Linha do tempo causal

Depois de gerar eventos independentes em mais de uma agência e transferências:

```powershell
cd iceibank-java/agencia
mvn compile exec:java -Dexec.mainClass=com.iceibank.MesclarLogs
```

## Frontend (Parte G)

Acesse `http://localhost:4000/` (ou 4001 / 4002) no navegador - o Spring Boot serve
`static/index.html` automaticamente. A tela permite escolher qual agencia usar como
"porta de entrada", fazer login, consultar saldo, depositar, sacar, transferir e ver o
historico de uma conta.

Usuários de demonstração: `aluno` / `senha123` ou `operador` / `operador123`
(ver `AuthController` - documentado como simplificacao didatica em `RESPOSTAS.md`).
