package com.ninsky.cronos.quote.domain;

import com.ninsky.cronos.domain.entity.enums.QuoteStatus;

/** The quote state machine's guard (§4.1), shared by every command. */
public final class QuoteTransitions {

    private QuoteTransitions() {
    }

    /**
     * @throws InvalidQuoteTransitionException when {@code to} is not reachable from {@code from}
     */
    public static void require(QuoteStatus from, QuoteStatus to) {
        if (from == null || !from.canMoveTo(to)) {
            throw new InvalidQuoteTransitionException(from, to);
        }
    }

    /** A transition the state machine does not allow. */
    public static final class InvalidQuoteTransitionException extends RuntimeException {

        private final transient QuoteStatus from;
        private final transient QuoteStatus to;

        public InvalidQuoteTransitionException(QuoteStatus from, QuoteStatus to) {
            super("Quote cannot move from " + from + " to " + to);
            this.from = from;
            this.to = to;
        }

        public QuoteStatus from() {
            return from;
        }

        public QuoteStatus to() {
            return to;
        }
    }
}
