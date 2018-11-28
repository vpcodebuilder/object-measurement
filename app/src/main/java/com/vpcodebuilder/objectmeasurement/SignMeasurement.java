package com.vpcodebuilder.objectmeasurement;

import org.opencv.core.Rect;
import org.opencv.core.Size;

public class SignMeasurement {
    private Rect boundary;
    private double rulerWidth;
    private double rulerHeight;

    public Rect getBoundary() {
        return boundary;
    }

    public double getRulerWidth() {
        return rulerWidth;
    }

    public double getRulerHeight() {
        return rulerHeight;
    }

    public SignMeasurement(Rect boundary, double rulerWidth, double rulerHeight) {
        this.boundary = boundary;
        this.rulerWidth = rulerWidth;
        this.rulerHeight = rulerHeight;
    }

    public Size ComputeObjectSize(Rect originalRect, Size realMarkerMillimeterSize) {
        double ratioWidth = realMarkerMillimeterSize.width / rulerWidth;
        double ratioHeight = realMarkerMillimeterSize.height / rulerHeight;
        return new Size(originalRect.width * ratioWidth, originalRect.height * ratioHeight);
    }

    public static android.graphics.Rect ConvertToGraphicRectType(Rect rect) {
        return new android.graphics.Rect((int)rect.tl().x, (int)rect.tl().y, (int)rect.br().x, (int)rect.br().y);
    }
}
