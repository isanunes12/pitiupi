package pitiupi.velha.control;

import pitiupi.velha.net.VelhaMessage;

/**
 * Como o controller manda mensagens.
 *
 * <p>O plugin implementa isto com a rede do núcleo. Separar a interface deixa
 * a regra do jogo testável sem rede.</p>
 */
public interface VelhaSender {

    /**
     * Envia para uma pessoa, por TCP.
     *
     * @param onFailure chamado na thread do Swing se a mensagem não pôde ser enviada.
     */
    void send(VelhaMessage message, String ip, Runnable onFailure);

    /** Envia para todos que estão na rede, por UDP multicast. Sem garantia de entrega. */
    void broadcast(VelhaMessage message);
}
