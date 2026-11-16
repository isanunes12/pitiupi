package pitiupi.velha.net;

import pitiupi.models.Messages.Message;

/**
 * Base das mensagens do jogo da velha.
 *
 * <p>O núcleo entrega só o objeto, sem dizer de qual IP ele veio. Por isso toda
 * mensagem carrega o nome e o IP de quem enviou, além do id da partida.</p>
 */
public abstract class VelhaMessage extends Message {

    private static final long serialVersionUID = 1L;

    private String gameId;
    private String senderName;
    private String senderIp;

    protected VelhaMessage() {
    }

    protected VelhaMessage(String gameId, String senderName, String senderIp) {
        this.gameId = gameId;
        this.senderName = senderName;
        this.senderIp = senderIp;
    }

    /** Id da sala e da partida (UUID). Mensagem de outra partida é ignorada. */
    public String getGameId() {
        return gameId;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getSenderIp() {
        return senderIp;
    }
}
