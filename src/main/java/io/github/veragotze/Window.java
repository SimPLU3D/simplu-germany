package io.github.veragotze;

public class Window<G> {
    G geometry;
    double minHeight;
    double maxHeight;
    String roofType;
    public Window(G geometry, double minHeight, double maxHeight, String roofType) {
        this.geometry = geometry;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.roofType = roofType;
    }
}
