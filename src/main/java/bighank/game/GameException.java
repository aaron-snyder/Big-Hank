package bighank.game;

/** A player tried something the rules don't allow. The message is shown to them as-is. */
public class GameException extends RuntimeException {
    public GameException(String message) {
        super(message);
    }
}
