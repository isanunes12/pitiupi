package pitiupi.velha.net;

/**
 * Pedido para entrar em uma sala (ou para jogar de novo com o mesmo adversário).
 *
 * <p>Vai por TCP, direto para o dono da sala. Quem recebe e aceita joga com X
 * e começa; quem pediu joga com O.</p>
 */
public class VelhaJoinMessage extends VelhaMessage {

    private static final long serialVersionUID = 1L;

    public VelhaJoinMessage() {
    }

    public VelhaJoinMessage(String roomId, String senderName, String senderIp) {
        super(roomId, senderName, senderIp);
    }
}
