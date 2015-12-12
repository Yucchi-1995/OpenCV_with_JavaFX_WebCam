package jp.yucchi.facedetection;

import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.util.Duration;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

/**
 *
 * @author Yucchi
 */
public class FXMLFaceDetectionController implements Initializable {

    @FXML
    private AnchorPane anchorPane;
    @FXML
    private AnchorPane buttonAnchorPane;
    @FXML
    private ImageView imageView;
    @FXML
    private Button playButton;
    @FXML
    private Button stopButton;

    private VideoCapture bufferedVideo;
    private Mat videoMatImage;
    private Mat2BufferedImage mat2BufferedImage;
    private Image imageVideo;
    private VideoProcessingService service;

    @FXML
    private void handlePlayButtonAction(ActionEvent event) {

        if (service == null) {
            service = new VideoProcessingService();
            mat2BufferedImage = new Mat2BufferedImage();
            videoMatImage = new Mat();
        }

        service.setOnSucceeded(wse -> {

            if (service.getValue() != null) {
                imageView.setImage(imageVideo);
                service.restart();
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Not able to open the video.");
                alert.setHeaderText("Video codec is not supported.");
                alert.setContentText("Exit the application.");
                alert.showAndWait()
                        .filter(response -> response == ButtonType.OK)
                        .ifPresent(response -> exitProcessing());
            }

        });

        service.setOnFailed(wse -> {

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("An unexpected error has occurred.");
            alert.setHeaderText(null);
            alert.setContentText("Exit the application.");
            alert.showAndWait()
                    .filter(response -> response == ButtonType.OK)
                    .ifPresent(response -> exitProcessing());

        });

        if (!bufferedVideo.isOpened()) {
            mat2BufferedImage = new Mat2BufferedImage();
            videoMatImage = new Mat();
            bufferedVideo = new VideoCapture(0); // WebCam 入力の場合
        }

        if (!service.isRunning()) {
            service.reset();
            service.start();
        }

        playButton.disableProperty().bind(service.runningProperty());
        stopButton.disableProperty().bind(service.runningProperty().not());

    }

    @FXML
    private void handleStopButtonAction(ActionEvent event) {

        if (service.isRunning()) {
            service.cancel();
        }

        if (bufferedVideo.isOpened()) {
            bufferedVideo.release();
            mat2BufferedImage = null;
            videoMatImage = null;
            imageView.setImage(new Image(this.getClass().getResource("resources/d3.png").toExternalForm()));
        }

    }

    @FXML
    private void handleExitButtonAction(ActionEvent event) {

        exitProcessing();

    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        imageView.setImage(new Image(this.getClass().getResource("resources/d3.png").toExternalForm()));

        bufferedVideo = new VideoCapture(0); // WebCam 入力の場合

        if (bufferedVideo.get(Videoio.CAP_PROP_FRAME_HEIGHT) > 360 || bufferedVideo.get(Videoio.CAP_PROP_FRAME_WIDTH) > 640) {
            imageView.fitHeightProperty().bind(anchorPane.heightProperty().subtract(buttonAnchorPane.getHeight()));
            imageView.fitWidthProperty().bind(anchorPane.widthProperty());
        }

    }

    double getFrameHeight() {
        return bufferedVideo.get(Videoio.CAP_PROP_FRAME_HEIGHT);

    }

    double getFrameWidth() {
        return bufferedVideo.get(Videoio.CAP_PROP_FRAME_WIDTH);

    }

    double getVideoControlHeight() {
        return buttonAnchorPane.getHeight();
    }

    class VideoProcessingService extends Service<Image> {

        private final long FRAME_CYCLE_TIME = 33_366_700L; // 29.7 fps
//        private final long FRAME_CYCLE_TIME = 41_708_375L; // 23.976 fps
        private long processingOvertime;
        private final CascadeClassifier faceDetector = new CascadeClassifier("src/jp/yucchi/facedetection/data/lbpcascades/lbpcascade_frontalface.xml");
        private Mat faceDetectionVideoImage = new Mat();

        @Override
        protected Task<Image> createTask() {

            Task<Image> task = new Task<Image>() {

                private long startTime = System.nanoTime();

                @Override
                protected Image call() throws Exception {

                    if (bufferedVideo.isOpened()) {
                        bufferedVideo.read(videoMatImage);
                        if (!videoMatImage.empty()) {
                            faceDetectionVideoImage = faceDetection(faceDetector, videoMatImage);
                            BufferedImage tempBufferedImage = mat2BufferedImage.toBufferedImage(faceDetectionVideoImage);
                            imageVideo = SwingFXUtils.toFXImage(tempBufferedImage, null);
                        } else {
                            bufferedVideo.release();
                            mat2BufferedImage = null;
                            videoMatImage = null;
                            imageVideo = new Image(this.getClass().getResource("resources/d2.png").toExternalForm());
                        }
                    }

                    if (System.nanoTime() - startTime < FRAME_CYCLE_TIME - processingOvertime) {
                        TimeUnit.NANOSECONDS.sleep(FRAME_CYCLE_TIME - (System.nanoTime() - startTime));
                        processingOvertime = 0L;
                    } else {
                        processingOvertime = System.nanoTime() - startTime - FRAME_CYCLE_TIME;
                    }
                    return imageVideo;

                }

                private Mat faceDetection(CascadeClassifier faceDetector, Mat videoMatImage) {

                    Mat dst = videoMatImage.clone();

                    MatOfRect faceDetections = new MatOfRect();

                    Imgproc.cvtColor(videoMatImage, dst, Imgproc.COLOR_RGB2GRAY);

                    Imgproc.equalizeHist(dst, dst);

                    faceDetector.detectMultiScale(dst, faceDetections);

                    if (faceDetections.toList().size() <= 0) {
                        return videoMatImage;
                    }

                    faceDetections.toList()
                            .forEach(rect -> {
                                Imgproc.circle(videoMatImage, new Point(rect.x + rect.width / 2, rect.y + rect.height / 2),
                                        rect.width / 2,
                                        new Scalar(0, 0, 255), 2);
                            });

                    return videoMatImage;

                }

            };

            return task;

        }

    }

    private void exitProcessing() {

        // クロージングアニメーション
        DoubleProperty closeOpacityProperty = new SimpleDoubleProperty(1.0);
        anchorPane.getScene().getWindow().opacityProperty().bind(closeOpacityProperty);

        Timeline closeTimeline = new Timeline(
                new KeyFrame(
                        new Duration(100),
                        new KeyValue(closeOpacityProperty, 1.0)
                ), new KeyFrame(
                        new Duration(2_500),
                        new KeyValue(closeOpacityProperty, 0.0)
                ));

        EventHandler<ActionEvent> eh = ae -> {
            if (service != null && service.isRunning()) {
                service.cancel();
            }
            if (bufferedVideo != null && bufferedVideo.isOpened()) {
                bufferedVideo.release();
            }
            Platform.exit();
            System.exit(0);
        };

        closeTimeline.setOnFinished(eh);
        closeTimeline.setCycleCount(1);
        closeTimeline.play();
    }

}
