package jp.yucchi.facedetection;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.opencv.core.Core;

/**
 *
 * @author Yucchi
 */
public class FaceDetection extends Application {

    static {
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(getClass().getResource("FXMLFaceDetection.fxml"));
        Parent root = loader.load();
        final FXMLFaceDetectionController controller = (FXMLFaceDetectionController) loader.getController();

        Scene scene = new Scene(root);

        scene.getStylesheets().add(this.getClass().getResource("FaceDetection.css").toExternalForm());

        stage.setScene(scene);
        stage.setMinWidth(640);
        stage.setMinHeight(395);
//        stage.initStyle(StageStyle.TRANSPARENT);
        Image myIcon = new Image(this.getClass().getResource("resources/sakura_icon.png").toExternalForm());
        stage.getIcons().add(myIcon);
        stage.setTitle("FaceDetection with OpenCV");

        // オープニングアニメーション
        DoubleProperty openOpacityProperty = new SimpleDoubleProperty(0.0);
        stage.opacityProperty().bind(openOpacityProperty);
        Timeline openTimeline = new Timeline(
                new KeyFrame(
                        new Duration(100),
                        new KeyValue(openOpacityProperty, 0.0)
                ), new KeyFrame(
                        new Duration(2_500),
                        new KeyValue(openOpacityProperty, 1.0)
                ));
        stage.show();
        openTimeline.setCycleCount(1);
        openTimeline.play();

        // 画面サイズ設定
        double h = controller.getFrameHeight();
        double w = controller.getFrameWidth();
        double vh = controller.getVideoControlHeight();
        if (h > 360 || w > 640) {
            stage.setHeight(h + vh);
            stage.setWidth(w);
        }

        stage.centerOnScreen();

        stage.setOnCloseRequest(we -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation.");
            alert.setHeaderText(null);
            alert.setContentText("Do you really want to exit the application?");
            alert.showAndWait()
                    .filter(response -> response == ButtonType.OK)
                    .ifPresent(response -> {
                        // クロージングアニメーション
                        DoubleProperty closeOpacityProperty = new SimpleDoubleProperty(1.0);
                        stage.opacityProperty().bind(closeOpacityProperty);

                        Timeline closeTimeline = new Timeline(
                                new KeyFrame(
                                        new Duration(100),
                                        new KeyValue(closeOpacityProperty, 1.0)
                                ), new KeyFrame(
                                        new Duration(2_500),
                                        new KeyValue(closeOpacityProperty, 0.0)
                                ));

                        EventHandler<ActionEvent> eh = ae -> {
                            Platform.exit();
                            System.exit(0);
                        };

                        closeTimeline.setOnFinished(eh);
                        closeTimeline.setCycleCount(1);
                        closeTimeline.play();
                    });
            we.consume();
        });

    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}
