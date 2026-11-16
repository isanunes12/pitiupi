package pitiupi.velha.control;

/**
 * O que a tela precisa saber do controller.
 */
public interface VelhaView {

    /** Algo mudou (fase, tabuleiro, vez, lista de salas): redesenhe lendo o controller. */
    void stateChanged();

    /** Alguém entrou na sua sala: traga a aba do jogo para a frente. */
    void opponentJoined(String name);

    /** Linha para o registro de mensagens de rede. */
    void log(String line);
}
