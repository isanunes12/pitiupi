package pitiupi.velha.net;

/**
 * Anúncio de uma sala, enviado para todos por UDP multicast.
 *
 * <p>Enquanto a sala está aberta o dono repete o anúncio a cada poucos segundos,
 * porque UDP não garante entrega e quem abriu o programa depois precisa ver a sala.
 * Com {@code open == false} o anúncio avisa que a sala fechou. O id da partida
 * ({@link #getGameId()}) é o id da sala.</p>
 */
public class VelhaRoomMessage extends VelhaMessage {

    private static final long serialVersionUID = 1L;

    private boolean open;

    public VelhaRoomMessage() {
    }

    public VelhaRoomMessage(String roomId, String senderName, String senderIp, boolean open) {
        super(roomId, senderName, senderIp);
        this.open = open;
    }

    public boolean isOpen() {
        return open;
    }
}
