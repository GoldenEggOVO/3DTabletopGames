package dev.tabletop3d.rules;

import java.util.List;
import java.util.Map;

public interface BoardGame {
    String id();

    int playerCount();

    int currentPlayer();

    boolean finished();

    String outcome();

    List<Cell> cells();

    List<String> legalActions(int seat);

    void apply(int seat, String action);

    Map<String, String> publicInfo();

    default List<RuleMessage> messages() {
        return List.of();
    }

    default List<String> actionsForCell(int seat, String cellId) {
        if (cellId == null) return List.of();
        return legalActions(seat).stream()
                .filter(
                        action -> {
                            String[] parts = action.split(":");
                            return parts.length == 2 && parts[1].equals(cellId)
                                    || parts.length == 3
                                            && (parts[1].equals(cellId) || parts[2].equals(cellId));
                        })
                .toList();
    }
}
