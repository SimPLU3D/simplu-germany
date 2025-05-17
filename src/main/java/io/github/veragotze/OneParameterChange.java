package io.github.veragotze;

import fr.ign.rjmcmc.kernel.Transform;

public class OneParameterChange implements Transform {
    private double amplitude;
    private int index;
    private int size;

    public OneParameterChange(double amplitude, int index, int size) {
        this.amplitude = amplitude;
        this.index = index;
        this.size = size;
    }

    @Override
    public double apply(boolean direct, double[] val0, double[] val1) {
        double dh = val0[size - 1];
        for (int i = 0 ; i < size; i++) {
            val1[i] = val0[i];
        }
        val1[index] = val0[index] + (0.5 - dh) * amplitude;
        val1[size - 1] = 1 - dh;
        return 1;
    }

    // @Override
    public double getAbsJacobian(boolean direct) {
        return 1;
    }

    @Override
    public int dimension() {
        return size;
    }

}
