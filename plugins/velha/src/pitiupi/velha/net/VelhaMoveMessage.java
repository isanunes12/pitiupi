package pitiupi.velha.net;

/**
 * Uma jogada: a casa (0 a 8, da esquerda para a direita, de cima para baixo)
 * e o número da jogada na partida (1 a 9).
 *
 * <p>O número da jogada serve para descartar uma mensagem repetida ou fora de ordem.</p>
 */
public class VelhaMoveMessage extends VelhaMessage {

    private static final long serialVersionUID = 1L;

    private int cell;
    private int moveNumber;

    public VelhaMoveMessage() {
    }

    public VelhaMoveMessage(String gameId, String senderName, String senderIp, int cell, int moveNumber) {
        super(gameId, senderName, senderIp);
        this.cell = cell;
        this.moveNumber = moveNumber;
    }

    public int getCell() {
        return cell;
    }

    public int getMoveNumber() {
        return moveNumber;
    }
}
