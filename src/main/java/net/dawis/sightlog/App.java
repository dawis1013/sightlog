package net.dawis.sightlog;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class App extends Application {
    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    static void main(String[] args) {
        App.launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/login_ui.fxml"));
        Scene scene = new Scene(loader.load());
        scene.setFill(Color.TRANSPARENT);
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setTitle("SightLog");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        LOG.info("Application started");
    }
}
