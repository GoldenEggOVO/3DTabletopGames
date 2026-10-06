package dev.tabletop3d.rules;

import java.util.List;

public interface SelectedHandGame extends HandGame {
    String selectionAction(int seat, List<String> ids);

    List<String> controls(int seat);
}
