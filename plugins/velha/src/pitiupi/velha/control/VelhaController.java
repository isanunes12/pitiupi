package pitiupi.velha.control;

import pitiupi.velha.net.VelhaEndMessage;
import pitiupi.velha.net.VelhaEndReason;
import pitiupi.velha.net.VelhaJoinMessage;
import pitiupi.velha.net.VelhaMessage;
import pitiupi.velha.net.VelhaMoveMessage;
import pitiupi.velha.net.VelhaReplyMessage;
import pitiupi.velha.net.VelhaRoomMessage;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Regra da partida e do protocolo, sem desenhar nada.
 *
 * <p>Protocolo:</p>
 * <ol>
 *   <li>A abre uma sala e a anuncia para todos com {@link VelhaRoomMessage}
 *       (UDP multicast), repetindo o anúncio enquanto a sala estiver aberta.</li>
 *   <li>B vê a sala na lista e envia {@link VelhaJoinMessage} para A (TCP).</li>
 *   <li>A responde {@link VelhaReplyMessage} (TCP) e anuncia que a sala fechou.
 *       Só o primeiro que pedir entra; os outros recebem a recusa.</li>
 *   <li>A é X e começa. A cada jogada, quem jogou envia {@link VelhaMoveMessage} (TCP).</li>
 *   <li>Vitória e empate cada lado calcula sozinho. Desistência e saída da sala
 *       usam {@link VelhaEndMessage} (TCP).</li>
 * </ol>
 *
 * <p>Todos os métodos devem ser chamados na thread do Swing.</p>
 */
public final class VelhaController {

    public enum Phase {
        /** Sem partida: veja as salas abertas ou abra a sua. */
        IDLE,
        /** Sua sala está aberta, esperando alguém entrar. */
        HOSTING,
        /** Pedido de entrada enviado, esperando a resposta do dono da sala. */
        JOINING,
        PLAYING,
        /** Partida encerrada: o tabuleiro continua na tela até jogar de novo ou sair. */
        FINISHED
    }

    public enum Outcome {
        NONE,
        WON,
        LOST,
        DRAW,
        OPPONENT_RESIGNED,
        RESIGNED,
        OPPONENT_LEFT,
        CONNECTION_LOST
    }

    /** Sem anúncio por este tempo, a sala some da lista (o dono saiu ou o aviso de fechamento se perdeu). */
    public static final long ROOM_TIMEOUT_MS = 7000;

    /**
     * Quanto tempo seguido o adversário precisa ficar fora da lista de presença
     * para a partida ser encerrada. Um sumiço curto não conta: a lista do núcleo
     * pode piscar (por exemplo, quando os relógios dos dois computadores diferem).
     */
    public static final long PRESENCE_GRACE_MS = 20000;

    /** De quantos em quantos {@link #tick()} uma mensagem sem confirmação é reenviada. */
    private static final int RESEND_EVERY_TICKS = 2;

    /** Quantas vezes a última jogada da partida é reenviada depois do fim. */
    private static final int FINAL_MOVE_RESENDS = 3;

    private static final VelhaView NO_VIEW = new VelhaView() {
        @Override
        public void stateChanged() {
        }

        @Override
        public void opponentJoined(String name) {
        }

        @Override
        public void log(String line) {
        }
    };

    private final VelhaSender sender;
    private final Supplier<String> localName;
    private final Supplier<String> localIp;
    private final LongSupplier clock;
    private VelhaView view = NO_VIEW;

    private Phase phase = Phase.IDLE;
    private Outcome outcome = Outcome.NONE;
    private Board board = new Board();
    private String gameId;
    private String opponentName = "";
    private String opponentIp = "";
    private char myMark = Board.X;
    private boolean myTurn;
    private String notice = "";
    private boolean opponentSeenOnline;
    private boolean opponentGone;
    /** Instante em que o adversário sumiu da lista de presença, ou -1 se ele está nela. */
    private long opponentAbsentSince = -1;
    /** Mensagem ainda sem confirmação de que chegou; é reenviada em {@link #tick()}. */
    private VelhaMessage pending;
    private int finalResendsLeft;
    private int ticksSinceResend;

    /** Salas dos outros, na ordem em que apareceram, com o instante do último anúncio. */
    private final Map<String, Room> rooms = new LinkedHashMap<>();
    private final Map<String, Long> roomLastSeen = new LinkedHashMap<>();

    private String scoreIp = "";
    private int wins;
    private int losses;
    private int draws;

    /**
     * @param clock relógio em milissegundos (no programa, {@code System::currentTimeMillis}).
     */
    public VelhaController(VelhaSender sender, Supplier<String> localName, Supplier<String> localIp,
                           LongSupplier clock) {
        this.sender = sender;
        this.localName = localName;
        this.localIp = localIp;
        this.clock = clock;
    }

    public void setView(VelhaView view) {
        this.view = view != null ? view : NO_VIEW;
    }

    // ------------------------------------------------------------------
    // Ações de quem está na tela
    // ------------------------------------------------------------------

    /** Abre uma sala e a anuncia para todos. Qualquer pessoa da rede pode entrar. */
    public void openRoom() {
        if (phase != Phase.IDLE) {
            return;
        }
        gameId = UUID.randomUUID().toString();
        phase = Phase.HOSTING;
        notice = "";
        view.log("→ todos: sala aberta [UDP multicast, repetido enquanto estiver aberta]");
        announceRoom(true);
        view.stateChanged();
    }

    public void closeRoom() {
        if (phase != Phase.HOSTING) {
            return;
        }
        view.log("→ todos: sala fechada [UDP multicast]");
        announceRoom(false);
        backToIdle("");
    }

    /** Entra em uma sala da lista. Se a sua própria sala estiver aberta, ela é fechada antes. */
    public void joinRoom(Room room) {
        if (room == null || (phase != Phase.IDLE && phase != Phase.HOSTING)) {
            return;
        }
        if (phase == Phase.HOSTING) {
            view.log("→ todos: sala fechada [UDP multicast]");
            announceRoom(false);
        }
        forgetRoom(room.id());
        setOpponent(room.hostName(), room.hostIp());
        gameId = room.id();
        board = new Board();
        outcome = Outcome.NONE;
        phase = Phase.JOINING;
        notice = "";
        pending = new VelhaJoinMessage(gameId, localName.get(), localIp.get());
        send(pending, "pedido para entrar na sala");
        view.stateChanged();
    }

    /** Desiste de entrar enquanto a resposta não chegou. */
    public void cancelJoin() {
        if (phase != Phase.JOINING) {
            return;
        }
        send(new VelhaEndMessage(gameId, localName.get(), localIp.get(), VelhaEndReason.LEFT), "saída da sala");
        backToIdle("");
    }

    /** Joga na casa (0 a 8). Ignora o clique se não for a sua vez ou a casa estiver ocupada. */
    public void play(int cell) {
        if (phase != Phase.PLAYING || !myTurn || !board.isFree(cell)) {
            return;
        }
        board.place(cell, myMark);
        myTurn = false;
        pending = new VelhaMoveMessage(gameId, localName.get(), localIp.get(), cell, board.count());
        finalResendsLeft = FINAL_MOVE_RESENDS;
        send(pending, "jogada " + board.count() + " na casa " + (cell + 1));
        checkEndOfGame();
        view.stateChanged();
    }

    public void resign() {
        if (phase != Phase.PLAYING) {
            return;
        }
        send(new VelhaEndMessage(gameId, localName.get(), localIp.get(), VelhaEndReason.RESIGNED), "desistência");
        finish(Outcome.RESIGNED);
        view.stateChanged();
    }

    /**
     * Jogar de novo com o mesmo adversário, que continua na sala. Não precisa de
     * aceite: se a pessoa ainda estiver lá, a nova partida começa (ela de X).
     */
    public void playAgain() {
        if (phase != Phase.FINISHED || opponentGone) {
            return;
        }
        gameId = UUID.randomUUID().toString();
        board = new Board();
        outcome = Outcome.NONE;
        phase = Phase.JOINING;
        notice = "";
        pending = new VelhaJoinMessage(gameId, localName.get(), localIp.get());
        send(pending, "pedido para jogar de novo");
        view.stateChanged();
    }

    /**
     * Sai do que estiver em andamento e volta à lista de salas. O plugin também
     * chama isto quando a aba do jogo é fechada, para ninguém ficar esperando.
     */
    public void leave() {
        switch (phase) {
            case HOSTING -> {
                view.log("→ todos: sala fechada [UDP multicast]");
                announceRoom(false);
            }
            case JOINING, PLAYING -> send(
                    new VelhaEndMessage(gameId, localName.get(), localIp.get(), VelhaEndReason.LEFT),
                    "saída da sala");
            case FINISHED -> {
                if (!opponentGone) {
                    send(new VelhaEndMessage(gameId, localName.get(), localIp.get(), VelhaEndReason.LEFT),
                            "saída da sala");
                }
            }
            default -> {
            }
        }
        backToIdle("");
    }

    /**
     * Chamado pelo plugin a cada poucos segundos: repete o anúncio da sala aberta
     * e tira da lista as salas que pararam de ser anunciadas.
     */
    public void tick() {
        if (phase == Phase.HOSTING) {
            announceRoom(true);
        }
        resendPending();
        long now = clock.getAsLong();
        if (opponentAbsentSince >= 0 && now - opponentAbsentSince > PRESENCE_GRACE_MS) {
            opponentAbsentSince = -1;
            opponentLeftNetwork();
        }
        boolean changed = false;
        Iterator<Map.Entry<String, Long>> it = roomLastSeen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > ROOM_TIMEOUT_MS) {
                rooms.remove(entry.getKey());
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            view.stateChanged();
        }
    }

    // ------------------------------------------------------------------
    // Mensagens que chegam da rede
    // ------------------------------------------------------------------

    /** Entrega uma mensagem recebida ao método certo, pelo tipo. */
    public void receive(VelhaMessage message) {
        if (message instanceof VelhaRoomMessage room) {
            onRoom(room);
        } else if (message instanceof VelhaJoinMessage join) {
            onJoin(join);
        } else if (message instanceof VelhaReplyMessage reply) {
            onReply(reply);
        } else if (message instanceof VelhaMoveMessage move) {
            onMove(move);
        } else if (message instanceof VelhaEndMessage end) {
            onEnd(end);
        }
    }

    public void onRoom(VelhaRoomMessage msg) {
        String roomId = msg.getGameId();
        String hostIp = msg.getSenderIp();
        if (roomId == null || hostIp == null || hostIp.isBlank()) {
            return;
        }
        // O multicast também volta para quem enviou: o anúncio da própria sala não entra na lista.
        if (roomId.equals(gameId) || hostIp.trim().equals(localIp.get())) {
            return;
        }
        String hostName = displayName(msg.getSenderName());
        if (!msg.isOpen()) {
            if (forgetRoom(roomId)) {
                view.log("← " + hostName + ": sala fechada [UDP multicast]");
                view.stateChanged();
            }
            return;
        }
        boolean isNew = !rooms.containsKey(roomId);
        rooms.put(roomId, new Room(roomId, hostName, hostIp.trim()));
        roomLastSeen.put(roomId, clock.getAsLong());
        if (isNew) {
            view.log("← " + hostName + ": sala aberta [UDP multicast]");
            view.stateChanged();
        }
    }

    public void onJoin(VelhaJoinMessage msg) {
        String fromIp = msg.getSenderIp();
        if (msg.getGameId() == null || fromIp == null || fromIp.isBlank()) {
            return;
        }
        fromIp = fromIp.trim();
        String fromName = displayName(msg.getSenderName());

        if (phase == Phase.HOSTING && msg.getGameId().equals(gameId)) {
            // O primeiro pedido que chega ocupa a sala.
            view.log("← " + fromName + ": pedido para entrar na sala [TCP]");
            setOpponent(fromName, fromIp);
            send(new VelhaReplyMessage(gameId, localName.get(), localIp.get(), true, null), "entrada aceita");
            view.log("→ todos: sala fechada [UDP multicast]");
            announceRoom(false);
            startGame(Board.X);
            view.opponentJoined(fromName);
            return;
        }
        if (phase == Phase.PLAYING && msg.getGameId().equals(gameId) && fromIp.equals(opponentIp)) {
            // Pedido repetido de quem já entrou: a resposta anterior pode ter se perdido. Responde de novo.
            sender.send(new VelhaReplyMessage(gameId, localName.get(), localIp.get(), true, null), fromIp, () -> {
            });
            return;
        }
        if (phase == Phase.FINISHED && !opponentGone && fromIp.equals(opponentIp)) {
            // O adversário quer jogar de novo e eu ainda estou na sala: começa outra partida.
            view.log("← " + fromName + ": pedido para jogar de novo [TCP]");
            gameId = msg.getGameId();
            send(new VelhaReplyMessage(gameId, localName.get(), localIp.get(), true, null), "entrada aceita");
            startGame(Board.X);
            return;
        }
        if (phase == Phase.JOINING && fromIp.equals(opponentIp)) {
            // Pedidos cruzados: cada um pediu para entrar na partida do outro ao mesmo tempo
            // (os dois clicaram em Jogar de novo, por exemplo). Vale o pedido de menor id;
            // os dois lados fazem a mesma comparação e chegam à mesma partida.
            if (msg.getGameId().compareTo(gameId) < 0) {
                view.log("← " + fromName + ": pedido cruzado [TCP]");
                gameId = msg.getGameId();
                send(new VelhaReplyMessage(gameId, localName.get(), localIp.get(), true, null), "entrada aceita");
                startGame(Board.X);
            }
            return;
        }
        // Sala já ocupada ou fechada: recusa sem mexer no que estiver em andamento.
        view.log("← " + fromName + ": pedido para entrar [TCP] — recusado, a sala não está mais aberta");
        sender.send(
                new VelhaReplyMessage(msg.getGameId(), localName.get(), localIp.get(), false,
                        "A sala não está mais aberta"),
                fromIp, () -> {
                });
    }

    public void onReply(VelhaReplyMessage msg) {
        if (phase != Phase.JOINING || !sameGame(msg)) {
            if (msg.isAccepted() && msg.getGameId() != null && msg.getSenderIp() != null
                    && !(phase == Phase.PLAYING && sameGame(msg))) {
                // Fui aceito em uma sala da qual já desisti: aviso o dono, que não fica esperando jogada.
                sender.send(
                        new VelhaEndMessage(msg.getGameId(), localName.get(), localIp.get(), VelhaEndReason.LEFT),
                        msg.getSenderIp(), () -> {
                        });
            }
            return;
        }
        if (msg.isAccepted()) {
            view.log("← " + opponentName + ": entrada aceita [TCP]");
            startGame(Board.O);
            return;
        }
        pending = null;
        view.log("← " + opponentName + ": entrada recusada [TCP]");
        String reason = msg.getReason();
        backToIdle(reason == null || reason.isBlank()
                ? "Não foi possível entrar na sala de " + opponentName + "."
                : reason + " (sala de " + opponentName + ").");
    }

    public void onMove(VelhaMoveMessage msg) {
        if (phase == Phase.JOINING && sameGame(msg) && msg.getMoveNumber() == 1) {
            // A primeira jogada do dono chegou antes da resposta (que se perdeu): fui aceito.
            view.log("← " + opponentName + ": entrada aceita (pela primeira jogada) [TCP]");
            startGame(Board.O);
        }
        if (phase != Phase.PLAYING || !sameGame(msg) || myTurn) {
            return;
        }
        // A jogada tem de ser a próxima da sequência e cair numa casa livre.
        if (msg.getMoveNumber() != board.count() + 1 || !board.isFree(msg.getCell())) {
            return;
        }
        board.place(msg.getCell(), opponentMark());
        myTurn = true;
        pending = null; // a jogada do adversário prova que a minha anterior chegou
        view.log("← " + opponentName + ": jogada " + msg.getMoveNumber() + " na casa " + (msg.getCell() + 1)
                + " [TCP]");
        checkEndOfGame();
        view.stateChanged();
    }

    public void onEnd(VelhaEndMessage msg) {
        if (!sameGame(msg) || msg.getReason() == null) {
            return;
        }
        if (phase == Phase.PLAYING) {
            if (msg.getReason() == VelhaEndReason.RESIGNED) {
                view.log("← " + opponentName + ": desistência [TCP]");
                finish(Outcome.OPPONENT_RESIGNED);
            } else {
                view.log("← " + opponentName + ": saída da sala [TCP]");
                finish(Outcome.OPPONENT_LEFT);
                opponentGone = true;
            }
            view.stateChanged();
        } else if (phase == Phase.FINISHED && msg.getReason() == VelhaEndReason.LEFT && !opponentGone) {
            view.log("← " + opponentName + ": saída da sala [TCP]");
            opponentGone = true;
            view.stateChanged();
        }
    }

    /**
     * A lista de presença mudou. Se o adversário estava na lista e sumiu, começa
     * a contar o tempo; a partida só é encerrada se ele continuar fora por
     * {@link #PRESENCE_GRACE_MS}.
     *
     * @param onlineIps IPs de quem está na lista agora.
     */
    public void onPeersChanged(Set<String> onlineIps) {
        if (phase == Phase.IDLE || phase == Phase.HOSTING) {
            return;
        }
        if (onlineIps.contains(opponentIp)) {
            opponentSeenOnline = true;
            opponentAbsentSince = -1;
            return;
        }
        // O IP da mensagem pode não ser o mesmo da lista (máquina com duas placas):
        // sem ter visto a pessoa na lista, não dá para concluir que ela saiu.
        if (opponentSeenOnline && !opponentGone && opponentAbsentSince < 0) {
            // Só começa a contar. Quem decide é o tick(), depois de PRESENCE_GRACE_MS seguidos.
            opponentAbsentSince = clock.getAsLong();
        }
    }

    private void opponentLeftNetwork() {
        if (phase == Phase.PLAYING) {
            view.log("! " + opponentName + " está fora da lista de presença há mais de "
                    + PRESENCE_GRACE_MS / 1000 + " s");
            finish(Outcome.OPPONENT_LEFT);
            opponentGone = true;
            view.stateChanged();
        } else if (phase == Phase.FINISHED) {
            opponentGone = true;
            view.stateChanged();
        } else if (phase == Phase.JOINING) {
            backToIdle(opponentName + " saiu da rede.");
        }
    }

    // ------------------------------------------------------------------
    // Leitura para a tela
    // ------------------------------------------------------------------

    public Phase getPhase() {
        return phase;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public Board getBoard() {
        return board;
    }

    /** Salas abertas por outras pessoas, na ordem em que apareceram. */
    public List<Room> getRooms() {
        return new ArrayList<>(rooms.values());
    }

    public String getOpponentName() {
        return opponentName;
    }

    public String getOpponentIp() {
        return opponentIp;
    }

    /** {@code true} depois que o adversário saiu da sala: não dá mais para jogar de novo com ele. */
    public boolean isOpponentGone() {
        return opponentGone;
    }

    public char getMyMark() {
        return myMark;
    }

    public boolean isMyTurn() {
        return phase == Phase.PLAYING && myTurn;
    }

    /** Aviso da última coisa que aconteceu fora de uma partida (entrada recusada, falha de conexão...). */
    public String getNotice() {
        return notice;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public int getDraws() {
        return draws;
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    /**
     * Reenvia o pedido de entrada ou a última jogada enquanto a resposta não vem.
     * Repetir não atrapalha: quem recebe um pedido repetido só responde de novo,
     * e uma jogada repetida é descartada pelo número da jogada.
     */
    private void resendPending() {
        if (pending == null || !sameGame(pending)) {
            return;
        }
        if (++ticksSinceResend < RESEND_EVERY_TICKS) {
            return;
        }
        ticksSinceResend = 0;
        boolean waiting = phase == Phase.JOINING || (phase == Phase.PLAYING && !myTurn);
        boolean lastMoveOfGame = phase == Phase.FINISHED && pending instanceof VelhaMoveMessage
                && !opponentGone && finalResendsLeft > 0;
        if (lastMoveOfGame) {
            finalResendsLeft--;
        }
        if (waiting || lastMoveOfGame) {
            sender.send(pending, opponentIp, () -> {
            });
        }
    }

    private void announceRoom(boolean open) {
        sender.broadcast(new VelhaRoomMessage(gameId, localName.get(), localIp.get(), open));
    }

    private boolean forgetRoom(String roomId) {
        roomLastSeen.remove(roomId);
        return rooms.remove(roomId) != null;
    }

    private void startGame(char mark) {
        board = new Board();
        myMark = mark;
        myTurn = mark == Board.X;
        outcome = Outcome.NONE;
        opponentGone = false;
        pending = null;
        phase = Phase.PLAYING;
        notice = "";
        view.stateChanged();
    }

    private void checkEndOfGame() {
        char winner = board.winner();
        if (winner != Board.EMPTY) {
            finish(winner == myMark ? Outcome.WON : Outcome.LOST);
        } else if (board.isFull()) {
            finish(Outcome.DRAW);
        }
    }

    private void finish(Outcome result) {
        phase = Phase.FINISHED;
        outcome = result;
        myTurn = false;
        switch (result) {
            case WON, OPPONENT_RESIGNED -> wins++;
            case LOST, RESIGNED -> losses++;
            case DRAW -> draws++;
            default -> {
            }
        }
    }

    private void backToIdle(String message) {
        phase = Phase.IDLE;
        outcome = Outcome.NONE;
        board = new Board();
        gameId = null;
        myTurn = false;
        opponentGone = false;
        opponentAbsentSince = -1;
        pending = null;
        notice = message;
        view.stateChanged();
    }

    private void setOpponent(String name, String ip) {
        opponentName = displayName(name);
        opponentIp = ip.trim();
        opponentSeenOnline = false;
        opponentGone = false;
        opponentAbsentSince = -1;
        if (!opponentIp.equals(scoreIp)) {
            scoreIp = opponentIp;
            wins = 0;
            losses = 0;
            draws = 0;
        }
    }

    private void send(VelhaMessage message, String description) {
        String sentGameId = gameId;
        view.log("→ " + opponentName + ": " + description + " [TCP]");
        sender.send(message, opponentIp, () -> onSendFailed(sentGameId));
    }

    private void onSendFailed(String sentGameId) {
        if (sentGameId == null || !sentGameId.equals(gameId)) {
            return;
        }
        view.log("! falha ao enviar para " + opponentIp + " [TCP]");
        if (phase == Phase.JOINING) {
            backToIdle("Não foi possível conectar com " + opponentName
                    + ". Confira se a pessoa está na lista e se o firewall libera TCP nesta porta.");
        } else if (phase == Phase.PLAYING) {
            finish(Outcome.CONNECTION_LOST);
            opponentGone = true;
            view.stateChanged();
        }
    }

    private boolean sameGame(VelhaMessage msg) {
        return gameId != null && gameId.equals(msg.getGameId());
    }

    private char opponentMark() {
        return myMark == Board.X ? Board.O : Board.X;
    }

    private static String displayName(String name) {
        return name == null || name.isBlank() ? "Alguém" : name.trim();
    }
}
