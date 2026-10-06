package dev.tabletop3d.render.cards;

/** Shared compact layers for native card backs and privately rendered faces. */
public final class CardLayout {
    // A 0.0025 step makes the next blue panel coplanar with the previous white face.
    public static final double DEPTH_STEP = .0025 + .168 / 32 * .1;
    public static final double HEIGHT_STEP = .0011;

    private CardLayout() {}
}
