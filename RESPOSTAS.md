# RESPOSTAS.md - ICEIBank Sprint 1 (Java / Spring Boot)

> Linguagem escolhida: **Java (Spring Boot)**, mantida do Sprint 1 ao Sprint 4.

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
