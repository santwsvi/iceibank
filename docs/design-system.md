# ICEIBank — mapa de UI e design system

## Mapa do projeto

| Área | Caminho | Responsabilidade |
|---|---|---|
| Backend de agência | `agencia/src/main/java/com/iceibank` | API REST, JWT, contas, transferências, relógio vetorial, Bully e mensageria. |
| Internet banking | `agencia/src/main/resources/static/index.html` | Login web, saldo, depósito/saque, transferência, histórico e consulta de líder. |
| Cliente mobile/web | `app_mobile/lib/main.dart` | App Flutter, login JWT, saldo/cache, operações, fila offline, sincronização e painel de líder. |
| Recursos Flutter | `app_mobile/pubspec.yaml`, `app_mobile/web` | Dependências e shell do build para navegador. |
| Evidências | `evidencias/sprint1`, `evidencias/sprint2`, `evidencias/sprint3` | Capturas organizadas por sprint. |
| Relato acadêmico | `RESPOSTAS.md` | Decisões e respostas das etapas do projeto. |

## Fluxos de tela

1. **Entrada:** selecionar agência, informar usuário e senha e receber o JWT.
2. **Visão geral:** ver saldo da conta consultada e líder atual, com atualização periódica.
3. **Movimentação:** consultar conta, depositar, sacar ou transferir; mostrar sucesso ou erro junto do formulário relacionado.
4. **Histórico:** carregar os eventos da conta em uma tabela legível.
5. **Offline no Flutter:** indicar saldo em cache, valor provisório, operações pendentes e conflitos que precisam de revisão.

## Tokens visuais

| Token | Valor | Uso |
|---|---|---|
| `ink / brand-dark` | `#102B26` | Texto de alto contraste e cartões de saldo. |
| `brand` | `#176B53` | Ações primárias, foco e estado conectado. |
| `mint` | `#DFF3E9` | Superfícies de confirmação e ações secundárias. |
| `gold` | `#E6BD62` | Marca e destaque de liderança. |
| `canvas` | `#F4F7F4` | Fundo de páginas. |
| `surface` | `#FFFFFF` | Cards, formulários e painéis. |
| `muted` | `#71807A` | Texto auxiliar e metadados. |
| `danger` | `#B5473E` | Erros e conflitos. |

Tipografia usa a pilha nativa do sistema; títulos têm peso alto e tracking levemente negativo. Cards têm cantos de 18–20 px, inputs 11–13 px, e controles mantêm altura mínima de 46–50 px. O grid do painel colapsa de duas colunas para uma coluna em telas estreitas; no Flutter, a grade passa a uma coluna abaixo de 780 px.

## Convenções de interação e acessibilidade

- Ações principais usam verbos objetivos e ficam junto dos campos que afetam.
- Erros e confirmações aparecem no contexto da operação, sem depender apenas de cor.
- Campos têm rótulos persistentes, foco visível e tamanhos adequados para toque.
- Liderança usa texto explícito e estado de atualização; a cor e o ícone são reforços.
- Dados de conta são escapados antes de serem renderizados no HTML.
- Valores monetários usam duas casas decimais e formatação brasileira.

## Nota de implementação

O site estático e o Flutter mantêm implementações nativas próprias, mas compartilham a paleta, hierarquia, espaçamento, estados e comportamento responsivo descritos acima. As mudanças são de apresentação e organização; os contratos REST permanecem os mesmos.
