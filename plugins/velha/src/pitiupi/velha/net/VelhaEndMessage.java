package pitiupi.velha.net;

/**
 * Encerra a partida fora do fim normal (vitória ou empate): desistência ou saída da sala.
 *
 * <p>Vitória e empate não precisam de mensagem: os dois lados aplicam as mesmas
 * jogadas no mesmo tabuleiro e chegam ao mesmo resultado.</p>
 */
public class VelhaEndMessage extends VelhaMessage {

    private static final long serialVersionUID = 1L;

    private VelhaEndReason reason;

    public VelhaEndMessage() {
    }

    public VelhaEndMessage(String gameId, String senderName, String senderIp, VelhaEndReason reason) {
        super(gameId, senderName, senderIp);
        this.reason = reason;
    }

    public VelhaEndReason getReason() {
        return reason;
    }
}
