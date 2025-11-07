package player;

import javafx.application.Application;
import javafx.stage.Stage;
import player.view.MainWindow;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        boolean found = new NativeDiscovery().discover();
        if (!found) {
            System.setProperty("jna.library.path", "F:\\exploit\\code\\java_code\\MusicPlayer\\VLC");
        }
        MainWindow window = new MainWindow();
        window.initStage(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
