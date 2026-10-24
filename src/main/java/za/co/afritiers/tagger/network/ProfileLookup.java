package za.co.afritiers.tagger.network;

import java.util.Optional;

@FunctionalInterface
public interface ProfileLookup extends AutoCloseable {
    record Profile(String id, String name) { }
    Optional<Profile> lookup(String username) throws Exception;
    default void close() { }
}
