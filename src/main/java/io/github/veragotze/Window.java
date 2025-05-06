package io.github.veragotze;

public class Window<G> {
    G geometry;
    double minHeight;
    double maxHeight;
    public Window(G geometry, double minHeight, double maxHeight) {
        this.geometry = geometry;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
    }
}
