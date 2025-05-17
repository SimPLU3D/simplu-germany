package io.github.veragotze;

public class Window<G> {
    G geometry;
    double minHeight;
    double maxHeight;
    // roof type can be flat (FD) or gable (SD)
    String roofType;
    // building style can be attached buildings (g) or not
    String buildingStyle;
    public Window(G geometry, double minHeight, double maxHeight, String roofType, String buildingStyle) {
        this.geometry = geometry;
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.roofType = roofType;
        this.buildingStyle = buildingStyle;
    }
}
