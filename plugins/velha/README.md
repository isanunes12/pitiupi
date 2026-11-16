# Plugin Jogo da velha

Jogo da velha entre duas pessoas da rede, com **salas abertas**: alguém abre uma sala, ela aparece para todos e o primeiro que entrar joga. Não há convite nem aceite.

## Como jogar

1. Abra **Plugins → Jogo da velha**. A aba mostra as salas abertas na rede.
2. Clique em **Abrir minha sala** e espere, ou escolha uma sala da lista e clique em **Entrar na sala** (duplo clique também entra).
3. A partida começa assim que alguém entra. O dono da sala joga com **X** e começa.
4. No fim, **Jogar de novo** começa outra partida com a mesma pessoa, sem aceite, e **Sair da sala** volta para a lista.

Fechar a aba fecha a sala ou avisa o adversário. Se a pessoa sumir da lista de presença, a partida é encerrada.

## Protocolo

O plugin usa os dois transportes do Pitiupi, cada um para o que serve:

- **UDP multicast** para anunciar a sala: é um aviso para todos, sem destinatário certo, e perder um anúncio não tem problema porque ele é repetido a cada 2 segundos.
- **TCP** para entrar na sala e jogar: é uma conversa entre duas pessoas em que nenhuma mensagem pode se perder nem chegar fora de ordem.

| Mensagem | Transporte | Quando | Campos próprios |
| --- | --- | --- | --- |
| `VelhaRoomMessage` | UDP multicast | Sala aberta (repetido) ou fechada | `open` |
| `VelhaJoinMessage` | TCP | Pedido para entrar na sala ou jogar de novo | — |
| `VelhaReplyMessage` | TCP | Resposta ao pedido | `accepted`, `reason` |
| `VelhaMoveMessage` | TCP | Uma jogada | `cell` (0 a 8), `moveNumber` (1 a 9) |
| `VelhaEndMessage` | TCP | Desistência ou saída da sala | `reason` |

Todas levam `gameId` (UUID da sala e da partida), `senderName` e `senderIp`, porque o núcleo entrega só o objeto, sem o endereço de origem.

Regras que valem explicar:

- **Só o primeiro entra.** O dono aceita o primeiro `VelhaJoinMessage` que chegar, anuncia a sala como fechada e recusa os seguintes.
- **Sala some sozinha.** Quem não recebe anúncio de uma sala por 7 segundos tira ela da lista. Cobre o caso do aviso de fechamento se perder (UDP) ou do dono fechar o programa.
- **O próprio anúncio volta.** Multicast também chega a quem enviou; o plugin descarta o anúncio da própria sala.
- **Vitória e empate não têm mensagem.** Os dois lados aplicam as mesmas jogadas no mesmo tabuleiro e chegam ao mesmo resultado.
- **Jogada validada.** Quem recebe confere o `gameId`, se é a vez do adversário, se `moveNumber` é o próximo da sequência e se a casa está livre; fora disso descarta.
- **Pedidos cruzados.** Se os dois pedirem ao mesmo tempo (os dois clicam em Jogar de novo), vale o pedido de menor `gameId`.

## Arquivos

| Arquivo | Papel |
| --- | --- |
| `src/META-INF/services/pitiupi.models.Plugin` | Diz ao Java o nome da classe do plugin |
| `src/pitiupi/velha/net/` | As cinco mensagens e a base `VelhaMessage` |
| `src/pitiupi/velha/control/Board.java` | Tabuleiro e linhas vencedoras, sem rede nem tela |
| `src/pitiupi/velha/control/VelhaController.java` | Fases, lista de salas e o protocolo |
| `src/pitiupi/velha/control/Room.java` | Uma sala aberta por outra pessoa |
| `src/pitiupi/velha/GUI/` | `VelhaPanel` (aba) e `BoardView` (desenho do tabuleiro) |
| `src/pitiupi/velha/plugin/VelhaPlugin.java` | Liga tudo ao núcleo e envia pela rede |

## Compilar

Na raiz do clone, `scripts\pitiupi.cmd run --all` (Windows) ou `./scripts/pitiupi.sh run --all` (Linux) compila este módulo junto com os outros do `plugins.registry.json`.

Para testar são necessários dois computadores na mesma rede e na mesma porta: duas janelas no mesmo PC usam portas diferentes e não ficam na mesma sala.
