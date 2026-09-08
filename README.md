# ICEIBank - Sprint 1 (Java / Spring Boot)

Implementacao em **Java 17 + Spring Boot 3** do Sprint 1 do ICEIBank: API REST/MVC de
contas particionadas em 3 agencias, relogio logico de Lamport, transferencias local e
entre agencias (com a limitacao conhecida documentada), autenticacao JWT e um frontend
web simples (HTML/JS puro, servido como recurso estatico pelo proprio Spring Boot).

## Requisitos

- JDK 17+
- Maven 3.9+ (ou use o `mvnw` se preferir gerar um wrapper)

## Estrutura

```
iceibank-java/
├── agencia/                     (projeto Maven - o mesmo codigo roda como as 3 agencias)
│   ├── pom.xml
│   └── src/main/java/com/iceibank/
│       ├── IceibankApplication.java
│       ├── MesclarLogs.java             (Parte E - linha do tempo unificada)
│       ├── config/                      (AppConfig = particionamento, WebConfig = RestTemplate)
│       ├── security/                    (JwtUtil, JwtAuthFilter, SecurityConfig)
│       ├── model/                       (Conta, Evento)
│       ├── service/                     (RelogioLamport, RegistroEventos, ContaService,
│       │                                 TransferenciaService, HistoricoService)
│       └── controller/                  (ContasController, TransferenciasController,
│                                          AuthController, ExtraController)
│   └── src/main/resources/
│       ├── application.properties
│       └── static/index.html            (Parte G - frontend)
├── evidencias/sprint1/                  (prints de teste - a preencher)
└── RESPOSTAS.md
```

## Como rodar as 3 agencias

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

## Linha do tempo unificada (Parte E)

Depois de gerar alguns eventos nas 3 agencias:

```powershell
cd iceibank-java/agencia
mvn compile exec:java -Dexec.mainClass=com.iceibank.MesclarLogs
```

## Frontend (Parte G)

Acesse `http://localhost:4000/` (ou 4001 / 4002) no navegador - o Spring Boot serve
`static/index.html` automaticamente. A tela permite escolher qual agencia usar como
"porta de entrada", fazer login, consultar saldo, depositar, sacar, transferir e ver o
historico de uma conta.

Usuarios de demonstracao: `aluno` / `senha123` ou `operador` / `operador123`
(ver `AuthController` - documentado como simplificacao didatica em `RESPOSTAS.md`).
