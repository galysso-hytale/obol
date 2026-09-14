package dev.galysso.obol.api;

/**
 * Thrown by {@link CoinsFormat#parse(String)} when the text is not an amount.
 *
 * <p>Checked on purpose: the text usually comes from a player (command
 * argument, chat), so rejecting it is a normal outcome the caller must report,
 * not a bug.</p>
 */
public class CoinsParseException extends Exception {

    private final String input;

    /**
     * Creates the exception.
     *
     * @param message why the text was rejected
     * @param input   the rejected text
     */
    public CoinsParseException(String message, String input) {
        super(message);
        this.input = input;
    }

    /**
     * {@return the text that was rejected}
     */
    public String input() {
        return input;
    }
}
