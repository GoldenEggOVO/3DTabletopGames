package dev.tabletop3d.rules;

import java.util.List;

/** World controls submit an explicit selection; legalActions supplies bounded bot candidates. */
public interface SelectedHandGame extends HandGame {
    String selectionAction(int seat, List<String> ids);

    List<String> controls(int seat);
}
