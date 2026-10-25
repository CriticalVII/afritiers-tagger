package za.co.afritiers.tagger.waitlist;

import java.util.*;

/** One alert per opening/event, independent of reconnects, errors or ordinary queue-size changes. */
public final class WaitlistAlerts {
    private final LinkedHashMap<String, String> announced = new LinkedHashMap<>();

    public List<Waitlists.Entry> update(Waitlists snapshot, Set<String> selected, boolean inGame) {
        List<Waitlists.Entry> alerts = new ArrayList<>();
        snapshot.entries().values().stream().sorted(Comparator.comparing(Waitlists.Entry::mode)).forEach(queue -> {
            if (!queue.open()) { announced.remove(queue.mode()); return; }
            if (!inGame || !selected.contains(queue.mode())) return;
            String event = queue.eventKey().isEmpty() ? "open" : queue.eventKey();
            if (!event.equals(announced.put(queue.mode(), event))) alerts.add(queue);
            if (announced.size() > 256) announced.remove(announced.keySet().iterator().next());
        });
        return List.copyOf(alerts);
    }
}
