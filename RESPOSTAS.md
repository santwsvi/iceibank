# RESPOSTAS.md - ICEIBank (Java / Spring Boot)

## Sprint 3 - implementação

### Funcionalidade adicional

Timeout da comunicação Bully configurável pelas variáveis `BULLY_HEARTBEAT_MS` (padrão 3000 ms) e `BULLY_TIMEOUT_MS` (padrão 1000 ms), definido em `application.properties`. Isso permite ajustar a sensibilidade da detecção para rede local ou ambiente de laboratório sem recompilar.

### Decisões de implementação

- O backend permaneceu em Java/Spring Boot. As agências trocam eleição e anúncio pelos endpoints `/bully/eleicao` e `/bully/coordenador`; `/bully/lider` é consultável sem JWT para a comunicação entre processos e pelo painel móvel.
- O heartbeat é agendado pelo Spring. Ao falhar o contato com a liderança, a agência registra o evento e inicia nova eleição. `historico-lideranca.ps1` reconstitui os eventos dos arquivos JSONL no Windows.
- A sincronização compara o vetor conhecido com o vetor atual da agência. Um vetor diferente retorna HTTP 409 com conta e vetor atuais. A granularidade é por agência, então outras contas podem provocar conflito.
- O app usa SQLite (`sqflite`) para cache e fila, `connectivity_plus` para perceber reconexão e apresenta resultado provisório para depósitos/saques offline. O 409 mantém a operação marcada para revisão e atualiza o saldo mostrado com o valor real. Transferências são online nesta versão.
- Flutter consulta a liderança a cada quatro segundos e tenta as três agências. No Android Emulator, a base HTTP usa `10.0.2.2`; em dispositivo físico, deve ser configurada para o IP acessível do computador.

### Respostas conceituais e evidências

O maior ID vence por convenção do Bully, mas inverter a prioridade também seria correto se todos usassem consistentemente a mesma regra. Mais de um `TORNOU_LIDER` durante eleições simultâneas é possível: anúncios podem se cruzar antes de convergir, gerando tráfego e mudanças transitórias. Heartbeat muito curto aumenta tráfego e falsos positivos; muito longo atrasa failover. Uma rede lenta acima do timeout pode parecer falha e causar eleição desnecessária. Uma líder que retorna com ID maior pode reassumir a liderança, criando troca durante operação; no Sprint 3 a liderança é informativa.

O app reaproveita endpoints REST e JWT do frontend web. O polling de quatro segundos equilibra atualização e tráfego; uma resposta transitória divergente converge nas próximas consultas. A UI móvel usa cache persistente e fila local, exigindo estados explícitos de pendência e conflito que não existem no fluxo web.

O conflito compara vetor inteiro porque mudanças causais podem ocorrer sem alteração do saldo observado; um saldo pode até voltar ao valor anterior após duas operações. A comparação por agência é deliberadamente ampla. Para maior precisão, seria necessário vetor/versão por conta ou registro de eventos associados à conta. Após HTTP 409, o app mostra o saldo real e deixa a operação em revisão, sem reaplicá-la automaticamente para evitar duplicidade/decisão financeira silenciosa.

UI otimista pode mostrar fundos que o servidor não aceitou, então o app marca saldo como provisório e destaca as operações pendentes. A escolha por SQLite permite persistir cache e fila entre reinicializações; Hive seria alternativa simples, mas não relacional.

**Limitação de validação desta sessão:** Flutter SDK não está disponível no PATH; não foi possível gerar a plataforma Android pelo Flutter CLI, executar no emulador ou capturar os oito prints reais. Esses arquivos devem ser capturados no ambiente com Flutter/emulador e guardados em `evidencias/sprint3/`.

> Linguagem escolhida: **Java (Spring Boot)**, mantida do Sprint 1 ao Sprint 4.

## Recapitulação do Sprint 1 e proposta do Sprint 2

### 1. Arquitetura e agências

O projeto executa três instâncias do mesmo serviço Spring Boot, identificadas por `AGENCIA_ID=0`, `1` ou `2`, normalmente nas portas 4000, 4001 e 4002. `AppConfig.agenciaResponsavel(id)` particiona as contas por `id % 3`; cada instância mantém em memória as contas da sua partição. Controllers expõem a API REST; `ContaService` mantém as contas; `TransferenciaService` coordena transferências; Spring Security/JWT protege a API; o HTML estático fornece o frontend. Operações de conta são locais à instância proprietária; consultas e transferências iniciadas por outra agência dependem de comunicação entre processos.

### 2. Comunicação antes do Sprint 2

Uma transferência remota debitava a origem e fazia um POST REST síncrono ao endpoint `/contas/{id}/creditar-remoto` da agência destino, autenticado com `X-Agencia-Secret`. A resposta só chegava depois do crédito remoto. Se a agência destino estivesse fora do ar ou excedesse o timeout, a chamada falhava e o débito permanecia aplicado, limitação intencional documentada no Sprint 1.

### 3. Efeitos da transferência

`TransferenciaService` valida a conta e o saldo na origem, avança o relógio da agência, debita e registra o evento. Para mesma agência, credita localmente e registra outro evento. Entre agências, envia id da conta, valor e metadados de Lamport via HTTP; o destino valida a conta, mescla o timestamp, credita e registra o evento. O cliente recebia sucesso após o crédito remoto; uma falha de rede deixava o estado parcial.

### 4. Eventos candidatos à publicação

- `transferencia.credito.solicitado`: produtor, agência de origem; dados: `messageId`, conta destino, valor, agência de origem e vetor causal; consumidor, fila da agência destino.
- `transferencia.credito.aplicado` ou `transferencia.credito.falhou`: produtor, agência destino; dados: `messageId`, conta, valor, origem, vetor e resultado; consumidores possíveis, agência de origem, auditoria e monitoramento.

### 5. Fluxo Pub/Sub proposto

A agência de origem publica um comando durável na exchange topic `iceibank.eventos`, com routing key `agencia.<id>.creditar`. A fila durável `fila-agencia-<id>` vinculada pela chave recebe a mensagem; o consumidor da agência destino mescla o vetor, tenta aplicar o crédito e registra o resultado. A origem responde que a mensagem foi publicada; a aplicação do crédito é assíncrona. Uma confirmação de crédito é uma evolução possível.

### 6. Duplicidade e falha

Uma redelivery pode creditar duas vezes se o consumidor aplicar o saldo e cair antes do ACK. A mensagem precisa de identificador estável (`messageId`) e o consumidor deve registrar IDs processados de forma durável e atômica com o crédito para garantir idempotência. Eventos fora de ordem podem aplicar crédito antes de uma operação causal anterior; sequência por conta, validação de estado e metadados causais ajudam a detectar e tratar isso. No escopo atual, contas e deduplicação em memória não sobrevivem a reinícios; a DLQ preserva mensagens rejeitadas, mas não resolve persistência nem transações.

### 7. Ordem de recebimento e concorrência

Se a agência 0 envia mensagens M1 e M2 em sequência, mas M1 sofre atraso e M2 é processada primeiro, a ordem observada pode divergir da ordem de envio. Da mesma forma, operações locais independentes nas agências 0 e 1 podem chegar aos logs em qualquer ordem. Ordenar apenas pelo relógio de parede pode sugerir uma causalidade que não existiu; deve-se distinguir ordem local, ordem de recebimento e causalidade.

### 8. Relógio vetorial

O vetor tem três posições, uma por agência. Evento local incrementa a posição própria; envio incrementa e anexa uma cópia; recebimento mescla posição a posição pelo máximo e incrementa a posição própria. Por exemplo, `[1,0,0]` e `[0,1,0]` são concorrentes; após uma mensagem do primeiro evento ser recebida na agência 1, um evento resultante como `[1,2,0]` é posterior a `[1,0,0]`.

### 9. Observabilidade

Cada evento registra agência, tipo, vetor, hora de parede, detalhes e, quando aplicável, `messageId`, origem/destino, routing key e resultado. Comparação componente a componente determina precedência causal ou concorrência. O log precisa preservar os vetores antes e depois do recebimento para explicar a propagação causal.

### 10. Evolução proposta

Substituir o POST remoto pelo publish durável na exchange topic do RabbitMQ; cada agência consome sua fila, mescla o vetor e aplica o crédito. A mensagem carrega ID estável, origem, destino, valor e vetor. O script de linha do tempo compara vetores e aponta eventos concorrentes. O teste de resiliência desliga o consumidor, publica uma mensagem e o religa; a fila retém a mensagem, mas a conta em memória pode ter desaparecido após reinício. A dead-letter queue será usada como funcionalidade adicional para preservar mensagens que não possam ser processadas.

## Sprint 2 - relógio vetorial e mensageria

### Parte B - questões 6.4

1. Com 10 agências cada vetor enviado terá 10 contadores, crescendo linearmente com o número de processos. Para três agências o custo é pequeno; em sistemas com milhares ou milhões de processos, vetor completo aumenta cada mensagem e a comparação. Existem relógios vetoriais esparsos ou hierárquicos para reduzir esse custo, com trade-offs.
2. `[3,1,0]` aconteceu antes de `[3,2,0]`: todas as posições do primeiro são menores ou iguais, e a segunda posição é estritamente maior.
3. `[3,1,0]` e `[1,3,0]` são concorrentes: o primeiro é maior na posição 0 e menor na posição 1, portanto nenhum vetor domina o outro.

### Parte C - questões 7.5

1. O consumidor atualiza o vetor ao receber. Se a conta foi apagada porque o processo foi reiniciado, a mensagem continua na fila, mas o crédito não pode ser aplicado. Após três tentativas, o consumidor rejeita a mensagem e o RabbitMQ a encaminha à fila de mensagens mortas da agência. O broker preserva a mensagem; a DLQ torna a falha visível e recuperável.
2. Em relação ao POST síncrono, a mensagem pode aguardar a agência destino voltar, e a origem confirma a publicação sem esperar o crédito. Isso reduz falhas por indisponibilidade transitória. Ainda há estados parciais: o débito ocorre antes do crédito, contas não persistem, a deduplicação atual é em memória e não há transação atômica entre saldo e ACK. Uma DLQ não é recuperação automática do saldo.
3. Não há JWT no consumidor porque JWT protege chamadas HTTP de usuários, enquanto o consumidor processa uma mensagem do broker. Isso não torna a mensagem automaticamente confiável: quem tem credenciais de publicação no RabbitMQ pode tentar forjar créditos. Em ambiente compartilhado, deve-se restringir credenciais/permissões por serviço, proteger TLS e validar origem, destino e conteúdo; JWT não substitui autenticação do broker.

### Parte D - questões 8.3

1. Cada posição identifica quanto da história de uma agência o evento conhece. Se os vetores forem incomparáveis componente a componente, nenhum evento causou o outro; um escalar de Lamport não carrega essa informação.
2. No ensaio local, `CRIAR_CONTA` da agência 2 recebeu `[0,0,1]` e `DEPOSITO` independente da agência 0 recebeu `[6,0,0]`. Os vetores são incomparáveis e as operações não trocaram mensagens, então a concorrência faz sentido. A transferência normal também mostrou débito `[2,0,0]` e crédito remoto `[3,2,0]`; o segundo domina o primeiro e, portanto, essa relação causal não é classificada como concorrente. A tentativa feita enquanto a agência destino estava parada resultou em três eventos de falha e mensagem na DLQ.
3. A comparação de todos os pares custa O(n²), inviável para logs muito grandes. Pode-se filtrar por janela/agência e particionar por período ou conta, armazenar índices de causalidade e processar lotes/consultas incrementais. A comparação exata continua custosa no pior caso.

### Funcionalidade adicional - DLQ por agência

Cada fila de crédito usa uma dead-letter exchange e uma fila durável `fila-agencia-<id>.dlq`. Falhas no processamento são tentadas três vezes com espera exponencial; depois a mensagem é rejeitada sem requeue e roteada para a DLQ. Isso cobre, por exemplo, conta destino ausente. A mensagem pode ser inspecionada e reenviada depois que o estado for corrigido. Captura de tela de execução real ainda pendente por depender de RabbitMQ acessível.

`GET /mensageria/dlq/status` (JWT obrigatório) informa a fila da agência e a quantidade de mensagens pendentes. Após corrigir o motivo da falha, a pessoa operadora pode usar RabbitMQ Manager para inspecionar e republicar a mensagem; a rota não descarta nem altera mensagens.

### Limitações observadas no ambiente desta edição

O código preserva contas em memória como no Sprint 1. A compilação limpa, os fluxos HTTP autenticados, a transferência assíncrona, a perda da conta no reinício, o encaminhamento à DLQ, o comparador causal e uma consulta pelo frontend foram verificados em um broker RabbitMQ local isolado. As quatro capturas PNG indicadas pelo roteiro continuam pendentes: a ferramenta de navegador desta sessão exibiu a tela, mas não oferece forma de gravar a imagem no diretório `evidencias/sprint2/`. A deduplicação em memória protege redelivery enquanto o processo permanece ativo, mas não sobrevive a reinício; idempotência durável requer armazenamento persistente coordenado com a alteração do saldo.

## Parte B - Relogio de Lamport e registro de eventos (secao 6.4)

**1. Por que `max(contador_local, timestampRecebido) + 1` em vez de adotar o timestamp recebido diretamente?**

Porque o relogio de Lamport precisa preservar duas garantias ao mesmo tempo: (a) o contador local nunca pode andar para tras (senão eventos locais futuros poderiam ter timestamp menor que eventos locais passados) e (b) todo evento que "aconteceu depois de receber uma mensagem" precisa ter timestamp estritamente maior que o da mensagem recebida (causa sempre antes do efeito). Adotar diretamente o timestamp recebido quebraria (a) sempre que o contador local ja estivesse a frente; usar `max(...) + 1` garante as duas coisas ao mesmo tempo: o novo valor e maior que o que a agencia ja tinha E maior que o que ela recebeu.

**2. Agencia 0 no contador 10 recebe mensagem com timestamp 3: qual o novo valor?**

`max(10, 3) + 1 = 11`. O contador da agencia mais rapida praticamente ignora o timestamp da agencia mais lenta (so usa o "+1" para garantir a ordem causal). Isso implica que agencias que processam muitos eventos rapidamente "puxam" o relogio logico geral para cima, e agencias mais lentas ou ociosas ficam sempre few passos atras nos seus proprios contadores ate receberem uma mensagem que as force a saltar para frente. Ou seja, o valor absoluto do contador de Lamport reflete mais "quantos eventos esta agencia ja viu, direta ou indiretamente" do que tempo real decorrido.

## Parte D - Transferencias (secao 8.3)

**1. Por que a transferencia local nao precisa de `aoEnviar()`/`aoReceber()`?**

Porque `aoEnviar()`/`aoReceber()` existem para marcar a passagem de uma mensagem entre dois **processos distintos** (duas JVMs/agencias diferentes) - e exatamente o mecanismo que garante a ordenacao causal entre eventos de processos diferentes. Quando origem e destino estao na mesma agencia, debito e credito acontecem no mesmo processo, protegidos pelo mesmo relogio; basta `eventoLocal()` duas vezes, pois nao ha nenhuma mensagem de rede cuja causalidade precise ser propagada.

**2. Saldo da conta de origem apos a falha reproduzida - foi revertido?**

Nao. O saldo da conta de origem permanece debitado mesmo com a chamada remota falhando - e exatamente a limitacao conhecida descrita no roteiro. Isso significa que, momentaneamente, o sistema perde a propriedade de atomicidade (a soma total de dinheiro no sistema "diminui" enquanto o problema nao e corrigido) e de consistencia bancaria (o valor debitado nao apareceu em lugar nenhum). O log registra a inconsistencia (`TRANSFERENCIA_FALHOU`), mas nao a corrige sozinho.

**3. Duas formas possiveis de corrigir isso (Sprint 4, alto nivel):**

- **Two-Phase Commit (2PC):** um coordenador pergunta as duas agencias "voce consegue aplicar sua parte?" (fase de preparacao/prepare), so aplica de fato (commit) se ambas confirmarem que conseguem; se qualquer uma falhar, ambas fazem rollback. Garante atomicidade forte, ao custo de bloquear recursos enquanto aguarda confirmacao.
- **Saga (compensacao):** a transferencia e dividida em passos locais independentes (debitar na origem, depois creditar no destino); se um passo posterior falhar, uma acao compensatoria (ex.: re-creditar a origem) e disparada para desfazer o que ja foi feito. Nao bloqueia recursos como o 2PC, mas exige que cada passo tenha uma compensacao bem definida e tolere estados intermediarios visiveis por um tempo.

## Parte E - Linha do tempo unificada (secao 10.3)

**1. O que significa ver dois timestamps diferentes sem saber se ha relacao causal?**

Significa que o relogio de Lamport garante a implicacao em um unico sentido: `A -> B` (causalmente) implica `timestamp(A) < timestamp(B)`, mas a volta nao vale. Dois eventos com timestamps diferentes podem ser: (a) genuinamente causalmente relacionados, ou (b) apenas concorrentes, e o algoritmo apenas "por acaso" atribuiu timestamps diferentes a eles (porque o contador e um numero unico global crescente, nao um vetor por processo). Portanto, so olhando o timestamp de Lamport, nao da para afirmar com certeza que A influenciou B - apenas que B nao pode ter influenciado A.

**2. O relogio de Lamport sozinho seria suficiente para distinguir concorrencia de precedencia com certeza? Por que motiva o relogio vetorial?**

Nao. Como so garante uma direcao da implicacao, o relogio de Lamport nao permite, dados dois timestamps, decidir com certeza se os eventos sao concorrentes ou se um precede o outro causalmente - para isso seria necessario comparar toda a "historia causal" de cada evento, o que um unico numero inteiro nao carrega. O relogio vetorial (Sprint 2) resolve exatamente essa limitacao: cada processo mantem um vetor com um contador por processo do sistema, permitindo comparar dois eventos e concluir com certeza se sao concorrentes (nenhum vetor domina o outro) ou se um aconteceu antes do outro (um vetor domina o outro em todas as posicoes).

## Parte F - Autenticacao JWT (secao 11.3)

**Design das credenciais:** login por par usuario/senha (nao id de conta + senha), documentado no `AuthController`, com dois usuarios fixos em memoria para fins didaticos deste sprint (`aluno`/`senha123` e `operador`/`operador123`). Motivo: uma pessoa pode movimentar mais de uma conta bancaria, entao amarrar autenticacao 1-para-1 a uma conta seria um modelo menos realista.

**Tempo de expiracao:** 15 minutos (configuravel via `iceibank.jwt.expiracao-minutos` em `application.properties`), curto o suficiente para reduzir a janela de uso indevido de um token vazado, mas confortavel para uma sessao de uso do frontend durante os testes.

**Chamada interna (creditar-remoto) - JWT de usuario ou algo diferente?**

Decidimos **nao** usar o mesmo JWT de usuario para a chamada agencia-a-agencia. A chamada `creditar-remoto` nao representa uma pessoa autenticada operando a API - representa uma agencia confiavel completando, em nome do sistema, uma transferencia que ja foi autorizada quando o usuario chamou `/transferencias` na agencia de origem (que ja validou o JWT do usuario ali). Por isso usamos um segredo de servico separado (`X-Agencia-Secret`, configuravel via `iceibank.internal.secret`), mais simples de rotacionar/gerenciar sem afetar sessoes de usuarios, e que evita reutilizar um token pessoal em uma chamada de maquina-para-maquina.

**1. Autenticacao vs autorizacao - a implementacao cobre as duas?**

Autenticacao e "provar quem voce e" (login + validacao do JWT). Autorizacao e "o que voce, ja identificado, tem permissao para fazer". A implementacao atual cobre **apenas autenticacao**: qualquer usuario com um token valido pode operar **qualquer conta** de qualquer agencia (nao ha vinculo entre o `usuario` do token e um conjunto de contas permitidas). Ou seja, na implementacao atual, um usuario autenticado consegue sim sacar de uma conta que nao e "dele" - nao ha essa nocao de posse implementada neste sprint. Isso e uma limitacao conhecida, deixada em aberto (poderia ser resolvida associando cada usuario a uma lista de ids de conta e verificando essa lista em cada operacao).

**2. Por que o servidor nao precisa consultar um banco para validar a assinatura do JWT?**

Porque a assinatura e verificada matematicamente com a mesma chave secreta usada para assinar (HMAC-SHA, via `Keys.hmacShaKeyFor(...)` + `jjwt`), e o proprio token carrega os dados necessarios (usuario, data de emissao, expiracao) em seu payload. Isso torna o backend **stateless**: qualquer instancia da agencia consegue validar qualquer token sozinha, sem compartilhar estado de sessao com outras instancias nem bater em um banco a cada requisicao. Isso melhora bastante a escalabilidade horizontal (multiplas instancias atras de um load balancer, sem "sticky sessions"), ao custo de nao ser trivial revogar um token individual antes da sua expiracao (diferente de sessoes em memoria/banco, que podem ser apagadas a qualquer momento).

**3. O que aconteceria se a chave secreta do JWT vazasse?**

Qualquer pessoa de posse da chave poderia forjar tokens validos para qualquer usuario (inclusive usuarios que nao existem), sem precisar saber nenhuma senha - comprometendo totalmente a autenticacao do sistema. A correcao exigiria trocar a chave secreta imediatamente (invalidando todos os tokens emitidos ate entao, forcando novo login de todos) e investigar como a chave vazou.

## Parte G - Frontend (secao 12.3)

**1. Como o frontend "lembra" de reenviar o token?**

O token retornado pelo `/auth/login` e guardado em uma variavel JavaScript (`token`) no escopo do script da pagina (ver `frontend`/`index.html`). A cada chamada subsequente (`chamarApi(...)`), o cabecalho `Authorization: Bearer <token>` e montado automaticamente antes do `fetch`. Como e uma variavel em memoria (nao `localStorage`), o token se perde ao recarregar a pagina - uma simplificacao aceitavel para o escopo deste sprint (documentada aqui; trocar para `localStorage`/`sessionStorage` seria a evolucao natural, com a devida atencao a riscos de XSS).

**2. O que acontece se o token expirar no meio do uso?**

A proxima chamada a API recebe HTTP 401. O `chamarApi(...)` do frontend trata esse caso explicitamente: mostra uma mensagem de erro visivel ("Sessao expirada ou nao autenticado...") e chama `logout()`, devolvendo a pessoa usuaria para a tela de login. Ou seja, a interface avisa - o erro nao fica apenas no console do navegador.

**3. Onde ficam o M, o V e o C no frontend?**

- **Model:** as variaveis de estado (`token`, `agenciaBase`) e os objetos JSON recebidos da API (conta, resultado de transferencia, itens de historico).
- **View:** o HTML/CSS estatico (formularios, tabelas) e as funcoes `mostrarErro`/`mostrarOk`, que atualizam o DOM a partir dos dados.
- **Controller:** as funcoes `login`, `consultarSaldo`, `movimentar`, `transferir`, `consultarHistorico` e `chamarApi`, que recebem a interacao do usuario, chamam a API (via `fetch`) e decidem o que mostrar na View.

A separacao existe de forma clara, mas e informal (um unico arquivo `index.html`, sem um framework MVC de fato) - em um frontend maior (React/Vue), isso naturalmente evoluiria para componentes/stores separados.

## Funcionalidade adicional (secao 2.1)

**Escolhida: Historico de transacoes por conta**, com um bonus adicional: **rota de status/health-check por agencia**.

- `GET /contas/{id}/historico` retorna a lista de eventos (criacao, deposito, saque, transferencia enviada/recebida, credito remoto recebido) que afetaram aquela conta especifica, ja filtrados e ordenados - implementado em `HistoricoService` + `ExtraController`. Foi escolhida porque complementa diretamente o extrato que o frontend (Parte G) precisa exibir, e reaproveita a mesma infraestrutura de eventos ja criada para o log geral (Parte B), sem duplicar logica de negocio.
- `GET /status` retorna o id da agencia, o valor atual do relogio de Lamport e a quantidade de contas sob sua responsabilidade - util para verificar rapidamente se uma agencia esta viva e em que "momento logico" ela esta, sem precisar ler o arquivo `.jsonl` diretamente.

Evidencia de teste: `evidencias/sprint1/funcionalidade-adicional.png`.

## Evidencias

Ver pasta `evidencias/sprint1/` para os prints indicados nas secoes 4.2, 2.1, 11.2 e 12.2 do roteiro (a preencher pelo aluno durante a execucao dos testes descritos neste documento).
