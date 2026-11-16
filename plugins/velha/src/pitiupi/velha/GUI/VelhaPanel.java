package pitiupi.velha.GUI;

import pitiupi.models.User;
import pitiupi.presentation.shell.PeerIpv4;
import pitiupi.velha.control.Board;
import pitiupi.velha.control.Room;
import pitiupi.velha.control.VelhaController;
import pitiupi.velha.control.VelhaController.Phase;
import pitiupi.velha.control.VelhaView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tela do jogo da velha dentro da aba central.
 *
 * <p>Fora de uma partida mostra a lista de salas abertas; durante a partida,
 * o tabuleiro. Não guarda regra: lê o estado do {@link VelhaController} em
 * {@link #stateChanged()} e repassa os cliques para ele.</p>
 */
public final class VelhaPanel extends JPanel implements VelhaView {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG_CHARS = 20_000;
    private static final String CARD_ROOMS = "salas";
    private static final String CARD_BOARD = "tabuleiro";

    private final VelhaController controller;
    private final Runnable bringTabToFront;

    private final JLabel statusLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel detailLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel scoreLabel = new JLabel(" ", SwingConstants.CENTER);

    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final DefaultListModel<Room> roomModel = new DefaultListModel<>();
    private final JList<Room> roomList = new JList<>(roomModel);
    private final BoardView boardView = new BoardView();

    private final JButton joinButton = new JButton("Entrar na sala");
    private final JButton openButton = new JButton("Abrir minha sala");
    private final JButton closeButton = new JButton("Fechar minha sala");
    private final JButton cancelButton = new JButton("Cancelar");
    private final JButton resignButton = new JButton("Desistir");
    private final JButton againButton = new JButton("Jogar de novo");
    private final JButton leaveButton = new JButton("Sair da sala");

    private final JTextArea logArea = new JTextArea(5, 40);

    /**
     * @param bringTabToFront abre ou foca a aba do jogo na janela principal (alguém entrou na sala).
     */
    public VelhaPanel(VelhaController controller, Runnable bringTabToFront) {
        this.controller = controller;
        this.bringTabToFront = bringTabToFront;

        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(10, 12, 10, 12));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        boardView.setOnCellClicked(controller::play);
        joinButton.addActionListener(e -> joinSelectedRoom());
        openButton.addActionListener(e -> controller.openRoom());
        closeButton.addActionListener(e -> controller.closeRoom());
        cancelButton.addActionListener(e -> controller.cancelJoin());
        resignButton.addActionListener(e -> controller.resign());
        againButton.addActionListener(e -> controller.playAgain());
        leaveButton.addActionListener(e -> controller.leave());

        stateChanged();
    }

    // ------------------------------------------------------------------
    // Chamado pelo plugin
    // ------------------------------------------------------------------

    /** Repassa ao controller quem está na rede agora (para notar quando o adversário some). */
    public void peersChanged(List<User> peers) {
        Set<String> onlineIps = new HashSet<>();
        for (User user : peers) {
            if (!user.isBroadcastTarget() && user.getAddress() != null) {
                onlineIps.add(PeerIpv4.fromInetAddress(user.getAddress()));
            }
        }
        controller.onPeersChanged(onlineIps);
    }

    /** Deixa selecionada a sala aberta por este IP, se houver (ação da lista lateral). Não entra na sala. */
    public void selectRoomOf(String hostIp) {
        for (int i = 0; i < roomModel.getSize(); i++) {
            if (roomModel.getElementAt(i).hostIp().equals(hostIp)) {
                roomList.setSelectedIndex(i);
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // VelhaView
    // ------------------------------------------------------------------

    @Override
    public void stateChanged() {
        Phase phase = controller.getPhase();
        refreshRooms();
        statusLabel.setText(statusText(phase));
        detailLabel.setText(detailText(phase));
        scoreLabel.setText(scoreText());

        boolean lobby = phase == Phase.IDLE || phase == Phase.HOSTING;
        cards.show(center, lobby ? CARD_ROOMS : CARD_BOARD);
        joinButton.setVisible(lobby);
        joinButton.setEnabled(roomList.getSelectedValue() != null);
        openButton.setVisible(phase == Phase.IDLE);
        closeButton.setVisible(phase == Phase.HOSTING);
        cancelButton.setVisible(phase == Phase.JOINING);
        resignButton.setVisible(phase == Phase.PLAYING);
        againButton.setVisible(phase == Phase.FINISHED && !controller.isOpponentGone());
        leaveButton.setVisible(phase == Phase.FINISHED);

        boardView.show(controller.getBoard(), controller.isMyTurn());
        revalidate();
        repaint();
    }

    @Override
    public void opponentJoined(String name) {
        bringTabToFront.run();
    }

    @Override
    public void log(String line) {
        logArea.append("[" + LocalTime.now().format(CLOCK) + "] " + line + "\n");
        int excess = logArea.getDocument().getLength() - MAX_LOG_CHARS;
        if (excess > 0) {
            logArea.replaceRange("", 0, excess);
        }
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    // ------------------------------------------------------------------
    // Montagem
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 18f));
        Color muted = UIManager.getColor("Label.disabledForeground");
        if (muted != null) {
            detailLabel.setForeground(muted);
        }

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        for (JLabel label : List.of(statusLabel, detailLabel, scoreLabel)) {
            label.setAlignmentX(Component.CENTER_ALIGNMENT);
            header.add(label);
            header.add(Box.createVerticalStrut(3));
        }
        return header;
    }

    private JPanel buildCenter() {
        roomList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roomList.setFont(roomList.getFont().deriveFont(15f));
        roomList.setFixedCellHeight(32);
        roomList.addListSelectionListener(e -> joinButton.setEnabled(roomList.getSelectedValue() != null));
        roomList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    joinSelectedRoom();
                }
            }
        });
        JScrollPane roomScroll = new JScrollPane(roomList);
        roomScroll.setBorder(BorderFactory.createTitledBorder("Salas abertas na rede"));

        center.add(roomScroll, CARD_ROOMS);
        center.add(boardView, CARD_BOARD);
        return center;
    }

    private JPanel buildFooter() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        actions.add(joinButton);
        actions.add(openButton);
        actions.add(closeButton);
        actions.add(cancelButton);
        actions.add(resignButton);
        actions.add(againButton);
        actions.add(leaveButton);

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Mensagens na rede"));

        JPanel footer = new JPanel(new BorderLayout(0, 8));
        footer.add(actions, BorderLayout.NORTH);
        footer.add(logScroll, BorderLayout.CENTER);
        return footer;
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    private void joinSelectedRoom() {
        Room room = roomList.getSelectedValue();
        if (room != null) {
            controller.joinRoom(room);
        }
    }

    /** Copia a lista de salas do controller, mantendo a sala que estava selecionada. */
    private void refreshRooms() {
        List<Room> rooms = controller.getRooms();
        boolean same = rooms.size() == roomModel.getSize();
        for (int i = 0; same && i < rooms.size(); i++) {
            same = rooms.get(i).equals(roomModel.getElementAt(i));
        }
        if (same) {
            return;
        }
        Room selected = roomList.getSelectedValue();
        roomModel.clear();
        for (Room room : rooms) {
            roomModel.addElement(room);
        }
        if (selected != null) {
            for (int i = 0; i < roomModel.getSize(); i++) {
                if (roomModel.getElementAt(i).id().equals(selected.id())) {
                    roomList.setSelectedIndex(i);
                }
            }
        }
        if (roomList.getSelectedValue() == null && roomModel.getSize() == 1) {
            roomList.setSelectedIndex(0);
        }
    }

    private String statusText(Phase phase) {
        String opponent = controller.getOpponentName();
        return switch (phase) {
            case IDLE -> roomModel.isEmpty() ? "Nenhuma sala aberta" : "Escolha uma sala para entrar";
            case HOSTING -> "Sua sala está aberta";
            case JOINING -> "Entrando na sala de " + opponent + "…";
            case PLAYING -> controller.isMyTurn() ? "Sua vez" : "Vez de " + opponent;
            case FINISHED -> switch (controller.getOutcome()) {
                case WON -> "Você venceu!";
                case LOST -> opponent + " venceu";
                case DRAW -> "Deu velha (empate)";
                case OPPONENT_RESIGNED -> opponent + " desistiu — vitória sua";
                case RESIGNED -> "Você desistiu";
                case OPPONENT_LEFT -> opponent + " saiu da partida";
                case CONNECTION_LOST -> "Conexão com " + opponent + " perdida";
                case NONE -> "Partida encerrada";
            };
        };
    }

    private String detailText(Phase phase) {
        return switch (phase) {
            case IDLE -> {
                String notice = controller.getNotice();
                if (!notice.isBlank()) {
                    yield notice;
                }
                yield roomModel.isEmpty()
                        ? "Abra a sua sala: ela aparece para todos na rede e o primeiro que entrar joga com você."
                        : "Entre em uma sala da lista ou abra a sua.";
            }
            case HOSTING -> "Esperando alguém entrar. Você joga com X e começa.";
            case JOINING -> "Pedido enviado por TCP para " + controller.getOpponentIp() + ".";
            case PLAYING, FINISHED -> "Você é " + markName(controller.getMyMark())
                    + " · " + controller.getOpponentName() + " (" + controller.getOpponentIp() + ") é "
                    + markName(controller.getMyMark() == Board.X ? Board.O : Board.X)
                    + (phase == Phase.FINISHED && controller.isOpponentGone()
                    && controller.getOutcome() != VelhaController.Outcome.OPPONENT_LEFT
                    ? " · " + controller.getOpponentName() + " saiu da sala" : "");
        };
    }

    private String scoreText() {
        Phase phase = controller.getPhase();
        int total = controller.getWins() + controller.getLosses() + controller.getDraws();
        if (total == 0 || phase == Phase.IDLE || phase == Phase.HOSTING) {
            return " ";
        }
        return "Placar — você " + controller.getWins() + " × " + controller.getLosses() + " "
                + controller.getOpponentName() + " · empates " + controller.getDraws();
    }

    private static String markName(char mark) {
        return mark == Board.X ? "X" : "O";
    }
}
