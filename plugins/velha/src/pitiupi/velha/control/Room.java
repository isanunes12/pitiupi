package pitiupi.velha.control;

/**
 * Uma sala aberta por outra pessoa, como aparece na lista.
 *
 * @param id       id da sala (será o id da partida)
 * @param hostName nome de quem abriu
 * @param hostIp   IP de quem abriu (para onde vai o pedido de entrada)
 */
public record Room(String id, String hostName, String hostIp) {

    @Override
    public String toString() {
        return "Sala de " + hostName + " (" + hostIp + ")";
    }
}
