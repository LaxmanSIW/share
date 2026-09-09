package com.invoicestudio.ui;

import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

public class WindowResizeHelper {

    public static void addResizeListener(Stage stage, Scene scene) {
        ResizeListener listener = new ResizeListener(stage);
        scene.addEventHandler(MouseEvent.MOUSE_MOVED, listener);
        scene.addEventHandler(MouseEvent.MOUSE_PRESSED, listener);
        scene.addEventHandler(MouseEvent.MOUSE_DRAGGED, listener);
        scene.addEventHandler(MouseEvent.MOUSE_EXITED, listener);
    }

    private static class ResizeListener implements EventHandler<MouseEvent> {
        private final Stage stage;
        private Cursor cursor = Cursor.DEFAULT;
        private int border = 6;
        private double startX = 0;
        private double startY = 0;
        private double startStageX = 0;
        private double startStageY = 0;
        private double startStageWidth = 0;
        private double startStageHeight = 0;

        public ResizeListener(Stage stage) {
            this.stage = stage;
        }

        @Override
        public void handle(MouseEvent mouseEvent) {
            if (stage.isMaximized()) {
                if (cursor != Cursor.DEFAULT) {
                    cursor = Cursor.DEFAULT;
                    if (stage.getScene() != null) stage.getScene().setCursor(Cursor.DEFAULT);
                }
                return;
            }

            double mouseX = mouseEvent.getX();
            double mouseY = mouseEvent.getY();
            double sceneWidth = stage.getScene().getWidth();
            double sceneHeight = stage.getScene().getHeight();

            if (MouseEvent.MOUSE_MOVED.equals(mouseEvent.getEventType())) {
                boolean top = mouseY < border;
                boolean bottom = mouseY > sceneHeight - border;
                boolean left = mouseX < border;
                boolean right = mouseX > sceneWidth - border;

                if (top && left) cursor = Cursor.NW_RESIZE;
                else if (top && right) cursor = Cursor.NE_RESIZE;
                else if (bottom && left) cursor = Cursor.SW_RESIZE;
                else if (bottom && right) cursor = Cursor.SE_RESIZE;
                else if (top) cursor = Cursor.N_RESIZE;
                else if (bottom) cursor = Cursor.S_RESIZE;
                else if (left) cursor = Cursor.W_RESIZE;
                else if (right) cursor = Cursor.E_RESIZE;
                else cursor = Cursor.DEFAULT;

                stage.getScene().setCursor(cursor);

            } else if (MouseEvent.MOUSE_PRESSED.equals(mouseEvent.getEventType())) {
                startX = mouseEvent.getScreenX();
                startY = mouseEvent.getScreenY();
                startStageX = stage.getX();
                startStageY = stage.getY();
                startStageWidth = stage.getWidth();
                startStageHeight = stage.getHeight();

            } else if (MouseEvent.MOUSE_DRAGGED.equals(mouseEvent.getEventType())) {
                if (cursor == Cursor.DEFAULT) return;

                double dx = mouseEvent.getScreenX() - startX;
                double dy = mouseEvent.getScreenY() - startY;

                double minW = stage.getMinWidth() > 0 ? stage.getMinWidth() : 600;
                double minH = stage.getMinHeight() > 0 ? stage.getMinHeight() : 400;

                // Horizontal resizing
                if (cursor == Cursor.E_RESIZE || cursor == Cursor.NE_RESIZE || cursor == Cursor.SE_RESIZE) {
                    double newW = startStageWidth + dx;
                    if (newW >= minW) stage.setWidth(newW);
                } else if (cursor == Cursor.W_RESIZE || cursor == Cursor.NW_RESIZE || cursor == Cursor.SW_RESIZE) {
                    double newW = startStageWidth - dx;
                    if (newW >= minW) {
                        stage.setX(startStageX + dx);
                        stage.setWidth(newW);
                    }
                }

                // Vertical resizing
                if (cursor == Cursor.S_RESIZE || cursor == Cursor.SE_RESIZE || cursor == Cursor.SW_RESIZE) {
                    double newH = startStageHeight + dy;
                    if (newH >= minH) stage.setHeight(newH);
                } else if (cursor == Cursor.N_RESIZE || cursor == Cursor.NE_RESIZE || cursor == Cursor.NW_RESIZE) {
                    double newH = startStageHeight - dy;
                    if (newH >= minH) {
                        stage.setY(startStageY + dy);
                        stage.setHeight(newH);
                    }
                }

            } else if (MouseEvent.MOUSE_EXITED.equals(mouseEvent.getEventType())) {
                if (!mouseEvent.isPrimaryButtonDown()) {
                    stage.getScene().setCursor(Cursor.DEFAULT);
                }
            }
        }
    }
}
