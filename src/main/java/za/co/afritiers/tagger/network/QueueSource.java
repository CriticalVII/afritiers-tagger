package za.co.afritiers.tagger.network;

import za.co.afritiers.tagger.waitlist.Waitlists;

@FunctionalInterface
public interface QueueSource extends AutoCloseable {
    Waitlists fetch() throws Exception;
    default void close() { }
}
