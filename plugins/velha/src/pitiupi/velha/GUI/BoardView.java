package pitiupi.velha.GUI;

import pitiupi.velha.control.Board;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/**
 * Desenha o tabuleiro e avisa em qual casa a pessoa clicou.
 *
 * <p>Não decide nada: se a jogada vale ou não é com o controller.</p>
 */
final class BoardView extends JComponent {

    private static final Color X_COLOR = new Color(0x1F6FEB);
    private static final Color O_COLOR = new Color(0xD9480F);
    private static final Color WIN_TINT = new Color(0x2E, 0xA0, 0x43, 70);
    private static final Color HOVER_TINT = new Color(0x1F, 0x6F, 0xEB, 30);

    private Board board = new Board();
    private boolean playable;
    private int hoverCell = -1;
    private IntConsumer onCellClicked = cell -> {
    };

    BoardView() {
        setPreferredSize(new Dimension(300, 300));
        setMinimumSize(new Dimension(180, 180));
        setOpaque(false);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int cell = cellAt(e.getX(), e.getY());
                if (cell >= 0) {
                    onCellClicked.accept(cell);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                setHoverCell(cellAt(e.getX(), e.getY()));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setHoverCell(-1);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    void setOnCellClicked(IntConsumer onCellClicked) {
        this.onCellClicked = onCellClicked;
    }

    /**
     * @param playable {@code true} quando é a vez de quem está nesta tela.
     */
    void show(Board board, boolean playable) {
        this.board = board;
        this.playable = playable;
        setCursor(Cursor.getPredefinedCursor(playable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
        repaint();
    }

    private void setHoverCell(int cell) {
        if (cell != hoverCell) {
            hoverCell = cell;
            repaint();
        }
    }

    /** Lado do quadrado desenhado (o tabuleiro fica centralizado no componente). */
    private int side() {
        return Math.max(0, Math.min(getWidth(), getHeight()) - 4);
    }

    private int originX() {
        return (getWidth() - side()) / 2;
    }

    private int originY() {
        return (getHeight() - side()) / 2;
    }

    private int cellAt(int x, int y) {
        int side = side();
        if (side < 3) {
            return -1;
        }
        int col = (x - originX()) * 3 / side;
        int row = (y - originY()) * 3 / side;
        if (x < originX() || y < originY() || col > 2 || row > 2) {
            return -1;
        }
        return row * 3 + col;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int side = side();
            int x0 = originX();
            int y0 = originY();
            float cell = side / 3f;

            int[] winning = board.winningLine();
            if (winning != null) {
                g2.setColor(WIN_TINT);
                for (int c : winning) {
                    fillCell(g2, x0, y0, cell, c);
                }
            } else if (playable && hoverCell >= 0 && board.isFree(hoverCell)) {
                g2.setColor(HOVER_TINT);
                fillCell(g2, x0, y0, cell, hoverCell);
            }

            Color line = UIManager.getColor("Label.foreground");
            g2.setColor(line != null ? line : Color.DARK_GRAY);
            g2.setStroke(new BasicStroke(Math.max(2f, side / 90f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i <= 2; i++) {
                int offset = Math.round(cell * i);
                g2.drawLine(x0 + offset, y0 + 4, x0 + offset, y0 + side - 4);
                g2.drawLine(x0 + 4, y0 + offset, x0 + side - 4, y0 + offset);
            }

            g2.setStroke(new BasicStroke(Math.max(4f, side / 28f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float pad = cell * 0.24f;
            for (int c = 0; c < 9; c++) {
                char mark = board.get(c);
                if (mark == Board.EMPTY) {
                    continue;
                }
                float left = x0 + (c % 3) * cell + pad;
                float top = y0 + (c / 3) * cell + pad;
                float size = cell - 2 * pad;
                if (mark == Board.X) {
                    g2.setColor(X_COLOR);
                    g2.drawLine(Math.round(left), Math.round(top), Math.round(left + size), Math.round(top + size));
                    g2.drawLine(Math.round(left + size), Math.round(top), Math.round(left), Math.round(top + size));
                } else {
                    g2.setColor(O_COLOR);
                    g2.drawOval(Math.round(left), Math.round(top), Math.round(size), Math.round(size));
                }
            }
        } finally {
            g2.dispose();
        }
    }

    private static void fillCell(Graphics2D g2, int x0, int y0, float cell, int index) {
        int x = x0 + Math.round((index % 3) * cell);
        int y = y0 + Math.round((index / 3) * cell);
        int size = Math.round(cell);
        g2.fillRoundRect(x + 5, y + 5, size - 10, size - 10, 14, 14);
    }
}
