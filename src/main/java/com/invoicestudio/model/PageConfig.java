package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PageConfig {
    private PageSizeName sizeName = PageSizeName.A4;
    private double width = 210.0; // mm
    private double height = 297.0; // mm
    private String orientation = "portrait"; // portrait, landscape
    private Margins margin = new Margins(8, 8, 8, 8); // mm
    private boolean autoHeight = false; // for continuous thermal rolls

    public PageConfig() {}

    public PageConfig(PageSizeName sizeName, double width, double height, String orientation, Margins margin) {
        this.sizeName = sizeName;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.margin = margin != null ? margin : new Margins(8, 8, 8, 8);
    }

    public PageSizeName getSizeName() { return sizeName != null ? sizeName : PageSizeName.A4; }
    public void setSizeName(PageSizeName sizeName) { this.sizeName = sizeName; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public double getHeight() { return height; }
    public void setHeight(double height) { this.height = height; }

    public String getOrientation() { return orientation != null ? orientation : "portrait"; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

    public Margins getMargin() { return margin != null ? margin : new Margins(8, 8, 8, 8); }
    public void setMargin(Margins margin) { this.margin = margin; }

    public boolean isAutoHeight() { return autoHeight; }
    public void setAutoHeight(boolean autoHeight) { this.autoHeight = autoHeight; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Margins {
        private double top = 8;
        private double right = 8;
        private double bottom = 8;
        private double left = 8;

        public Margins() {}

        public Margins(double top, double right, double bottom, double left) {
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            this.left = left;
        }

        public double getTop() { return top; }
        public void setTop(double top) { this.top = top; }

        public double getRight() { return right; }
        public void setRight(double right) { this.right = right; }

        public double getBottom() { return bottom; }
        public void setBottom(double bottom) { this.bottom = bottom; }

        public double getLeft() { return left; }
        public void setLeft(double left) { this.left = left; }
    }
}
