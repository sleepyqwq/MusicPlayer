package player.view;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.util.Duration;
import player.model.LyricLine;
import player.model.Song;
import player.util.MusicLibrary;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Objects;

public class MainWindow {
    /** 底部“播放/暂停”按钮也要作为成员变量，便于在换歌、媒体结束后直接切换图标 **/
    private Button playPauseButton;
    // 资源路径：classpath 下的 images 文件夹
    private static final String DISC_IMG       = getResource("/images/disc.png");
    private static final String LIST_BG_IMG    = getResource("/images/list.png");

    // 新增音量相关成员变量
    private StackPane volumeOverlay;
    private Slider volumeSlider;
    private boolean isVolumeControlVisible = false;

    // 把顶部栏的标签提升为成员变量，方便后续更新
    private Label songLabel;
    private Label artistLabel;

    private StackPane playlistOverlay;
    private StackPane bodyRoot;       // 叠加局部Pane和全屏Pane
    private BorderPane localPane;     // 局部模式：唱片+局部歌词
    private ScrollPane fullPane;      // 全屏模式：完整歌词滚动

    private ImageView discImageView;
    private Label prevLyricLabel;
    private VBox fullLyricsBox;

    private boolean isFullScreenLyrics = false;
    private boolean isPlaying = false; // 播放状态
    /** 标记当前歌曲的媒体是否已被调用过 prepareAndPlayCurrentSong(...) */
    private boolean mediaPrepared = false;

    private List<Song> allSongs;      // 所有歌曲列表
    private ListView<String> listView; // 播放列表控件

    /** VLCJ 播放器工厂及播放器实例 */
    private MediaPlayerFactory vlcFactory;
    private MediaPlayer vlcPlayer;

    /** 当前正在播放的歌曲 */
    private Song currentSong;

    /** 用于在 currentTime 变化时控制歌词索引 */
    private int currentLyricIndex = 0;

    // 底部进度条 / 时间标签
    private ProgressBar bottomProgressBar;
    private Label bottomCurrentTimeLabel;
    private Label bottomTotalTimeLabel;

    private Timeline progressTimer;

    private MediaPlayerEventListener currentMediaListener;
    private long currentTotalDuration = 0; // 存储当前歌曲总时长

    private boolean wasPlaying; // 用于记录拖拽进度条前的播放状态
    private boolean isDragging = false; // 添加类成员变量

    // 新增字段：用于局部模式滚动动画时承载 prev+curr 两行
    private VBox lyricBoxContent;

    // 新增字段：记录上一次全屏滚动的 vvalue
    private double lastVvalue = 0.0;

    private Label nextLyricLabel;    // 显示下一行歌词（第三行）

    private Circle discClipCircle; // 用于共享裁剪圆
    private StackPane animationContainer; // 动画容器

    /** 初始化舞台 **/
    public void initStage(  Stage stage) {
        Font.loadFont(getClass().getResourceAsStream("/iconfont/iconfont.ttf"), 16);
        // 1) 后端：扫描 MusicList，并保存到 allSongs
        allSongs = MusicLibrary.loadAllSongs();

        // —— 初始化 VLCJ，需先设置 jna.library.path 或在 VM options 加参数 ——
        vlcFactory = new MediaPlayerFactory();       // 默认会从系统路径加载 libvlc
        vlcPlayer  = vlcFactory.mediaPlayers().newMediaPlayer();

        // —— 改动：将背景图换成渐变色 Pane ——
        Pane gradientPane = new Pane();
        gradientPane.setStyle(
                "-fx-background-color: linear-gradient(" +
                        "to bottom, " +
                        "#F3E8FF 0%, " +
                        "#FFFFFF 100%);"
        );

        // —— 顶部栏、Body、底部栏按旧逻辑生成 ——
        VBox topBar = createTopBar();
        bodyRoot = new StackPane();
        createLocalPane();
        createFullPane();
        localPane.setVisible(true);
        fullPane.setVisible(false);
        bodyRoot.getChildren().addAll(localPane, fullPane);
        bodyRoot.setOnMouseClicked(e -> toggleLyricsMode());
        VBox bottomBar = createBottomBar();

        BorderPane border = new BorderPane();
        border.setTop(topBar);
        border.setCenter(bodyRoot);
        border.setBottom(bottomBar);

        // —— 播放列表浮层 ——
        playlistOverlay = createPlaylistOverlay();
        playlistOverlay.setVisible(false);

        // —— 创建音量控制浮层 ——
        volumeOverlay = createVolumeControl();
        volumeOverlay.setVisible(false);

        // —— 把 gradientPane、border、playlistOverlay、volumeOverlay 叠放 ——
        StackPane root = new StackPane(gradientPane, border, playlistOverlay, volumeOverlay);
        StackPane.setAlignment(playlistOverlay, Pos.CENTER_RIGHT);

        // 关键：让 volumeOverlay 浮在右下，但“抬高”到🔊按钮之上
        StackPane.setAlignment(volumeOverlay, Pos.BOTTOM_RIGHT);
        // 下面这行中的 bottomMargin 需要根据你的底部栏高度＋🔊按钮到底部的间距 调整:
        // 比如底部栏高度约 50px，再给 5px 间隔，就写 new Insets(0, 10, 55, 0)
        StackPane.setMargin(volumeOverlay, new Insets(0, 10, 55, 0));

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        // 设置窗口图标
        stage.getIcons().add(  // ✅ 使用正确的参数名
                new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icon.png")))
        );

        stage.setTitle("TQ的音乐播放器😋");
        stage.setScene(scene);
        stage.show();

        // 初始化第一首歌
        if (!allSongs.isEmpty()) {
            loadSong(allSongs.getFirst());
        }
    }


    /** 创建顶部栏：歌曲名 + 艺术家 **/
    private VBox createTopBar() {
        songLabel = new Label("歌曲名");
        songLabel.setStyle("-fx-font-weight:bold; -fx-font-size:16px;");

        artistLabel = new Label("艺术家");
        artistLabel.setStyle("-fx-font-size:12px;");

        VBox box = new VBox(2, songLabel, artistLabel);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(6));
        return box;
    }

    /** 创建局部模式 Pane：圆形唱片 + 三行歌词（第一行淡出，第二行高亮，第三行淡化） **/
    private void createLocalPane() {

        // —— 圆形唱片容器 ——
        StackPane discContainer = new StackPane();
        discContainer.setPrefSize(260, 260);
        discContainer.setMaxSize(260, 260);
        discContainer.setAlignment(Pos.CENTER);

        // 外层半透明白色环：半径 130，填充白色 60% 透明度
        Circle ring = new Circle(130);
        ring.setFill(Color.rgb(255, 255, 255, 0.9));
        ring.setStroke(Color.TRANSPARENT);

        // 唱片容器（确保同心）
        StackPane discContent = new StackPane();
        discContent.setAlignment(Pos.CENTER);

        // 内层裁剪圆：半径 120 (提升为成员变量)
        discClipCircle = new Circle(120);
        discClipCircle.setCenterX(120);
        discClipCircle.setCenterY(120);

        // 唱片图片 240×240
        discImageView = new ImageView(new Image(DISC_IMG));
        discImageView.setPreserveRatio(true);
        discImageView.setFitWidth(240);
        discImageView.setFitHeight(240);
        discImageView.setClip(discClipCircle); // 使用成员变量

        // 添加唱片图片
        discContent.getChildren().add(discImageView);

        // 创建动画容器
        animationContainer = new StackPane();
        animationContainer.getChildren().addAll(ring, discContent);
        animationContainer.setAlignment(Pos.CENTER);

        // 添加动画容器到主容器
        discContainer.getChildren().add(animationContainer);

        StackPane.setMargin(discContainer, new Insets(20, 0, 0, 0));

        // 阴影应用到整个 discContainer
        DropShadow containerShadow = new DropShadow();
        containerShadow.setColor(Color.rgb(0, 0, 0, 0.5));
        containerShadow.setRadius(50);
        containerShadow.setOffsetX(8);
        containerShadow.setOffsetY(8);
        discContainer.setEffect(containerShadow);

        // 唱片旋转动画
        RotateTransition rotateTransition = new RotateTransition(Duration.seconds(10), discContainer);
        rotateTransition.setByAngle(360);
        rotateTransition.setCycleCount(RotateTransition.INDEFINITE);
        rotateTransition.setInterpolator(Interpolator.LINEAR);
        discContainer.setUserData(rotateTransition);

        // —— 局部歌词区域：两行 Label ——
        prevLyricLabel = new Label("");
        prevLyricLabel.setFont(new Font("Arial", 18));
        prevLyricLabel.setTextFill(Color.rgb(255, 100, 100, 0.9)); // 半透明淡红
        prevLyricLabel.setOpacity(1.0);

        nextLyricLabel = new Label("");
        nextLyricLabel.setFont(new Font("Arial", 12));
        nextLyricLabel.setTextFill(Color.rgb(255, 100, 100, 0.5)); // 更半透明
        nextLyricLabel.setOpacity(1.0);

        lyricBoxContent = new VBox(3, prevLyricLabel, nextLyricLabel);
        lyricBoxContent.setPadding(new Insets(0));

        BorderPane.setAlignment(lyricBoxContent, Pos.BOTTOM_LEFT);
        BorderPane.setMargin(lyricBoxContent, new Insets(0, 0, 30, 20));

        localPane = new BorderPane();
        localPane.setCenter(discContainer);
        localPane.setBottom(lyricBoxContent);
        discImageView.setImage(new Image(DISC_IMG)); // 使用您原有的默认封面
    }

    /** 创建全屏模式 Pane：ScrollPane 中放完整歌词列表，隐藏滚动条并保持透明背景 **/
    private void createFullPane() {
        fullLyricsBox = new VBox(10);
        fullLyricsBox.setPadding(new Insets(20));
        // 后续根据歌曲动态插入歌词

        StackPane fullContainer = new StackPane(fullLyricsBox);
        fullContainer.setAlignment(Pos.CENTER);

        fullPane = new ScrollPane(fullContainer);
        fullPane.setFitToWidth(true);
        fullPane.setFitToHeight(true);
        fullPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        fullPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        fullPane.getStyleClass().add("transparent-scroll-pane");

        fullPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            fullContainer.setPrefWidth(newBounds.getWidth());
            fullContainer.setPrefHeight(newBounds.getHeight());
        });
    }

    /** 切换“局部歌词”↔“全屏歌词”并添加渐变动画 **/
    private void toggleLyricsMode() {
        isFullScreenLyrics = !isFullScreenLyrics;

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300));
        FadeTransition fadeIn  = new FadeTransition(Duration.millis(300));

        if (isFullScreenLyrics) {
            fadeOut.setNode(localPane);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            fadeIn.setNode(fullPane);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            fadeOut.setOnFinished(e -> {
                localPane.setVisible(false);
                fullPane.setVisible(true);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            fadeOut.setNode(fullPane);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);

            fadeIn.setNode(localPane);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            fadeOut.setOnFinished(e -> {
                fullPane.setVisible(false);
                localPane.setVisible(true);
                fadeIn.play();
            });
            fadeOut.play();
        }
    }

    /** 处理进度条鼠标按下事件 */
    private void handleProgressBarMousePress(MouseEvent e) {
        isDragging = true;
        bottomProgressBar.setScaleY(1.2);
        if (vlcPlayer == null || !mediaPrepared) return;

        // 暂停播放（如果正在播放）
        wasPlaying = vlcPlayer.status().isPlaying();
        if (wasPlaying) {
            vlcPlayer.controls().pause();
        }

        // 暂停进度条更新定时器
        if (progressTimer != null) {
            progressTimer.pause();
        }

        // 计算并设置新进度
        updateProgressFromMouse(e);
    }

    /** 处理进度条拖拽事件 */
    private void handleProgressBarDrag(MouseEvent e) {
        if (vlcPlayer == null || !mediaPrepared) return;
        updateProgressFromMouse(e);
    }

    /** 处理进度条鼠标释放事件 */
    private void handleProgressBarRelease(MouseEvent e) {
        isDragging = false;
        bottomProgressBar.setScaleY(1.0);
        if (vlcPlayer == null || !mediaPrepared) return;

        // 设置媒体位置
        updateProgressFromMouse(e);

        // 恢复播放状态
        if (wasPlaying) {
            vlcPlayer.controls().play();
        }

        // 恢复进度条更新定时器
        if (progressTimer != null) {
            progressTimer.play();
        }
    }

    /** 根据鼠标位置更新进度 */
    private void updateProgressFromMouse(MouseEvent e) {
        ProgressBar progressBar = (ProgressBar) e.getSource();
        double mouseX = e.getX();
        double totalWidth = progressBar.getWidth();
        double newProgress = mouseX / totalWidth;

        // 确保进度在0-1之间
        newProgress = Math.max(0.0, Math.min(1.0, newProgress));
        progressBar.setProgress(newProgress);

        // 计算对应的媒体时间
        long totalMillis = currentTotalDuration > 0 ? currentTotalDuration : vlcPlayer.media().info().duration();
        if (totalMillis > 0) {
            long newTime = (long) (newProgress * totalMillis);

            // 更新媒体位置
            vlcPlayer.controls().setTime(newTime);

            // 更新当前时间显示
            if (bottomCurrentTimeLabel != null) {
                bottomCurrentTimeLabel.setText(formatDuration(javafx.util.Duration.millis(newTime)));
            }
        }
    }

    /**
     * 创建一个“字体图标”按钮，带阴影 + 点击时缩放反馈
     * @param textUnicode iconfont.css 中对应的 content: "\eXXX"
     * @param fontSize    需要的图标字号
     */
    private Button createIconFontButton(String textUnicode, double fontSize) {
        Button btn = new Button(textUnicode);
        btn.setFont(Font.font("iconfont", fontSize));
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: rgba(0, 0, 0, 0.7);");

        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.rgb(0, 0, 0, 0.4));
        shadow.setRadius(5);
        shadow.setOffsetX(2);
        shadow.setOffsetY(2);
        btn.setEffect(shadow);

        btn.setOnMousePressed((MouseEvent e) -> {
            btn.setScaleX(0.9);
            btn.setScaleY(0.9);
        });
        btn.setOnMouseReleased((MouseEvent e) -> {
            btn.setScaleX(1.0);
            btn.setScaleY(1.0);
        });

        return btn;
    }

    /**
     * 底部栏：进度条 + 时间显示 + 控制按钮 + 歌单按钮 + 音量按钮
     */
    private VBox createBottomBar() {
        // 1）底部进度条
        bottomProgressBar = new ProgressBar(0);
        bottomProgressBar.setPrefWidth(360);
        bottomProgressBar.setPrefHeight(12);
        bottomProgressBar.getStyleClass().clear();
        bottomProgressBar.getStyleClass().add("custom-progress");

        bottomProgressBar.setOnMousePressed(this::handleProgressBarMousePress);
        bottomProgressBar.setOnMouseDragged(this::handleProgressBarDrag);
        bottomProgressBar.setOnMouseReleased(this::handleProgressBarRelease);

        // 2）时间标签
        bottomCurrentTimeLabel = new Label("00:00");
        bottomTotalTimeLabel   = new Label("00:00");
        HBox timeBox = new HBox(8,
                bottomCurrentTimeLabel,
                new Label("/"),
                bottomTotalTimeLabel
        );
        timeBox.setAlignment(Pos.CENTER);
        timeBox.setPadding(new Insets(2));

        // 3）控制按钮：上一曲、快退、播放/暂停、快进、下一曲
        Button trackPrev = createIconFontButton("\ue693", 24); // icon-shangyiqu
        Button prev      = createIconFontButton("\ue68e", 20); // icon-kuaitui
        playPauseButton  = createIconFontButton("\ue692", 28); // icon-bofangzhong（“播放”）
        Button next      = createIconFontButton("\ue68f", 20); // icon-kuaijin
        Button trackNext = createIconFontButton("\ue694", 24); // icon-xiayiqu

        // 3.1）歌单按钮（统一用 iconfont）
        Button listBtn = createIconFontButton("\ue699", 24); // icon-bofangduilie

        // 3.2）音量按钮（统一用 iconfont，假设 \ue698 对应“音量”图标）
        Button volumeBtn = createIconFontButton("\ue698", 24); // 请根据实际 Unicode 修改

        volumeBtn.setOnAction(e -> toggleVolumeControl());

        // 4）播放/暂停 按钮：淡出→切换 Unicode→淡入 动画
        playPauseButton.setOnAction(e -> {
            if (currentSong == null) {
                if (!allSongs.isEmpty()) {
                    loadSong(allSongs.getFirst());
                } else {
                    return;
                }
            }
            if (!mediaPrepared) {
                // 第一次点击：准备并播放
                prepareAndPlayCurrentSong(
                        bottomProgressBar,
                        bottomCurrentTimeLabel,
                        bottomTotalTimeLabel
                );
                mediaPrepared = true;
                isPlaying = true;
                // 切换到“暂停”图标 (\ue690)
                applyFadeSwitch(playPauseButton, "\ue690", 28);
                // 启动唱片转盘
                RotateTransition rt =
                        (RotateTransition) localPane.getCenter().getUserData();
                rt.play();
                return;
            }
            if (vlcPlayer.status().isPlaying()) {
                // 正在播放时，点击暂停
                vlcPlayer.controls().pause();
                isPlaying = false;
                // 切换回“播放”图标 (\ue692)
                applyFadeSwitch(playPauseButton, "\ue692", 28);
                // 停止转盘
                RotateTransition rt =
                        (RotateTransition) localPane.getCenter().getUserData();
                rt.pause();
            } else {
                // 当前暂停时，点击继续播放
                vlcPlayer.controls().play();
                isPlaying = true;
                // 切换到“暂停”图标 (\ue690)
                applyFadeSwitch(playPauseButton, "\ue690", 28);
                // 继续转盘
                RotateTransition rt =
                        (RotateTransition) localPane.getCenter().getUserData();
                rt.play();
            }
        });

        // 5）“快退”10s
        prev.setOnAction(e -> {
            if (vlcPlayer != null) {
                long currentTime = vlcPlayer.status().time();
                long newTime = currentTime - 10_000;
                if (newTime < 0) newTime = 0;
                vlcPlayer.controls().setTime(newTime);
                updateProgressBar();
            }
        });

        // 6）“快进”10s
        next.setOnAction(e -> {
            if (vlcPlayer != null) {
                long currentTime = vlcPlayer.status().time();
                long newTime = currentTime + 10_000;
                long total = vlcPlayer.media().info().duration();
                if (newTime > total) newTime = total;
                vlcPlayer.controls().setTime(newTime);
                updateProgressBar();
            }
        });

// 7）“上一曲”
        trackPrev.setOnAction(e -> {
            if (currentSong != null && allSongs.size() > 1) {
                int idx     = allSongs.indexOf(currentSong);
                int prevIdx = (idx - 1 + allSongs.size()) % allSongs.size();
                Song newSong = allSongs.get(prevIdx);

                // === 修改开始 ===
                // 直接使用 Song 对象的封面图片
                playDiscChangeAnimation(newSong.getCoverImage());
                // === 修改结束 ===

                loadSong(newSong);
                mediaPrepared = false;
                prepareAndPlayCurrentSong(
                        bottomProgressBar,
                        bottomCurrentTimeLabel,
                        bottomTotalTimeLabel
                );
                mediaPrepared = true;
                isPlaying = true;
                applyFadeSwitch(playPauseButton, "\ue690", 28);
            }
        });

// 8）“下一曲”
        trackNext.setOnAction(e -> {
            if (currentSong != null && allSongs.size() > 1) {
                int idx     = allSongs.indexOf(currentSong);
                int nextIdx = (idx + 1) % allSongs.size();
                Song newSong = allSongs.get(nextIdx);

                // === 修改开始 ===
                // 直接使用 Song 对象的封面图片
                playDiscChangeAnimation(newSong.getCoverImage());
                // === 修改结束 ===

                loadSong(newSong);
                mediaPrepared = false;
                prepareAndPlayCurrentSong(
                        bottomProgressBar,
                        bottomCurrentTimeLabel,
                        bottomTotalTimeLabel
                );
                mediaPrepared = true;
                isPlaying = true;
                applyFadeSwitch(playPauseButton, "\ue690", 28);
            }
        });

        // 9）“歌单”按钮：淡入弹出播放列表
        listBtn.setOnAction(e -> {
            playlistOverlay.setOpacity(0);
            playlistOverlay.setVisible(true);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(300), playlistOverlay);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });

        // —— 10）把 控制按钮 放中间 ——
        HBox controls = new HBox(10,
                trackPrev,
                prev,
                playPauseButton,
                next,
                trackNext
        );
        controls.setAlignment(Pos.CENTER);

        // —— 11）右侧放“音量”和“歌单”图标 ——
        HBox rightBtns = new HBox(10, volumeBtn, listBtn);
        rightBtns.setAlignment(Pos.CENTER_RIGHT);

        // —— 12）底部容器：中间放 controls，右侧放 rightBtns ——
        BorderPane bp = new BorderPane();
        bp.setCenter(controls);
        bp.setRight(rightBtns);
        bp.setPadding(new Insets(5, 15, 5, 15));

        // —— 13）合并 进度条 + 时间 + 按钮 ——
        HBox progressBox = new HBox(bottomProgressBar);
        progressBox.setAlignment(Pos.CENTER);

        return new VBox(progressBox, timeBox, bp);
    }

    // 在类中添加新方法
    private void playDiscChangeAnimation(Image newImage) {
        // 1. 获取唱片容器
        StackPane discContainer = (StackPane) localPane.getCenter();

        // 2. 暂停旋转动画
        RotateTransition rt = (RotateTransition) discContainer.getUserData();
        rt.pause();

        // 3. 创建新唱片视图
        ImageView newDiscView = new ImageView(newImage);
        newDiscView.setPreserveRatio(true);
        newDiscView.setFitWidth(240);
        newDiscView.setFitHeight(240);

        // 4. 创建临时裁剪
        Circle tempClip = new Circle(120);
        tempClip.setCenterX(120);
        tempClip.setCenterY(120);
        newDiscView.setClip(tempClip);

        // 5. 设置初始位置（右侧外部）
        newDiscView.setTranslateX(300);
        newDiscView.setOpacity(0.8);

        // 6. 获取动画容器并添加新唱片
        StackPane animationContainer = (StackPane) discContainer.getChildren().get(0);
        animationContainer.getChildren().add(newDiscView);

        // 7. 创建动画
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(400), newDiscView);
        slideIn.setToX(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition parallelTransition = getParallelTransition(newDiscView, slideIn);

        parallelTransition.play();
    }

    private ParallelTransition getParallelTransition(ImageView newDiscView, TranslateTransition slideIn) {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), newDiscView);
        fadeIn.setToValue(1.0);

        ParallelTransition parallelTransition = new ParallelTransition(slideIn, fadeIn);

        parallelTransition.setOnFinished(e -> {
            // 1. 获取动画容器
            StackPane animationContainer = (StackPane) ((StackPane) localPane.getCenter()).getChildren().get(0);

            // 2. 找到原始唱片容器（包含旧唱片的 StackPane）
            StackPane originalDiscContainer = null;
            for (Node node : animationContainer.getChildren()) {
                if (node instanceof StackPane && node != newDiscView) {
                    // 排除新添加的临时唱片视图
                    originalDiscContainer = (StackPane) node;
                    break;
                }
            }

            if (originalDiscContainer != null && originalDiscContainer.getChildren().size() > 0) {
                // 3. 从原始唱片容器中移除旧唱片
                Node oldDisc = originalDiscContainer.getChildren().get(0);
                if (oldDisc instanceof ImageView) {
                    ((ImageView) oldDisc).setClip(null);
                }
                originalDiscContainer.getChildren().clear();

                // 4. 添加新唱片到原始容器
                originalDiscContainer.getChildren().add(newDiscView);
            }

            // 5. 移除动画容器中的临时新唱片视图
            animationContainer.getChildren().remove(newDiscView);

            // 6. 更新为新唱片视图，并设置共享裁剪圆
            discImageView = newDiscView;
            discImageView.setClip(discClipCircle);

            // 7. 继续旋转动画
            RotateTransition rotate = (RotateTransition) ((StackPane) localPane.getCenter()).getUserData();
            if (rotate != null) {
                rotate.play();
            }
        });
        return parallelTransition;
    }

    /**
     * “淡出 → 切换文本 → 淡入” 动画
     * @param btn         需要切换图标的 Button
     * @param newUnicode  切换后的 Unicode 文本 (如 "\ue690")
     * @param fontSize    切换后的字体大小
     */
    private void applyFadeSwitch(Button btn, String newUnicode, double fontSize) {
        FadeTransition fadeOut = new FadeTransition(Duration.millis(150), btn);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(ev -> {
            btn.setText(newUnicode);
            btn.setFont(Font.font("iconfont", fontSize));
            FadeTransition fadeIn = new FadeTransition(Duration.millis(150), btn);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        fadeOut.play();
    }

    /** 创建音量控制浮层 - 透明背景，音量条在按钮正上方 **/
    private StackPane createVolumeControl() {
        // 音量滑动条
        volumeSlider = new Slider(0, 100, 50);
        volumeSlider.setOrientation(Orientation.VERTICAL);
        volumeSlider.setPrefHeight(120); // 高度保持不变
        volumeSlider.setPrefWidth(24);   // 宽度适中
        volumeSlider.setShowTickLabels(false);
        volumeSlider.setShowTickMarks(false);
        volumeSlider.setSnapToTicks(true);

        // 自定义样式 - 淡蓝色填充效果
        volumeSlider.setStyle(
                "-track-color: linear-gradient(to top, #4FC3F7, #B3E5FC);" +
                        "-thumb-color: #29B6F6;" +
                        "-fx-background-color: transparent;"
        );

        // 音量值改变事件
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (vlcPlayer != null) {
                vlcPlayer.audio().setVolume(newVal.intValue());
            }
        });

        // 滑动条容器 - 透明背景，但确保音量条可见
        VBox sliderBox = new VBox(volumeSlider);
        sliderBox.setAlignment(Pos.CENTER);
        sliderBox.setPadding(new Insets(10, 5, 10, 5)); // 适当的内边距确保滑块可见
        sliderBox.setStyle(
                "-fx-background-color: rgba(255,255,255,0.1);" + // 轻微背景确保滑块可见
                        "-fx-background-radius: 8;" +
                        "-fx-border-radius: 8;" +
                        "-fx-border-color: rgba(179,229,252,0.5);" + // 淡蓝色边框
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.2), 5, 0, 0, 2);"
        );

        // 创建浮层容器
        StackPane overlay = new StackPane(sliderBox);
        overlay.setAlignment(Pos.BOTTOM_RIGHT);
        overlay.setStyle("-fx-background-color: transparent;");

        // 添加点击外部关闭功能
        overlay.setOnMouseClicked(e -> {
            if (!sliderBox.getBoundsInParent().contains(e.getX(), e.getY())) {
                hideVolumeControl();
            }
        });

        // 定位在音量按钮正上方
        StackPane.setAlignment(overlay, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(overlay, new Insets(0, 0, 45, 0)); // 紧贴按钮上方

        return overlay;
    }

    /** 显示/隐藏音量控制 **/
    private void toggleVolumeControl() {
        if (isVolumeControlVisible) {
            hideVolumeControl();
        } else {
            showVolumeControl();
        }
    }

    /** 显示音量控制（带动画） **/
    private void showVolumeControl() {
        isVolumeControlVisible = true;
        volumeOverlay.setVisible(true);

        // 设置初始状态（透明且向下偏移）
        volumeOverlay.setOpacity(0);
        volumeOverlay.setTranslateY(20);

        // 创建并行动画：淡入 + 上移
        ParallelTransition pt = new ParallelTransition();
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), volumeOverlay);
        fadeIn.setToValue(1.0);

        TranslateTransition moveUp = new TranslateTransition(Duration.millis(200), volumeOverlay);
        moveUp.setToY(0);

        pt.getChildren().addAll(fadeIn, moveUp);
        pt.play();
    }

    /** 隐藏音量控制（带动画） **/
    private void hideVolumeControl() {
        isVolumeControlVisible = false;

        // 创建并行动画：淡出 + 下移
        ParallelTransition pt = new ParallelTransition();
        FadeTransition fadeOut = new FadeTransition(Duration.millis(200), volumeOverlay);
        fadeOut.setToValue(0);

        TranslateTransition moveDown = new TranslateTransition(Duration.millis(200), volumeOverlay);
        moveDown.setToY(20);

        pt.getChildren().addAll(fadeOut, moveDown);
        pt.setOnFinished(e -> volumeOverlay.setVisible(false));
        pt.play();
    }

    /** 强制更新进度条和时间显示 */
    private void updateProgressBar() {
        if (vlcPlayer != null && bottomProgressBar != null) {
            try {
                long currentMillis = vlcPlayer.status().time();
                long totalMillis = currentTotalDuration > 0 ?
                        currentTotalDuration :
                        vlcPlayer.media().info().duration();

                // 更新进度条
                if (totalMillis > 0) {
                    double frac = (double) currentMillis / totalMillis;
                    frac = Math.min(1.0, Math.max(0.0, frac));
                    bottomProgressBar.setProgress(frac);
                }

                // 更新时间标签
                if (bottomCurrentTimeLabel != null) {
                    bottomCurrentTimeLabel.setText(formatDuration(javafx.util.Duration.millis(currentMillis)));
                }

            } catch (Exception e) {
                System.err.println("更新进度出错: " + e.getMessage());
            }
        }
    }


    /**
     * 创建播放列表浮层：遮罩 + 背景图片 + 列表，紧贴右侧
     */
    private StackPane createPlaylistOverlay() {
        // 遮罩层：整个 bodyRoot 下半透明黑，点击空白处可收起
        Region mask = new Region();
        mask.setStyle("-fx-background-color: rgba(0, 0, 0, 0.5);");
        mask.setOnMouseClicked(e -> {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), playlistOverlay);
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(evt -> playlistOverlay.setVisible(false));
            fadeOut.play();
        });
        mask.prefWidthProperty().bind(bodyRoot.widthProperty());
        mask.prefHeightProperty().bind(bodyRoot.heightProperty());

        // 播放列表的 ListView
        listView = new ListView<>();
        for (Song s : allSongs) {
            String artist = s.getArtist();
            if (artist == null || artist.isBlank()) {
                artist = "无名";
            }
            listView.getItems().add(s.getTitle() + " - " + artist);
        }
        listView.setStyle("-fx-background-color: transparent; -fx-control-inner-background: transparent;");
        VBox.setVgrow(listView, Priority.ALWAYS);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: white; -fx-background-color: transparent;");
                    setFont(new Font("Arial", 14));
                }
            }
        });

        listView.setOnMouseClicked(evt -> {
            if (evt.getClickCount() == 2) {
                int idx = listView.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    loadSong(allSongs.get(idx));
                    mediaPrepared = false;
                    prepareAndPlayCurrentSong(bottomProgressBar, bottomCurrentTimeLabel, bottomTotalTimeLabel);
                    mediaPrepared = true;
                    isPlaying = true;
                    RotateTransition rt = (RotateTransition) ((StackPane) localPane.getCenter()).getUserData();
                    rt.play();
                    playPauseButton.setText("\ue690");
                    playPauseButton.setFont(Font.font("iconfont", 28));
                }
                playlistOverlay.setVisible(false);
            }
        });

        // 标题：播放队列
        VBox content = new VBox(10);
        Label title = new Label("播放队列");
        title.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
        title.setPadding(new Insets(15, 15, 10, 15));

        content.getChildren().addAll(title, listView);
        content.setAlignment(Pos.TOP_CENTER);
        content.setPadding(new Insets(0, 0, 20, 0));
        content.setMaxWidth(Region.USE_PREF_SIZE);

        // 背景图片 ImageView
        ImageView bgView = new ImageView(new Image(LIST_BG_IMG));
        bgView.setPreserveRatio(false);

        // 容器：背景图片 + 列表内容
        StackPane listContainer = new StackPane(bgView, content);
        listContainer.setAlignment(Pos.TOP_CENTER);

        // 限制宽高
        listContainer.prefWidthProperty().bind(bodyRoot.widthProperty().multiply(0.25));
        listContainer.prefHeightProperty().bind(bodyRoot.heightProperty());

        bgView.fitWidthProperty().bind(listContainer.prefWidthProperty());
        bgView.fitHeightProperty().bind(listContainer.prefHeightProperty());

        content.prefWidthProperty().bind(listContainer.prefWidthProperty().subtract(20));
        listView.prefWidthProperty().bind(content.prefWidthProperty().subtract(30));

        AnchorPane anchorPane = new AnchorPane(listContainer);
        AnchorPane.setRightAnchor(listContainer, 0.0);
        AnchorPane.setTopAnchor(listContainer, 0.0);
        AnchorPane.setBottomAnchor(listContainer, 0.0);
        anchorPane.setPickOnBounds(false);

        StackPane overlayPane = new StackPane(mask, anchorPane);
        overlayPane.setPickOnBounds(true);
        overlayPane.setAlignment(Pos.CENTER_RIGHT);

        return overlayPane;
    }




    private void loadSong(Song song) {
        Image cover = song.getCoverImage();
        if (cover != null) {
            discImageView.setImage(cover);
        } else {
            discImageView.setImage(new Image(DISC_IMG));
        }
        if (vlcPlayer != null && vlcPlayer.status().isPlaying()) {
            vlcPlayer.controls().stop();
        }
        mediaPrepared = false;

        currentSong = song;
        currentLyricIndex = 0;

        songLabel.setText(song.getTitle());
        String artist = song.getArtist();
        artistLabel.setText((artist == null || artist.isBlank()) ? "无名" : artist);

        List<LyricLine> lyrics = song.getLyrics();
        if (lyrics != null && !lyrics.isEmpty()) {
            // “当前行”—— 首句，黑色
            prevLyricLabel.setText(lyrics.get(0).getText());
            prevLyricLabel.setFont(new Font("Arial", 18));
            prevLyricLabel.setTextFill(Color.BLACK); // 改为黑色
            prevLyricLabel.setOpacity(1.0);

            // “下一行”—— 第二句（若存在），黑色半透明
            if (lyrics.size() > 1) {
                nextLyricLabel.setText(lyrics.get(1).getText());
                nextLyricLabel.setFont(new Font("Arial", 12));
                nextLyricLabel.setTextFill(Color.rgb(0, 0, 0, 0.5)); // 半透明黑
                nextLyricLabel.setOpacity(1.0);
            } else {
                nextLyricLabel.setText("");
                nextLyricLabel.setOpacity(1.0);
            }
        } else {
            // 纯音乐
            prevLyricLabel.setText("纯音乐，请欣赏");
            prevLyricLabel.setFont(new Font("Arial", 18));
            prevLyricLabel.setTextFill(Color.BLACK);
            prevLyricLabel.setOpacity(1.0);
            nextLyricLabel.setText("");
            nextLyricLabel.setOpacity(1.0);
        }

        // 全屏歌词清空后重新添加，默认都用黑色
        fullLyricsBox.getChildren().clear();
        if (lyrics != null && !lyrics.isEmpty()) {
            for (LyricLine line : lyrics) {
                Label lbl = new Label(line.getText());
                lbl.setFont(new Font("Arial", 16));
                lbl.setTextFill(Color.BLACK); // 默认黑色
                fullLyricsBox.getChildren().add(lbl);
            }
        } else {
            Label lbl = new Label("纯音乐，请欣赏");
            lbl.setFont(new Font("Arial", 16));
            lbl.setTextFill(Color.BLACK);
            fullLyricsBox.getChildren().add(lbl);
        }
        // 重置唱片旋转角度
        StackPane discContainer = (StackPane) localPane.getCenter();
        discContainer.setRotate(0);

        // 停止并重置旋转动画
        RotateTransition rt = (RotateTransition) discContainer.getUserData();
        if (rt != null) {
            rt.stop();
        }
    }


    /**
     * 准备并播放 currentSong，同时绑定进度条与时间显示
     *
     * @param progressBar      当前底部的 ProgressBar 控件
     * @param currentTimeLabel 底部显示“当前播放时间”的 Label
     * @param totalTimeLabel   底部显示“总时长”的 Label
     */
    private void prepareAndPlayCurrentSong(ProgressBar progressBar,
                                           Label currentTimeLabel,
                                           Label totalTimeLabel) {
        if (currentSong == null) return;

        File songFile = currentSong.getFile();
        if (!songFile.exists() || !songFile.isFile()) {
            System.err.println("文件不存在或不是有效文件: " + songFile.getAbsolutePath());
            return;
        }

        // 如果在播放，则先停止
        if (vlcPlayer.status().isPlaying()) {
            vlcPlayer.controls().stop();
        }
        // 停掉旧的进度定时器
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
        // 移除旧的媒体监听
        if (currentMediaListener != null) {
            vlcPlayer.events().removeMediaPlayerEventListener(currentMediaListener);
            currentMediaListener = null;
        }

        String mediaPath = songFile.toURI().toString();
        if (mediaPath.startsWith("file:/") && !mediaPath.startsWith("file:///")) {
            mediaPath = mediaPath.replaceFirst("^file:/+", "file:///");
        }
        System.out.println("播放路径: " + mediaPath);

        // 新建媒体监听器
        currentMediaListener = new MediaPlayerEventAdapter() {
            private boolean firstTime = true;

            @Override
            public void playing(MediaPlayer mp) {
                if (firstTime) {
                    firstTime = false;
                    currentTotalDuration = mp.media().info().duration();
                    Platform.runLater(() -> {
                        totalTimeLabel.setText(formatDuration(Duration.millis(currentTotalDuration)));
                        progressBar.setProgress(0);
                    });
                }
            }

            @Override
            public void finished(MediaPlayer mp) {
                Platform.runLater(() -> {
                    isPlaying = false;
                    // 停掉进度更新
                    if (progressTimer != null) {
                        progressTimer.stop();
                        progressTimer = null;
                    }
                    // 切换回"播放"图标
                    playPauseButton.setText("\ue692");
                    playPauseButton.setFont(Font.font("iconfont", 28));

                    // 停止转盘
                    RotateTransition rt = (RotateTransition) ((StackPane) localPane.getCenter()).getUserData();
                    if (rt != null) {
                        rt.stop();
                        // 重置旋转角度
                        ((StackPane) localPane.getCenter()).setRotate(0);
                    }

                    // 保留总时长信息，只重置进度和当前时间
                    bottomProgressBar.setProgress(0);
                    bottomCurrentTimeLabel.setText("00:00");

                    // 不要重置总时长标签，保持显示歌曲的实际时长
                    // bottomTotalTimeLabel.setText("00:00"); // 移除这行

                    currentLyricIndex = 0;
                    lastVvalue = 0.0;
                    fullPane.setVvalue(0.0);

                    // 重置歌词显示状态
                    resetLyricsDisplay();

                    // 重置媒体位置到开头
                    if (vlcPlayer != null) {
                        vlcPlayer.controls().setTime(0);
                    }

                    // 重置媒体准备状态，以便下次点击播放时重新准备
                    mediaPrepared = false;
                });
            }

            /**
             * 重置歌词显示到初始状态（显示第一句歌词）
             */
            private void resetLyricsDisplay() {
                if (currentSong == null) return;

                List<LyricLine> lyrics = currentSong.getLyrics();

                // 重置局部歌词显示
                if (lyrics != null && !lyrics.isEmpty()) {
                    // 显示第一句歌词
                    prevLyricLabel.setText(lyrics.get(0).getText());
                    prevLyricLabel.setFont(new Font("Arial", 18));
                    prevLyricLabel.setTextFill(Color.BLACK);
                    prevLyricLabel.setOpacity(1.0);

                    // 显示第二句歌词（如果有）
                    if (lyrics.size() > 1) {
                        nextLyricLabel.setText(lyrics.get(1).getText());
                        nextLyricLabel.setFont(new Font("Arial", 12));
                        nextLyricLabel.setTextFill(Color.rgb(0, 0, 0, 0.5));
                        nextLyricLabel.setOpacity(1.0);
                    } else {
                        nextLyricLabel.setText("");
                        nextLyricLabel.setOpacity(1.0);
                    }
                } else {
                    // 纯音乐提示
                    prevLyricLabel.setText("纯音乐，请欣赏");
                    prevLyricLabel.setFont(new Font("Arial", 18));
                    prevLyricLabel.setTextFill(Color.BLACK);
                    prevLyricLabel.setOpacity(1.0);
                    nextLyricLabel.setText("");
                    nextLyricLabel.setOpacity(1.0);
                }

                // 重置全屏歌词高亮状态
                if (fullLyricsBox != null) {
                    for (Node node : fullLyricsBox.getChildren()) {
                        if (node instanceof Label) {
                            Label label = (Label) node;
                            label.setStyle("-fx-text-fill: black; -fx-font-weight: normal;");
                        }
                    }

                    // 高亮第一行歌词
                    if (!fullLyricsBox.getChildren().isEmpty()) {
                        ((Label) fullLyricsBox.getChildren().get(0)).setStyle(
                                "-fx-text-fill: rgba(255,100,100,0.6); -fx-font-weight:bold;"
                        );
                    }
                }
            }
            @Override
            public void error(MediaPlayer mp) {
                System.err.println("媒体播放错误");
                Platform.runLater(() -> progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS));
            }
        };
        vlcPlayer.events().addMediaPlayerEventListener(currentMediaListener);

        vlcPlayer.media().prepare(mediaPath);
        vlcPlayer.controls().play();

        // 开始转盘动画
        RotateTransition rtDisc = (RotateTransition) ((StackPane) localPane.getCenter()).getUserData();
        if (rtDisc != null) rtDisc.play();

        // 初始化歌词索引与全屏滚动位置
        currentLyricIndex = 0;
        lastVvalue = 0.0;
        fullPane.setVvalue(0.0);

        // 进度更新定时器：每 200ms 更新一次
        progressTimer = new Timeline(
                new KeyFrame(Duration.millis(200), evt -> {
                    if (!isDragging && vlcPlayer.status().isPlaying()) {
                        try {
                            long currentMillis = vlcPlayer.status().time();
                            long totalMillis = currentTotalDuration > 0
                                    ? currentTotalDuration
                                    : vlcPlayer.media().info().duration();

                            // 更新进度条
                            if (totalMillis > 0) {
                                double frac = (double) currentMillis / totalMillis;
                                frac = Math.min(1.0, Math.max(0.0, frac));
                                progressBar.setProgress(frac);
                            } else {
                                progressBar.setProgress(0);
                            }
                            currentTimeLabel.setText(formatDuration(Duration.millis(currentMillis)));

                            // —— 局部歌词动画 ——
                            List<LyricLine> localList = currentSong.getLyrics();
                            if (localList != null && !localList.isEmpty()) {
                                int oldIndex = currentLyricIndex;
                                while (currentLyricIndex < localList.size() - 1
                                        && currentMillis >= localList.get(currentLyricIndex + 1).getTimeInMillis()) {
                                    currentLyricIndex++;
                                }
                                if (currentLyricIndex != oldIndex) {
                                    // 当前行淡入效果
                                    prevLyricLabel.setText(localList.get(currentLyricIndex).getText());
                                    prevLyricLabel.setFont(new Font("Arial", 18));
                                    prevLyricLabel.setTextFill(Color.rgb(255, 100, 100, 0.7));
                                    prevLyricLabel.setOpacity(0.0);
                                    FadeTransition fadeIn = new FadeTransition(Duration.millis(300), prevLyricLabel);
                                    fadeIn.setFromValue(0.0);
                                    fadeIn.setToValue(1.0);
                                    fadeIn.play();
                                    // 下一行显示
                                    if (currentLyricIndex + 1 < localList.size()) {
                                        nextLyricLabel.setText(localList.get(currentLyricIndex + 1).getText());
                                        nextLyricLabel.setFont(new Font("Arial", 12));
                                        nextLyricLabel.setTextFill(Color.rgb(0, 0, 0, 0.7));
                                        nextLyricLabel.setOpacity(1.0);
                                    } else {
                                        nextLyricLabel.setText("");
                                        nextLyricLabel.setOpacity(1.0);
                                    }
                                }
                            }
                            // —— 全屏滚动与样式更新 ——
                            List<LyricLine> fullLyricList = currentSong.getLyrics();
                            if (fullLyricList != null && !fullLyricList.isEmpty()) {
                                int totalLines = fullLyricsBox.getChildren().size();
                                double targetV = (double) currentLyricIndex / (totalLines - 1);
                                Timeline scrollAnim = new Timeline(
                                        new KeyFrame(Duration.ZERO,
                                                new KeyValue(fullPane.vvalueProperty(), lastVvalue)
                                        ),
                                        new KeyFrame(Duration.millis(300),
                                                new KeyValue(fullPane.vvalueProperty(), targetV)
                                        )
                                );
                                scrollAnim.play();
                                lastVvalue = targetV;

                                for (int i = 0; i < fullLyricList.size(); i++) {
                                    Label lbl = (Label) fullLyricsBox.getChildren().get(i);
                                    if (i == currentLyricIndex) {
                                        lbl.setStyle("-fx-text-fill: rgba(255,100,100,0.6); -fx-font-weight:bold;");
                                    } else {
                                        lbl.setStyle("-fx-text-fill: rgba(0,0,0,0.6); -fx-font-weight: normal;");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("更新进度出错: " + e.getMessage());
                        }
                    }
                })
        );
        progressTimer.setCycleCount(Animation.INDEFINITE);
        progressTimer.play();
    }


    /**
     * 辅助方法：将 javafx.util.Duration 转成 "mm:ss" 格式
     */
    private String formatDuration(Duration d) {
        int totalSeconds = (int) Math.floor(d.toSeconds());
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /** 工具方法：从 classpath 加载资源 **/
    private static String getResource(String path) {
        URL url = MainWindow.class.getResource(path);
        if (url == null) {
            throw new RuntimeException("资源不存在：" + path);
        }
        return url.toExternalForm();
    }
}
