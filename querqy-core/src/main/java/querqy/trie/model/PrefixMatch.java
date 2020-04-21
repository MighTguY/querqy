package querqy.trie.model;

import java.util.Objects;

public class PrefixMatch<T> {
    public final T match;
    public final int exclusiveEnd;
    private int lookupOffset;

    public PrefixMatch(int exclusiveEnd, T match) {
        this.exclusiveEnd = exclusiveEnd;
        this.match = match;
    }

    public int getLookupOffset() {
        return lookupOffset;
    }

    public PrefixMatch<T> setLookupOffset(int lookupOffset) {
        this.lookupOffset = lookupOffset;
        return this;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PrefixMatch<?> that = (PrefixMatch<?>) o;
        return exclusiveEnd == that.exclusiveEnd &&
                lookupOffset == that.lookupOffset &&
                Objects.equals(match, that.match);
    }

    @Override
    public int hashCode() {
        return Objects.hash(match, exclusiveEnd, lookupOffset);
    }

    @Override
    public String toString() {
        return "PrefixMatch{" +
                "match=" + match +
                ", exclusiveEnd=" + exclusiveEnd +
                ", lookupOffset=" + lookupOffset +
                '}';
    }
}
