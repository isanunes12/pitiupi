package pitiupi.velha.net;

/**
 * Resposta a um {@link VelhaJoinMessage}: entrou na sala, ou não (com o motivo).
 */
public class VelhaReplyMessage extends VelhaMessage {

    private static final long serialVersionUID = 1L;

    private boolean accepted;
    private String reason;

    public VelhaReplyMessage() {
    }

    public VelhaReplyMessage(String gameId, String senderName, String senderIp, boolean accepted, String reason) {
        super(gameId, senderName, senderIp);
        this.accepted = accepted;
        this.reason = reason;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getReason() {
        return reason;
    }
}
