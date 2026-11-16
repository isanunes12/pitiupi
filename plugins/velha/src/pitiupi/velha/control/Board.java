package pitiupi.velha.control;

import java.util.Arrays;

/**
 * Tabuleiro 3x3. Casas numeradas de 0 a 8:
 *
 * <pre>
 * 0 | 1 | 2
 * 3 | 4 | 5
 * 6 | 7 | 8
 * </pre>
 *
 * Não conhece rede nem tela: só guarda as marcas e diz quem ganhou.
 */
public final class Board {

    public static final char EMPTY = ' ';
    public static final char X = 'X';
    public static final char O = 'O';

    private static final int[][] LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
            {0, 4, 8}, {2, 4, 6}
    };

    private final char[] cells = new char[9];

    public Board() {
        Arrays.fill(cells, EMPTY);
    }

    public char get(int cell) {
        return cells[cell];
    }

    public boolean isFree(int cell) {
        return cell >= 0 && cell < cells.length && cells[cell] == EMPTY;
    }

    /**
     * Marca a casa.
     *
     * @return {@code false} se a casa não existe ou já está ocupada.
     */
    public boolean place(int cell, char mark) {
        if (!isFree(cell) || (mark != X && mark != O)) {
            return false;
        }
        cells[cell] = mark;
        return true;
    }

    /** Quantas jogadas já foram feitas. */
    public int count() {
        int filled = 0;
        for (char c : cells) {
            if (c != EMPTY) {
                filled++;
            }
        }
        return filled;
    }

    public boolean isFull() {
        return count() == cells.length;
    }

    /**
     * As três casas da linha vencedora, ou {@code null} se ninguém fechou linha.
     */
    public int[] winningLine() {
        for (int[] line : LINES) {
            char first = cells[line[0]];
            if (first != EMPTY && first == cells[line[1]] && first == cells[line[2]]) {
                return line.clone();
            }
        }
        return null;
    }

    /** {@link #X}, {@link #O} ou {@link #EMPTY} se ainda não há vencedor. */
    public char winner() {
        int[] line = winningLine();
        return line == null ? EMPTY : cells[line[0]];
    }
}
