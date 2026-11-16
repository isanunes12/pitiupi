package pitiupi.velha.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.application.peeractions.PeerTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;
import pitiupi.net.tcp.TcpAttemptListener;
import pitiupi.net.tcp.TcpConnection;
import pitiupi.presentation.shell.LocalNetworkInfo;
import pitiupi.presentation.shell.PeerIpv4;
import pitiupi.velha.GUI.VelhaPanel;
import pitiupi.velha.control.VelhaController;
import pitiupi.velha.control.VelhaSender;
import pitiupi.velha.net.VelhaEndMessage;
import pitiupi.velha.net.VelhaJoinMessage;
import pitiupi.velha.net.VelhaMessage;
import pitiupi.velha.net.VelhaMoveMessage;
import pitiupi.velha.net.VelhaReplyMessage;
import pitiupi.velha.net.VelhaRoomMessage;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.util.List;

/**
 * Jogo da velha com salas abertas: alguém abre uma sala, ela aparece para
 * todos na rede e o primeiro que entrar joga.
 *
 * <p>Esta classe só liga as peças ao núcleo: cria o controller e o painel,
 * registra a ação da lista lateral, envia pela rede e repassa o que chega.</p>
 */
public class VelhaPlugin extends Plugin implements VelhaSender {

    /** De quanto em quanto tempo a sala aberta é anunciada de novo. */
    private static final int ANNOUNCE_INTERVAL_MS = 2000;

    private ConnectionManager connectionManager;
    private PluginWorkspace workspace;
    private VelhaController controller;
    private VelhaPanel panel;

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.connectionManager = connectionManager;
        this.workspace = workspace;
        this.controller = new VelhaController(
                this,
                connectionManager::getUserName,
                LocalNetworkInfo::bestLocalIpv4,
                System::currentTimeMillis
        );
        this.panel = new VelhaPanel(
                controller,
                () -> workspace.openOrFocusTab(getIdentifier(), getDisplayName())
        );
        controller.setView(panel);
        bindShellWorkspaceContent(panel);
        installPeerActions(workspace.peerActions());

        // A lista de presença muda fora da thread do Swing.
        connectionManager.addPeerListener(peers ->
                SwingUtilities.invokeLater(() -> panel.peersChanged(peers)));

        // O Timer do Swing já dispara na thread da tela.
        new Timer(ANNOUNCE_INTERVAL_MS, e -> controller.tick()).start();
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "velha.peer",
                "Ver sala de jogo da velha desta pessoa",
                PeerActionTarget.SINGLE_PEER,
                target -> {
                    workspace.openOrFocusTab(getIdentifier(), getDisplayName());
                    // A ação só mostra a sala. A entrada sai no botão Entrar na sala.
                    if (target instanceof PeerTarget.Single single && !single.peer().isSelf()) {
                        single.peer().getInetAddress().ifPresent(address ->
                                panel.selectRoomOf(PeerIpv4.fromInetAddress(address)));
                    }
                }
        ));
    }

    // ------------------------------------------------------------------
    // VelhaSender: como as mensagens saem
    // ------------------------------------------------------------------

    /**
     * Para uma pessoa: TCP público do núcleo, fora da thread do Swing. Abrir a
     * conexão pode levar até 5 segundos e a janela não pode travar esperando.
     */
    @Override
    public void send(VelhaMessage message, String ip, Runnable onFailure) {
        connectionManager.sendTcpAsync(message, ip, new TcpAttemptListener() {
            @Override
            public void onConnecting(String endpoint) {
            }

            @Override
            public void onSuccess(TcpConnection connection) {
            }

            @Override
            public void onFailure(String reason) {
                onFailure.run();
            }
        });
    }

    /** Para todos: UDP multicast. Um datagrama só, sem conexão e sem garantia de entrega. */
    @Override
    public void broadcast(VelhaMessage message) {
        MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), TransportMode.MULTICAST_UDP);
    }

    // ------------------------------------------------------------------
    // Como as mensagens chegam
    // ------------------------------------------------------------------

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(
                VelhaRoomMessage.class,
                VelhaJoinMessage.class,
                VelhaReplyMessage.class,
                VelhaMoveMessage.class,
                VelhaEndMessage.class
        );
    }

    @Override
    public void receiveMessage(Message message) {
        // O núcleo chama isto na thread que leu o socket; a tela só muda na thread do Swing.
        if (message instanceof VelhaMessage velha) {
            SwingUtilities.invokeLater(() -> controller.receive(velha));
        }
    }

    @Override
    public void onShellTabClosed() {
        // Fechar a aba fecha a sala ou avisa o adversário, que não fica esperando.
        controller.leave();
    }

    @Override
    public String getName() {
        return "Velha";
    }

    @Override
    public String getDisplayName() {
        return "Jogo da velha";
    }

    @Override
    public String getAuthor() {
        return "Isa Nunes";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public String getIdentifier() {
        return "pitiupi.velha";
    }
}
