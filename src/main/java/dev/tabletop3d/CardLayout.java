package dev.tabletop3d;

/** Shared compact layers for native card backs and privately rendered faces. */
final class CardLayout {
    // A 0.0025 step makes the next blue panel coplanar with the previous white face.
    static final double DEPTH_STEP = .0025 + .168 / 32 * .1;
    static final double HEIGHT_STEP = .0011;

    private CardLayout() {}
}
