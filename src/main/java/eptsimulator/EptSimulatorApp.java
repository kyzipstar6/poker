package eptsimulator;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.awt.Toolkit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.shape.Ellipse;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.util.Duration;

public class EptSimulatorApp extends Application {
    private static final int STARTING_STACK = 25_000;
    private static final int ACTION_SECONDS = 20;
    private static final int TIME_BANK_SECONDS = 30;
    private static final int INITIAL_TIME_BANK_CHIPS = 5;
    private static final int MAX_TIME_BANK_CHIPS = 10;
    private static final int[] SMALL_BLINDS = {100, 100, 200, 300, 400, 600, 800, 1_000, 1_500, 2_000};
    private static final int[] BIG_BLINDS = {200, 300, 400, 600, 800, 1_200, 1_600, 2_000, 3_000, 4_000};
    private static final int[] PAYOUT_TENTHS = {200, 150, 110, 80, 70, 60, 50, 45, 42, 40, 38, 35, 32, 25, 23};
    private static final String[] NAMES = {"M. Laurent", "Elena V.", "Jonas K.", "Sofia R.", "A. Moretti", "Tom W.", "Nina P.", "D. Fischer"};
        private static final List<EventOption> EVENTS = List.of(
            new EventOption("EPT Cup  ·  €550", 500, 50, 25_000),
            new EventOption("EPT High Roller  ·  €1,100", 1_000, 100, 30_000),
            new EventOption("EPT Main Event  ·  €5,300", 5_000, 300, 30_000));

    private final Random random = new Random();
    private final List<Player> opponents = new ArrayList<>();
    private final List<RemoteTable> remoteTables = new ArrayList<>();
    private final List<Card> board = new ArrayList<>();
    private final List<Card> deck = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private final List<Card> heroCards = new ArrayList<>();
    private final List<Boolean> heroDiscards = new ArrayList<>();
    private final List<Label> boardSlots = new ArrayList<>();
    private final List<Label> heroSlots = new ArrayList<>();
    private final Deque<HandReplay> recentHands = new ArrayDeque<>();
    private final Deque<HandReplay> hotHands = new ArrayDeque<>();
    private List<Player> actionPlayers = List.of();
    private EventOption eventOption;
    private RemoteTable observedTable;
    private Difficulty difficulty = Difficulty.MEDIUM;
    private GameMode gameMode = GameMode.NO_LIMIT_HOLDEM;
    private int fieldSize = 90;
    private int blindIntervalSeconds = 20 * 60;
    private int nextRemoteTableNumber = 2;
    private int timeBankChips = INITIAL_TIME_BANK_CHIPS;
    private int actionSecondsRemaining = ACTION_SECONDS;
    private Player hero;
    private int levelIndex, secondsInLevel, handNumber, buttonSeat, pot, heroStreetBet, currentBet, actionIndex;
    private boolean handOver = true, heroFolded, heroTurn, soundEnabled = true, replaySaved, drawPending, drawCompleted;
    private boolean timeBankEarnedThisHand;
    private String actionPrompt = "WAITING FOR TOURNAMENT";
    private Label blindValue, levelClock, levelValue, fieldValue, prizePoolValue, firstPrizeValue;
    private Label handLabel, statusLabel, heroStackLabel, heroNameLabel, actionHint, opponentCount;
    private Label payoutLabel;
    private Label actionTimerLabel, timeBankLabel;
    private VBox rankingList;
    private int lastDisplayedFieldSize = -1;
    private final Map<Player, Integer> displayedStacks = new IdentityHashMap<>();
    private VBox opponentRail;
    private VBox localPlayerList;
    private VBox remoteTableList;
    private VBox hotHandList;
    private VBox tournamentRail;
    private Button checkCallButton, raiseButton, foldButton, startButton;
    private Button tournamentInfoButton, replayButton, soundButton, localTableButton;
    private Button timeBankButton;
    private Slider raiseSlider;
    private TablePane tablePane;
    private Stage primaryStage;
    private Timeline tournamentClock;
    private Timeline remoteHandClock;
    private Timeline autoDealCountdown;
    private PauseTransition actionDelay;
    private Timeline actionClock;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        hero = new Player("You", STARTING_STACK);
        for (String name : NAMES) opponents.add(new Player(name, STARTING_STACK));
        showRegistration(stage);
    }

    private void startTournament(Stage stage, EventOption option, GameMode selectedMode, Difficulty selectedDifficulty,
                                 int selectedFieldSize, int blindMinutes) {
        if (tournamentClock != null) tournamentClock.stop();
        if (remoteHandClock != null) remoteHandClock.stop();
        eventOption = option;
        gameMode = selectedMode;
        difficulty = selectedDifficulty;
        fieldSize = selectedFieldSize;
        blindIntervalSeconds = blindMinutes * 60;
        recentHands.clear(); hotHands.clear();
        timeBankChips = INITIAL_TIME_BANK_CHIPS;
        actionSecondsRemaining = ACTION_SECONDS;
        timeBankEarnedThisHand = false;
        lastDisplayedFieldSize = -1;
        displayedStacks.clear();
        hero.stack = option.startingStack();
        hero.stackHistory.clear(); hero.stackHistory.add(hero.stack);
        List<Integer> tableSizes = balancedTableSizes(fieldSize);
        int tableCount = tableSizes.size();
        int localSeatCount = tableSizes.get(0);
        while (opponents.size() > localSeatCount - 1) opponents.remove(opponents.size() - 1);
        while (opponents.size() < localSeatCount - 1) opponents.add(new Player("Seat " + (opponents.size() + 2), option.startingStack()));
        for (Player player : opponents) {
            player.stack = option.startingStack(); player.stackHistory.clear(); player.stackHistory.add(player.stack);
        }
        remoteTables.clear(); observedTable = null;
        nextRemoteTableNumber = 2;
        int playerNumber = 1;
        for (int tableIndex = 1; tableIndex < tableCount; tableIndex++) {
            int tableSize = tableSizes.get(tableIndex);
            List<Player> tablePlayers = new ArrayList<>();
            for (int seat = 0; seat < tableSize; seat++) {
                tablePlayers.add(new Player(String.format("Player %03d", playerNumber++), option.startingStack()));
            }
            remoteTables.add(new RemoteTable(nextRemoteTableNumber++, tablePlayers));
        }
        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-shell");
        root.setTop(buildHeader());
        root.setCenter(buildMainArea());
        root.setBottom(buildActionDock());
        Scene scene = new Scene(root, 1200, 700);
        applyTheme(scene);
        stage.setTitle("EPT Simulator | Tournament Room");
        stage.setMinWidth(1100);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.show();

        tournamentClock = new Timeline(new KeyFrame(Duration.seconds(1), event -> tickClock()));
        tournamentClock.setCycleCount(Timeline.INDEFINITE);
        tournamentClock.play();
        remoteHandClock = new Timeline(new KeyFrame(Duration.seconds(12), event -> simulateRemoteTables()));
        remoteHandClock.setCycleCount(Timeline.INDEFINITE);
        remoteHandClock.play();
        updateTournamentInfo();
        refreshTable();
        statusLabel.setText("First hand begins in 5 seconds.");
        scheduleAutoDeal();
    }

    private void showRegistration(Stage stage) {
        Label eyebrow = new Label("MONTE CARLO  ·  EUROPEAN POKER TOUR");
        eyebrow.setStyle("-fx-text-fill: #d0b878; -fx-font-size: 11px; -fx-font-weight: bold;");
        Label title = new Label("Choose your event");
        title.setStyle("-fx-text-fill: #f0efe8; -fx-font-size: 34px; -fx-font-weight: bold;");
        Label subtitle = new Label("Select a buy-in to register for the tournament.");
        subtitle.setStyle("-fx-text-fill: #9ca89f; -fx-font-size: 14px;");
        ComboBox<EventOption> eventPicker = new ComboBox<>();
        eventPicker.getItems().setAll(EVENTS);
        eventPicker.setValue(EVENTS.get(2));
        eventPicker.setMaxWidth(Double.MAX_VALUE);
        eventPicker.setStyle("-fx-font-size: 14px;");
        ComboBox<GameMode> modePicker = new ComboBox<>();
        modePicker.getItems().setAll(GameMode.values()); modePicker.setValue(GameMode.NO_LIMIT_HOLDEM);
        modePicker.setMaxWidth(Double.MAX_VALUE);
        ComboBox<Difficulty> difficultyPicker = new ComboBox<>();
        difficultyPicker.getItems().setAll(Difficulty.values()); difficultyPicker.setValue(Difficulty.MEDIUM);
        difficultyPicker.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> fieldPicker = new Spinner<>(9, 500, 90, 9);
        fieldPicker.setEditable(true); fieldPicker.setMaxWidth(Double.MAX_VALUE);
        Spinner<Integer> blindPicker = new Spinner<>(5, 60, 20, 5);
        blindPicker.setEditable(true); blindPicker.setMaxWidth(Double.MAX_VALUE);
        Label details = new Label();
        details.setStyle("-fx-text-fill: #c6d0c8; -fx-font-size: 13px; -fx-line-spacing: 8px;");
        Runnable refreshDetails = () -> {
            EventOption selected = eventPicker.getValue();
            details.setText(String.format("BUY-IN     %s total  ·  %s prize pool entry%nSTARTING STACK     %s chips%nFIELD     %d players  ·  estimated prize pool %s",
                formatMoney(selected.buyIn() + selected.fee()), formatMoney(selected.buyIn()),
                formatChips(selected.startingStack()), fieldPicker.getValue(), formatMoney(fieldPicker.getValue() * selected.buyIn())));
        };
        eventPicker.valueProperty().addListener((observable, oldValue, newValue) -> refreshDetails.run());
        fieldPicker.valueProperty().addListener((observable, oldValue, newValue) -> refreshDetails.run());
        refreshDetails.run();
        Button startButton = new Button("REGISTER & START");
        startButton.getStyleClass().add("primary-button");
        startButton.setOnAction(event -> startTournament(stage, eventPicker.getValue(), modePicker.getValue(),
            difficultyPicker.getValue(), fieldPicker.getValue(), blindPicker.getValue()));
        Button loadButton = new Button("LOAD SAVED TOURNAMENT");
        loadButton.getStyleClass().add("utility-button"); loadButton.setMaxWidth(Double.MAX_VALUE);
        loadButton.setOnAction(event -> loadGame());
        VBox content = new VBox(11, eyebrow, title, subtitle,
            new Label("TOURNAMENT"), eventPicker,
            new Label("POKER FORMAT"), modePicker,
            new Label("AI DIFFICULTY"), difficultyPicker,
            new Label("ENTRANTS  ·  BLIND LEVEL MINUTES"), new HBox(10, fieldPicker, blindPicker),
                details, startButton, loadButton);
        content.setMaxWidth(520);
        content.setPadding(new Insets(34));
        content.setStyle("-fx-background-color: #18221e; -fx-border-color: #53664e; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4;");
        BorderPane lobby = new BorderPane(content);
        lobby.setStyle("-fx-background-color: #101916;");
        BorderPane.setAlignment(content, Pos.CENTER);
        Scene scene = new Scene(lobby, 1000, 680);
        applyTheme(scene);
        stage.setTitle("EPT Simulator | Event Registration");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.show();
    }

    private void applyTheme(Scene scene) {
        String encodedCss = URLEncoder.encode(css(), StandardCharsets.UTF_8).replace("+", "%20");
        scene.getStylesheets().add("data:text/css," + encodedCss);
    }
    String css(){
        return """
                .root { -fx-font-family: 'Segoe UI'; -fx-background-color: #101916; -fx-text-fill: #f0efe8; }
.app-shell { -fx-background-color: #101916; }
.top-bar { -fx-background-color: #18221e; -fx-border-color: #33413b; -fx-border-width: 0 0 1 0; }
.brand-mark { -fx-background-color: #d3b878; -fx-text-fill: #14221d; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 26px; -fx-font-weight: 700; -fx-alignment: center; -fx-min-width: 38px; -fx-min-height: 38px; -fx-background-radius: 2; }
.brand-name { -fx-text-fill: #f3f0e6; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 20px; -fx-font-weight: 700; }
.brand-caption, .eyebrow, .section-title, .stat-key, .dock-label { -fx-text-fill: #9ca89f; -fx-font-size: 10px; -fx-font-weight: 700; }
.brand-caption, .eyebrow { -fx-text-fill: #cfb777; -fx-font-size: 9px; }
.header-event { -fx-text-fill: #f0efe8; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 20px; -fx-font-weight: 600; }
.live-indicator { -fx-background-color: #a34338; -fx-text-fill: #fff7ed; -fx-font-size: 9px; -fx-font-weight: 700; -fx-padding: 5 8; -fx-background-radius: 2; }
.header-meta { -fx-text-fill: #bcc5be; -fx-font-size: 10px; -fx-font-weight: 600; }
.main-area { -fx-background-color: #101916; }
.left-rail { -fx-padding: 4 18 0 0; -fx-border-color: transparent #33413b transparent transparent; -fx-border-width: 0 1 0 0; }
.rail-section { -fx-padding: 0 0 17 0; -fx-border-color: transparent transparent #293630 transparent; -fx-border-width: 0 0 1 0; }
.section-title { -fx-text-fill: #cfb777; -fx-font-size: 10px; }
.stat-key { -fx-font-size: 9px; -fx-text-fill: #859188; }
.stat-value { -fx-text-fill: #e8e8dd; -fx-font-size: 12px; -fx-font-weight: 600; }
.rail-note { -fx-text-fill: #748279; -fx-font-size: 9px; -fx-line-spacing: 4px; }
.payout-list { -fx-text-fill: #d9ddd4; -fx-font-size: 11px; -fx-line-spacing: 8px; -fx-font-weight: 500; }
.opponent-rail { -fx-padding: 5 0 0 0; }
.section-count { -fx-text-fill: #829188; -fx-font-size: 9px; }
.opponent-row { -fx-padding: 8 6; -fx-border-color: transparent transparent #26322c transparent; -fx-border-width: 0 0 1 0; }
.opponent-avatar { -fx-background-color: #2f4139; -fx-text-fill: #d4bd81; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 15px; -fx-alignment: center; -fx-min-width: 32px; -fx-min-height: 32px; -fx-background-radius: 16; }
.opponent-name { -fx-text-fill: #e0e4dc; -fx-font-size: 11px; -fx-font-weight: 600; }
.opponent-stack { -fx-text-fill: #8f9c92; -fx-font-size: 10px; }
.table-area { -fx-background-color: transparent; }
.felt { -fx-fill: #155647; -fx-stroke: #a7844e; -fx-stroke-width: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.55), 24, 0.18, 0, 12); }
.table-name { -fx-text-fill: #9fc3b2; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 11px; -fx-font-weight: 700; }
.pot-caption { -fx-text-fill: #b5c9bd; -fx-font-size: 9px; -fx-font-weight: 700; }
.pot-amount { -fx-text-fill: #f2d489; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 22px; -fx-font-weight: 700; }
.table-action { -fx-text-fill: #a6beb0; -fx-font-size: 9px; -fx-font-weight: 700; }
.seat { -fx-background-color: rgba(20,32,27,0.94); -fx-border-color: #788b78; -fx-border-width: 1; -fx-background-radius: 4; -fx-border-radius: 4; -fx-padding: 7 8; }
.seat-name { -fx-text-fill: #e8ece4; -fx-font-size: 10px; -fx-font-weight: 600; }
.seat-stack { -fx-text-fill: #d6bd7d; -fx-font-size: 10px; }
.seat-action { -fx-text-fill: #9fc3b2; -fx-font-size: 8px; -fx-font-weight: 700; }
.seat.acting-seat { -fx-border-color: #e6c978; -fx-effect: dropshadow(gaussian,rgba(230,201,120,0.6),12,0.2,0,0); }
.seat.folded-seat { -fx-border-color: #4a514c; }
.revealed-cards { -fx-alignment: center; }
.revealed-card { -fx-background-color: #f4f0e5; -fx-text-fill: #242722; -fx-font-size: 11px; -fx-font-weight: 700; -fx-alignment: center; -fx-min-width: 25px; -fx-min-height: 34px; -fx-background-radius: 3; }
.utility-button { -fx-background-color: #26362f; -fx-text-fill: #d7e0d7; -fx-border-color: #42554a; -fx-border-width: 1; -fx-padding: 8 11; }
.rank-number { -fx-text-fill: #d3b878; -fx-font-size: 10px; -fx-min-width: 22px; }
.ranking-row { -fx-padding: 4 3; -fx-border-color: transparent transparent #293630 transparent; -fx-border-width: 0 0 1 0; -fx-cursor: hand; }
.ranking-row.eliminated-row { -fx-background-color: rgba(211,184,120,0.24); -fx-border-color: #d3b878; }
.action-timer { -fx-text-fill: #e8cf91; -fx-font-size: 18px; -fx-font-weight: 700; -fx-min-width: 42px; }
.time-bank-button { -fx-background-color: #a88642; -fx-text-fill: #161d18; -fx-font-weight: 700; -fx-padding: 9 12; }
.playing-card { -fx-background-color: #f4f0e5; -fx-text-fill: #242722; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 22px; -fx-font-weight: 700; -fx-alignment: center; -fx-min-width: 54px; -fx-min-height: 76px; -fx-background-radius: 4; -fx-border-color: #d4d0c5; -fx-border-width: 1; -fx-border-radius: 4; -fx-effect: dropshadow(gaussian,rgba(0,0,0,0.26),8,0.1,0,3); }
.red-card { -fx-text-fill: #a53c35; }
.card-back { -fx-background-color: #b7a46c; -fx-text-fill: #f5edcf; -fx-border-color: #e0d3a5; }
.card-slot { -fx-background-color: rgba(11,49,40,0.42); -fx-border-color: rgba(208,222,211,0.25); -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-min-width: 54px; -fx-min-height: 76px; }
.hero-stack { -fx-text-fill: #f0d287; -fx-font-size: 12px; -fx-font-weight: 700; }
.hero-name { -fx-text-fill: #d4e2d8; -fx-font-size: 9px; -fx-font-weight: 700; }
.action-dock { -fx-background-color: #18221e; -fx-border-color: #33413b transparent transparent transparent; -fx-border-width: 1 0 0 0; }
.dock-label { -fx-text-fill: #9ba89f; }
.status-message { -fx-text-fill: #e0e5dc; -fx-font-size: 12px; }
.raise-amount { -fx-text-fill: #f0d287; -fx-font-family: 'Bahnschrift SemiCondensed'; -fx-font-size: 17px; -fx-min-width: 48px; -fx-alignment: center-right; }
.slider .track { -fx-background-color: #35463d; -fx-pref-height: 4px; }
.slider .thumb { -fx-background-color: #d2b878; -fx-pref-width: 13px; -fx-pref-height: 13px; }
.button { -fx-cursor: hand; -fx-font-size: 10px; -fx-font-weight: 700; -fx-padding: 12 18; -fx-background-radius: 3; -fx-border-radius: 3; }
.fold-button { -fx-background-color: #27342e; -fx-text-fill: #c3cbc3; -fx-border-color: #435149; -fx-border-width: 1; }
.secondary-button { -fx-background-color: #d4bd81; -fx-text-fill: #19251f; }
.primary-button { -fx-background-color: #b4493d; -fx-text-fill: #fff8ed; }
.button:hover { -fx-effect: dropshadow(gaussian,rgba(219,196,134,0.26),9,0.1,0,0); }
.button:disabled { -fx-opacity: 0.42; -fx-cursor: default; }
                """;
    }
    private Node buildHeader() {
        Label mark = new Label("E"); mark.getStyleClass().add("brand-mark");
        Label brand = new Label("EUROPEAN POKER TOUR"); brand.getStyleClass().add("brand-name");
        Label caption = new Label("TOURNAMENT SIMULATOR"); caption.getStyleClass().add("brand-caption");
        VBox brandText = new VBox(2, brand, caption);
        HBox lockup = new HBox(11, mark, brandText); lockup.setAlignment(Pos.CENTER_LEFT);
        Label eventNo = new Label("EVENT 04  /  MONTE CARLO"); eventNo.getStyleClass().add("eyebrow");
        Label eventName = new Label(eventOption.label()); eventName.getStyleClass().add("header-event");
        VBox event = new VBox(4, eventNo, eventName);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        Label live = new Label("LIVE"); live.getStyleClass().add("live-indicator");
        Label table = new Label(gameMode.label + "  ·  TABLE 07"); table.getStyleClass().add("header-meta");
        tournamentInfoButton = new Button("INFO  ▾"); tournamentInfoButton.getStyleClass().add("utility-button");
        tournamentInfoButton.setOnAction(e -> toggleTournamentInfo());
        replayButton = new Button("REPLAY  0/5"); replayButton.getStyleClass().add("utility-button");
        replayButton.setDisable(true); replayButton.setOnAction(e -> openReplay());
        Button saveButton = new Button("SAVE"); saveButton.getStyleClass().add("utility-button");
        saveButton.setOnAction(e -> saveGame());
        Button loadButton = new Button("LOAD"); loadButton.getStyleClass().add("utility-button");
        loadButton.setOnAction(e -> loadGame());
        localTableButton = new Button("MY TABLE"); localTableButton.getStyleClass().add("utility-button");
        localTableButton.setOnAction(e -> showLocalTable());
        soundButton = new Button("SOUND  ON"); soundButton.getStyleClass().add("utility-button");
        soundButton.setOnAction(e -> toggleSound());
        HBox liveBlock = new HBox(7, tournamentInfoButton, replayButton, saveButton, loadButton, localTableButton, soundButton, live, table);
        liveBlock.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(40, lockup, event, spacer, liveBlock);
        header.setAlignment(Pos.CENTER_LEFT); header.setPadding(new Insets(16, 26, 16, 26));
        header.getStyleClass().add("top-bar");
        return header;
    }

    private Node buildMainArea() {
        tournamentRail = buildTournamentRail();
        opponentRail = buildOpponentRail();
        tablePane = new TablePane();
        HBox center = new HBox(20, opponentRail, tablePane);
        HBox.setHgrow(tablePane, Priority.ALWAYS); center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(18, 22, 12, 18));
        BorderPane main = new BorderPane(center);
        main.setLeft(tournamentRail); BorderPane.setMargin(tournamentRail, new Insets(18, 0, 18, 22));
        main.getStyleClass().add("main-area");
        return main;
    }

    private void toggleTournamentInfo() {
        if (tournamentRail == null) return;
        boolean expanded = !tournamentRail.isVisible();
        tournamentRail.setVisible(expanded);
        tournamentRail.setManaged(expanded);
        tournamentInfoButton.setText(expanded ? "INFO  ▾" : "INFO  ▸");
    }

    private void toggleSound() {
        soundEnabled = !soundEnabled;
        soundButton.setText(soundEnabled ? "SOUND  ON" : "SOUND  OFF");
        if (soundEnabled) playSoundAlert(SoundCue.UI);
    }

    private void playSoundAlert(SoundCue cue) {
        if (!soundEnabled) return;
        Thread.ofVirtual().start(() -> {
            AudioFormat format = new AudioFormat(44_100, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format); line.start();
                for (int frequency : cue.frequencies) {
                    int samples = 3_528;
                    byte[] audio = new byte[samples * 2];
                    for (int sample = 0; sample < samples; sample++) {
                        double envelope = Math.min(sample / 220.0, (samples - sample) / 500.0);
                        short value = (short) (Math.sin(2 * Math.PI * frequency * sample / 44_100.0) * 3_000 * envelope);
                        audio[sample * 2] = (byte) value;
                        audio[sample * 2 + 1] = (byte) (value >> 8);
                    }
                    line.write(audio, 0, audio.length);
                }
                line.drain();
            } catch (Exception unavailable) {
                Toolkit.getDefaultToolkit().beep();
            }
        });
    }

    private void animateChips(Player player, int amount) {
        if (tablePane == null || amount <= 0) return;
        tablePane.animateChips(player, amount);
    }

    private void openReplay() {
        if (recentHands.isEmpty()) return;
        openReplay(recentHands.peekLast(), false);
    }

    private void openReplay(HandReplay hand) { openReplay(hand, true); }

    private void openReplay(HandReplay hand, boolean tvReplay) {
        Stage replayStage = new Stage();
        ReplayViewer viewer = new ReplayViewer(List.of(hand), tvReplay);
        Scene replayScene = new Scene(viewer, 760, 560);
        applyTheme(replayScene);
        replayStage.setTitle("EPT Simulator | Hand Replay");
        replayStage.setMinWidth(640);
        replayStage.setMinHeight(480);
        replayStage.setScene(replayScene);
        replayStage.show();
    }

    private final class ReplayViewer extends BorderPane {
        private final List<HandReplay> hands;
        private final boolean tvReplay;
        private final Label heading = new Label(), result = new Label(), potLabel = new Label();
        private final HBox heroCardsView = new HBox(8), boardView = new HBox(8);
        private final VBox playerActions = new VBox(8);
        private final Button previous = new Button("← PREVIOUS"), next = new Button("NEXT →");
        private final Button playTv = new Button("PLAY TV REPLAY");
        private final Label playbackCaption = new Label();
        private Timeline playbackTimeline;
        private int playbackBeat;
        private int selectedIndex;

        ReplayViewer(List<HandReplay> hands, boolean tvReplay) {
            this.hands = hands; this.tvReplay = tvReplay;
            selectedIndex = hands.size() - 1;
            heading.getStyleClass().add("header-event");
            result.getStyleClass().add("status-message");
            potLabel.getStyleClass().add("stat-value");
            Label heroTitle = new Label("YOUR CARDS"); heroTitle.getStyleClass().add("section-title");
            Label boardTitle = new Label("BOARD"); boardTitle.getStyleClass().add("section-title");
            playbackCaption.getStyleClass().add("status-message");
            VBox content = new VBox(14, heading, result, potLabel, playbackCaption, heroTitle, heroCardsView, boardTitle, boardView, playerActions);
            content.setPadding(new Insets(28));
            setCenter(content);
            previous.getStyleClass().add("utility-button"); next.getStyleClass().add("utility-button");
            playTv.getStyleClass().add("primary-button");
            previous.setOnAction(e -> { selectedIndex--; render(); });
            next.setOnAction(e -> { selectedIndex++; render(); });
            playTv.setVisible(tvReplay); playTv.setManaged(tvReplay);
            playTv.setOnAction(event -> playTvReplay());
            HBox navigation = new HBox(12, playTv, previous, next); navigation.setAlignment(Pos.CENTER_RIGHT);
            navigation.setPadding(new Insets(14, 24, 18, 24));
            setBottom(navigation);
            render();
        }

        private void render() {
            HandReplay hand = hands.get(selectedIndex);
            heading.setText(tvReplay ? String.format("TV REPLAY  ·  HAND %02d", hand.number())
                    : String.format("HAND %02d  ·  REPLAY %d OF %d", hand.number(), selectedIndex + 1, hands.size()));
            result.setText(hand.result());
            potLabel.setText("POT  " + formatChips(hand.pot()));
            heroTitleText(hand);
            showCards(heroCardsView, hand.heroCards());
                int boardCount = !tvReplay ? hand.board().size() : Math.min(hand.board().size(),
                    playbackBeat == 0 ? 0 : Math.min(5, playbackBeat < 4 ? playbackBeat + 2 : 5));
            showCards(boardView, hand.board().subList(0, boardCount));
            playerActions.getChildren().clear();
            Label title = new Label("TABLE ACTIONS"); title.getStyleClass().add("section-title");
            playerActions.getChildren().add(title);
            if (tvReplay) playbackCaption.setText(switch (playbackBeat) {
                case 0 -> "READY  ·  PRE-FLOP";
                case 1 -> "THE FLOP";
                case 2 -> "THE TURN";
                case 3 -> "THE RIVER";
                default -> "SHOWDOWN  ·  REVEAL";
            });
            else playbackCaption.setText("");
            int visibleSeats = tvReplay ? Math.min(hand.seats().size(), Math.max(0, playbackBeat - 3) * 3) : hand.seats().size();
            for (int index = 0; index < visibleSeats; index++) {
                ReplaySeat seat = hand.seats().get(index);
                String shown = seat.cards().isEmpty() ? "Mucked" : String.join("  ", seat.cards().stream().map(Card::display).toList());
                String handName = seat.handName().isBlank() ? "" : "  ·  " + seat.handName();
                Label row = new Label(String.format("%-18s %-14s %s%s", seat.name(), seat.action(), shown, handName));
                row.getStyleClass().add("status-message");
                playerActions.getChildren().add(row);
            }
            previous.setDisable(tvReplay || selectedIndex == 0);
            next.setDisable(tvReplay || selectedIndex == hands.size() - 1);
            playTv.setText(playbackTimeline != null && playbackTimeline.getStatus() == javafx.animation.Animation.Status.RUNNING
                    ? "PLAYING..." : "PLAY TV REPLAY");
        }

        private void heroTitleText(HandReplay hand) {
            Node title = ((VBox) getCenter()).getChildren().get(4);
            if (title instanceof Label label) label.setText(hand.result().startsWith("HOT HAND") ? "WINNER'S CARDS" : "YOUR CARDS");
        }

        private void playTvReplay() {
            if (playbackTimeline != null) playbackTimeline.stop();
            playbackBeat = 0; render();
            playbackTimeline = new Timeline(new KeyFrame(Duration.millis(850), event -> { playbackBeat++; render(); }));
            playbackTimeline.setCycleCount(6);
            playbackTimeline.setOnFinished(event -> render());
            playbackTimeline.play();
            render();
        }

        private void showCards(HBox destination, List<Card> cards) {
            destination.getChildren().clear();
            for (Card card : cards) {
                Label face = cardLabel(card, false);
                face.setPrefSize(48, 68);
                destination.getChildren().add(face);
            }
        }
    }

    private VBox buildTournamentRail() {
        blindValue = valueLabel(); levelClock = valueLabel(); fieldValue = valueLabel();
        prizePoolValue = valueLabel(); firstPrizeValue = valueLabel();
        levelValue = valueLabel("01");
        VBox levels = section("BLINDS & LEVELS", stat("LEVEL", levelValue),
                stat("SMALL / BIG", blindValue), stat("NEXT LEVEL", levelClock));
        VBox tournament = section("TOURNAMENT", stat("PLAYERS LEFT", fieldValue),
            stat("PRIZE POOL", prizePoolValue), stat("1ST PLACE", firstPrizeValue),
            stat("BUY-IN", valueLabel(eventOption == null ? "-" : formatMoney(eventOption.buyIn() + eventOption.fee()))));
            Label title = new Label("TOP 7  ·  15 PAID"); title.getStyleClass().add("section-title");
        payoutLabel = new Label(); payoutLabel.getStyleClass().add("payout-list");
        VBox payouts = new VBox(12, title, payoutLabel); payouts.getStyleClass().add("rail-section");
        Label rankingTitle = new Label("LIVE CHIP RANKING"); rankingTitle.getStyleClass().add("section-title");
        rankingList = new VBox(4);
        ScrollPane rankingScroll = new ScrollPane(rankingList);
        rankingScroll.setFitToWidth(true); rankingScroll.setMaxHeight(230);
        VBox rankingSection = new VBox(10, rankingTitle, rankingScroll);
        rankingSection.getStyleClass().add("rail-section");
        Label note = new Label(blindIntervalSeconds / 60 + "-MINUTE LEVELS\nANTE FROM LEVEL 3"); note.getStyleClass().add("rail-note");
        VBox rail = new VBox(20, levels, tournament, payouts, rankingSection, note);
        rail.setPrefWidth(220); rail.setMinWidth(200); rail.getStyleClass().add("left-rail");
        return rail;
    }

    private void openStackChart(Player player) {
        NumberAxis handAxis = new NumberAxis(); handAxis.setLabel("Hand / Snapshot");
        NumberAxis stackAxis = new NumberAxis(); stackAxis.setLabel("Chips");
        LineChart<Number, Number> chart = new LineChart<>(handAxis, stackAxis);
        chart.setTitle(player.name + "  ·  Chip Evolution");
        chart.setAnimated(true); chart.setCreateSymbols(false);
        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName(player.name);
        for (int index = 0; index < player.stackHistory.size(); index++) {
            series.getData().add(new XYChart.Data<>(index, player.stackHistory.get(index)));
        }
        chart.getData().add(series);
        Stage chartStage = new Stage();
        Scene scene = new Scene(new BorderPane(chart), 760, 480);
        applyTheme(scene);
        chartStage.setTitle("EPT Simulator | Chip Evolution");
        chartStage.setScene(scene); chartStage.show();
    }

    private void refreshRanking() {
        if (rankingList == null) return;
        rankingList.getChildren().clear();
        List<Player> ranked = new ArrayList<>();
        ranked.add(hero); ranked.addAll(opponents);
        for (RemoteTable table : remoteTables) ranked.addAll(table.players);
        ranked.sort(Comparator.comparingInt((Player player) -> player.stack).reversed());
        for (int index = 0; index < ranked.size(); index++) {
            Player player = ranked.get(index);
            Label rank = new Label(String.format("%02d", index + 1));
            rank.getStyleClass().add("rank-number");
            Label name = new Label(player.name); name.getStyleClass().add("opponent-name");
            Label stack = new Label(formatChips(player.stack)); stack.getStyleClass().add("opponent-stack");
            HBox row = new HBox(8, rank, name, new Region(), stack);
            row.setAlignment(Pos.CENTER_LEFT); HBox.setHgrow(row.getChildren().get(2), Priority.ALWAYS);
            row.getStyleClass().add("ranking-row");
            if (player.stack <= 0) row.getStyleClass().add("eliminated-row");
            Integer previousStack = displayedStacks.put(player, player.stack);
            if (previousStack != null && previousStack != player.stack) {
                row.setStyle(previousStack > player.stack
                        ? "-fx-background-color: rgba(211,184,120,0.30);"
                        : "-fx-background-color: rgba(82,153,116,0.25);");
                Timeline stackFlash = new Timeline(new KeyFrame(Duration.millis(700), event -> row.setStyle("")));
                stackFlash.play();
            }
            row.setOnMouseClicked(event -> openStackChart(player));
            rankingList.getChildren().add(row);
        }
    }

    private VBox buildOpponentRail() {
        Label title = new Label("AT YOUR TABLE"); title.getStyleClass().add("section-title");
        opponentCount = new Label((opponents.size() + 1) + " PLAYERS"); opponentCount.getStyleClass().add("section-count");
        HBox heading = new HBox(title, opponentCount); heading.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(opponentCount, Priority.ALWAYS); opponentCount.setMaxWidth(Double.MAX_VALUE);
        opponentCount.setAlignment(Pos.CENTER_RIGHT);
        remoteTableList = new VBox(5);
        for (RemoteTable remoteTable : remoteTables) {
            Button tableButton = new Button(remoteTable.label());
            tableButton.getStyleClass().add("utility-button"); tableButton.setMaxWidth(Double.MAX_VALUE);
            tableButton.setOnAction(event -> observeTable(remoteTable));
            remoteTableList.getChildren().add(tableButton);
        }
        ScrollPane remoteScroll = new ScrollPane(remoteTableList);
        remoteScroll.setFitToWidth(true); remoteScroll.setMaxHeight(140);
        remoteScroll.setVisible(!remoteTables.isEmpty()); remoteScroll.setManaged(!remoteTables.isEmpty());
        Label remoteTitle = new Label("OTHER TABLES"); remoteTitle.getStyleClass().add("section-title");
        VBox rail = new VBox(12, heading); rail.setPrefWidth(198); rail.setMinWidth(180);
        rail.getStyleClass().add("opponent-rail");
        localPlayerList = new VBox(5);
        for (Player player : opponents) localPlayerList.getChildren().add(opponentRow(player));
        rail.getChildren().add(localPlayerList);
        if (!remoteTables.isEmpty()) rail.getChildren().addAll(remoteTitle, remoteScroll);
        Label hotTitle = new Label("HOT HANDS"); hotTitle.getStyleClass().add("section-title");
        hotHandList = new VBox(5);
        ScrollPane hotScroll = new ScrollPane(hotHandList);
        hotScroll.setFitToWidth(true); hotScroll.setMaxHeight(150);
        rail.getChildren().addAll(hotTitle, hotScroll);
        return rail;
    }

    private void refreshHotHandList() {
        if (hotHandList == null) return;
        hotHandList.getChildren().clear();
        if (hotHands.isEmpty()) {
            Label empty = new Label("Waiting for a big hand..."); empty.getStyleClass().add("opponent-stack");
            hotHandList.getChildren().add(empty); return;
        }
        for (HandReplay hand : hotHands) {
            Button entry = new Button(hand.result());
            entry.getStyleClass().add("utility-button"); entry.setMaxWidth(Double.MAX_VALUE);
            entry.setOnAction(event -> openReplay(hand));
            hotHandList.getChildren().add(entry);
        }
    }

    private void refreshLocalPlayerList() {
        if (localPlayerList == null) return;
        localPlayerList.getChildren().clear();
        for (Player player : opponents) localPlayerList.getChildren().add(opponentRow(player));
        opponentCount.setText((opponents.size() + 1) + " PLAYERS");
    }

    private void observeTable(RemoteTable table) {
        observedTable = table;
        actionPrompt = "OBSERVING TABLE " + table.number;
        tablePane.showRemoteTable(table);
        refreshActions();
    }

    private void showLocalTable() {
        observedTable = null;
        tablePane.showLocalTable();
        refreshActions();
    }

    private void saveGame() {
        if (eventOption == null) return;
        FileChooser chooser = saveChooser("Save EPT tournament");
        java.io.File file = chooser.showSaveDialog(primaryStage);
        if (file == null) return;
        Properties state = new Properties();
        state.setProperty("version", "1");
        state.setProperty("event", Integer.toString(EVENTS.indexOf(eventOption)));
        state.setProperty("mode", gameMode.name()); state.setProperty("difficulty", difficulty.name());
        state.setProperty("fieldSize", Integer.toString(fieldSize));
        state.setProperty("blindIntervalSeconds", Integer.toString(blindIntervalSeconds));
        state.setProperty("levelIndex", Integer.toString(levelIndex));
        state.setProperty("secondsInLevel", Integer.toString(secondsInLevel));
        state.setProperty("handNumber", Integer.toString(handNumber));
        state.setProperty("heroStack", Integer.toString(hero.stack));
        state.setProperty("timeBankChips", Integer.toString(timeBankChips));
        state.setProperty("actionSecondsRemaining", Integer.toString(actionSecondsRemaining));
        state.setProperty("timeBankEarnedThisHand", Boolean.toString(timeBankEarnedThisHand));
        state.setProperty("pot", Integer.toString(pot)); state.setProperty("currentBet", Integer.toString(currentBet));
        state.setProperty("heroStreetBet", Integer.toString(heroStreetBet));
        state.setProperty("handOver", Boolean.toString(handOver)); state.setProperty("heroFolded", Boolean.toString(heroFolded));
        state.setProperty("heroActedThisRound", Boolean.toString(hero.actedThisRound));
        state.setProperty("drawPending", Boolean.toString(drawPending));
        state.setProperty("drawCompleted", Boolean.toString(drawCompleted));
        state.setProperty("heroDiscards", String.join(";", heroDiscards.stream().map(String::valueOf).toList()));
        state.setProperty("soundEnabled", Boolean.toString(soundEnabled));
        state.setProperty("buttonSeat", Integer.toString(buttonSeat));
        state.setProperty("heroCards", encodeCards(heroCards)); state.setProperty("board", encodeCards(board));
        state.setProperty("deck", encodeCards(deck)); state.setProperty("discardPile", encodeCards(discardPile));
        state.setProperty("local.count", Integer.toString(opponents.size()));
        for (int i = 0; i < opponents.size(); i++) writePlayer(state, "local." + i + ".", opponents.get(i));
        state.setProperty("remote.count", Integer.toString(remoteTables.size()));
        for (int i = 0; i < remoteTables.size(); i++) {
            RemoteTable table = remoteTables.get(i);
            String prefix = "remote." + i + ".";
            state.setProperty(prefix + "number", Integer.toString(table.number));
            state.setProperty(prefix + "handNumber", Integer.toString(table.handNumber));
            state.setProperty(prefix + "pot", Integer.toString(table.pot));
            state.setProperty(prefix + "lastAction", table.lastAction);
            state.setProperty(prefix + "board", encodeCards(table.board));
            state.setProperty(prefix + "players", Integer.toString(table.players.size()));
            for (int seat = 0; seat < table.players.size(); seat++) writePlayer(state, prefix + "player." + seat + ".", table.players.get(seat));
        }
        try (var output = Files.newOutputStream(file.toPath())) {
            state.store(output, "EPT Simulator tournament save");
            if (statusLabel != null) statusLabel.setText("Tournament saved to " + file.getName() + ".");
        } catch (IOException exception) {
            showError("Could not save tournament", exception.getMessage());
        }
    }

    private void loadGame() {
        FileChooser chooser = saveChooser("Load EPT tournament");
        java.io.File file = chooser.showOpenDialog(primaryStage);
        if (file == null) return;
        Properties state = new Properties();
        try (var input = Files.newInputStream(file.toPath())) {
            state.load(input);
            if (!"1".equals(state.getProperty("version"))) throw new IOException("Unsupported save version.");
            int eventIndex = Integer.parseInt(state.getProperty("event", "2"));
            GameMode loadedMode = GameMode.valueOf(state.getProperty("mode"));
            Difficulty loadedDifficulty = Difficulty.valueOf(state.getProperty("difficulty"));
            int loadedFieldSize = Integer.parseInt(state.getProperty("fieldSize"));
            int loadedBlindSeconds = Integer.parseInt(state.getProperty("blindIntervalSeconds"));
            startTournament(primaryStage, EVENTS.get(eventIndex), loadedMode, loadedDifficulty, loadedFieldSize, loadedBlindSeconds / 60);
            if (autoDealCountdown != null) autoDealCountdown.stop();
            hero.stack = Integer.parseInt(state.getProperty("heroStack"));
            timeBankChips = Integer.parseInt(state.getProperty("timeBankChips", Integer.toString(INITIAL_TIME_BANK_CHIPS)));
            actionSecondsRemaining = Integer.parseInt(state.getProperty("actionSecondsRemaining", Integer.toString(ACTION_SECONDS)));
            timeBankEarnedThisHand = Boolean.parseBoolean(state.getProperty("timeBankEarnedThisHand", "false"));
            pot = Integer.parseInt(state.getProperty("pot")); currentBet = Integer.parseInt(state.getProperty("currentBet"));
            heroStreetBet = Integer.parseInt(state.getProperty("heroStreetBet"));
            levelIndex = Integer.parseInt(state.getProperty("levelIndex"));
            secondsInLevel = Integer.parseInt(state.getProperty("secondsInLevel"));
            handNumber = Integer.parseInt(state.getProperty("handNumber"));
            buttonSeat = Integer.parseInt(state.getProperty("buttonSeat"));
            handOver = Boolean.parseBoolean(state.getProperty("handOver"));
            heroFolded = Boolean.parseBoolean(state.getProperty("heroFolded")); heroTurn = !handOver && hero.stack > 0;
            hero.actedThisRound = Boolean.parseBoolean(state.getProperty("heroActedThisRound", "false"));
            drawPending = Boolean.parseBoolean(state.getProperty("drawPending", "false"));
            drawCompleted = Boolean.parseBoolean(state.getProperty("drawCompleted", "false"));
            soundEnabled = Boolean.parseBoolean(state.getProperty("soundEnabled", "true"));
            heroDiscards.clear();
            String discardFlags = state.getProperty("heroDiscards", "");
            if (!discardFlags.isBlank()) for (String flag : discardFlags.split(";")) heroDiscards.add(Boolean.parseBoolean(flag));
            if (soundButton != null) soundButton.setText(soundEnabled ? "SOUND  ON" : "SOUND  OFF");
            replaceCards(heroCards, state.getProperty("heroCards", ""));
            replaceCards(board, state.getProperty("board", ""));
            replaceCards(deck, state.getProperty("deck", ""));
            replaceCards(discardPile, state.getProperty("discardPile", ""));
            for (int i = 0; i < opponents.size(); i++) readPlayer(state, "local." + i + ".", opponents.get(i));
            int tableCount = Math.min(remoteTables.size(), Integer.parseInt(state.getProperty("remote.count", "0")));
            for (int i = 0; i < tableCount; i++) {
                RemoteTable table = remoteTables.get(i); String prefix = "remote." + i + ".";
                table.handNumber = Integer.parseInt(state.getProperty(prefix + "handNumber", "0"));
                table.pot = Integer.parseInt(state.getProperty(prefix + "pot", "0"));
                table.lastAction = state.getProperty(prefix + "lastAction", "Waiting for next hand");
                replaceCards(table.board, state.getProperty(prefix + "board", ""));
                for (int seat = 0; seat < table.players.size(); seat++) readPlayer(state, prefix + "player." + seat + ".", table.players.get(seat));
            }
            observedTable = null; actionPrompt = handOver ? "SAVED TOURNAMENT" : "SAVED HAND  ·  YOUR ACTION";
            statusLabel.setText(handOver ? "Tournament loaded." : "Hand restored. Action resumes with you.");
            updateTournamentInfo(); refreshTable(); refreshActions();
            refreshActionTimerDisplay();
            if (!handOver && heroTurn) startActionClock(false);
            if (handOver) scheduleAutoDeal();
        } catch (IOException | IllegalArgumentException exception) {
            showError("Could not load tournament", exception.getMessage());
        }
    }

    private FileChooser saveChooser(String title) {
        FileChooser chooser = new FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("EPT save (*.eptsave)", "*.eptsave"));
        chooser.setInitialFileName("tournament.eptsave");
        return chooser;
    }

    private static void writePlayer(Properties state, String prefix, Player player) {
        state.setProperty(prefix + "name", player.name); state.setProperty(prefix + "stack", Integer.toString(player.stack));
        state.setProperty(prefix + "streetBet", Integer.toString(player.streetBet));
        state.setProperty(prefix + "folded", Boolean.toString(player.foldedThisHand));
        state.setProperty(prefix + "showCards", Boolean.toString(player.showCards));
        state.setProperty(prefix + "actedThisRound", Boolean.toString(player.actedThisRound));
        state.setProperty(prefix + "action", player.lastAction); state.setProperty(prefix + "cards", encodeCards(player.holeCards));
        state.setProperty(prefix + "stackHistory", String.join(",", player.stackHistory.stream().map(String::valueOf).toList()));
    }

    private static void readPlayer(Properties state, String prefix, Player player) {
        player.stack = Integer.parseInt(state.getProperty(prefix + "stack", Integer.toString(player.stack)));
        player.streetBet = Integer.parseInt(state.getProperty(prefix + "streetBet", "0"));
        player.foldedThisHand = Boolean.parseBoolean(state.getProperty(prefix + "folded", "false"));
        player.showCards = Boolean.parseBoolean(state.getProperty(prefix + "showCards", "false"));
        player.actedThisRound = Boolean.parseBoolean(state.getProperty(prefix + "actedThisRound", "false"));
        player.lastAction = state.getProperty(prefix + "action", "");
        replaceCards(player.holeCards, state.getProperty(prefix + "cards", ""));
        String history = state.getProperty(prefix + "stackHistory", "");
        if (!history.isBlank()) {
            player.stackHistory.clear();
            for (String snapshot : history.split(",")) player.stackHistory.add(Integer.parseInt(snapshot));
        }
    }

    private static String encodeCards(List<Card> cards) {
        return String.join(";", cards.stream().map(card -> card.rank() + ":" + card.suit()).toList());
    }

    private static void replaceCards(List<Card> cards, String encoded) {
        cards.clear();
        if (encoded.isBlank()) return;
        for (String token : encoded.split(";")) {
            String[] values = token.split(":"); cards.add(new Card(Integer.parseInt(values[0]), Integer.parseInt(values[1])));
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message == null ? "Unknown error" : message);
        alert.setTitle(title); alert.setHeaderText(null); alert.initOwner(primaryStage); alert.showAndWait();
    }

    private Node opponentRow(Player player) {
        Label initial = new Label(player.name.substring(0, 1)); initial.getStyleClass().add("opponent-avatar");
        Label name = new Label(player.name); name.getStyleClass().add("opponent-name");
        Label stack = new Label(formatChips(player.stack)); stack.getStyleClass().add("opponent-stack");
        VBox details = new VBox(4, name, stack);
        HBox row = new HBox(10, initial, details); row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("opponent-row"); row.setUserData(player);
        return row;
    }

    private Node buildActionDock() {
        handLabel = new Label("HAND 01"); handLabel.getStyleClass().add("dock-label");
        statusLabel = new Label("Your tournament starts here."); statusLabel.getStyleClass().add("status-message");
        VBox status = new VBox(5, handLabel, statusLabel); status.setMinWidth(250);
        actionTimerLabel = new Label("20"); actionTimerLabel.getStyleClass().add("action-timer");
        timeBankLabel = new Label("5 CHIPS"); timeBankLabel.getStyleClass().add("dock-label");
        timeBankButton = new Button("THROW TIME BANK CHIP");
        timeBankButton.getStyleClass().add("time-bank-button");
        timeBankButton.setOnAction(event -> useTimeBankChip());
        VBox clockControl = new VBox(5, new Label("TIME TO ACT"), actionTimerLabel, timeBankLabel, timeBankButton);
        clockControl.getStyleClass().add("clock-control");
        Label raiseTitle = new Label("RAISE TO"); raiseTitle.getStyleClass().add("dock-label");
        raiseSlider = new Slider(400, 4000, 600); raiseSlider.setShowTickLabels(false); raiseSlider.setShowTickMarks(false);
        Label raiseAmount = new Label("600"); raiseAmount.getStyleClass().add("raise-amount");
        raiseSlider.valueProperty().addListener((obs, oldV, newV) -> raiseAmount.setText(formatChips(roundHundred(newV.doubleValue()))));
        HBox sliderRow = new HBox(10, raiseSlider, raiseAmount); HBox.setHgrow(raiseSlider, Priority.ALWAYS);
        VBox raiseControl = new VBox(7, raiseTitle, sliderRow); raiseControl.setPrefWidth(255);
        foldButton = new Button("FOLD"); foldButton.getStyleClass().add("fold-button"); foldButton.setOnAction(e -> fold());
        checkCallButton = new Button("CHECK"); checkCallButton.getStyleClass().add("secondary-button"); checkCallButton.setOnAction(e -> checkOrCall());
        raiseButton = new Button("RAISE"); raiseButton.getStyleClass().add("primary-button"); raiseButton.setOnAction(e -> raise(roundHundred(raiseSlider.getValue())));
        startButton = new Button("DEAL NEXT HAND"); startButton.getStyleClass().add("primary-button"); startButton.setOnAction(e -> startHand());
        HBox actions = new HBox(10, foldButton, checkCallButton, raiseButton, startButton); actions.setAlignment(Pos.CENTER_RIGHT);
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox dock = new HBox(22, status, spacer, clockControl, raiseControl, actions);
        dock.setAlignment(Pos.CENTER_LEFT); dock.setPadding(new Insets(17, 24, 18, 24)); dock.getStyleClass().add("action-dock");
        refreshActions();
        return dock;
    }

    private VBox section(String title, Node... rows) {
        Label heading = new Label(title); heading.getStyleClass().add("section-title");
        VBox box = new VBox(13); box.getChildren().add(heading); box.getChildren().addAll(rows);
        box.getStyleClass().add("rail-section"); return box;
    }

    private HBox stat(String title, Label value) {
        Label key = new Label(title); key.getStyleClass().add("stat-key"); value.getStyleClass().add("stat-value");
        Region gap = new Region(); HBox.setHgrow(gap, Priority.ALWAYS);
        HBox row = new HBox(key, gap, value); row.setAlignment(Pos.CENTER_LEFT); return row;
    }

    private Label valueLabel() { Label label = new Label(); label.getStyleClass().add("stat-value"); return label; }
    private Label valueLabel(String text) { Label label = valueLabel(); label.setText(text); return label; }

    private void startHand() {
        if (hero.stack <= 0) { statusLabel.setText("You have been eliminated from the event."); refreshActions(); return; }
        if (autoDealCountdown != null) autoDealCountdown.stop();
        startButton.setText("DEAL NEXT HAND");
        if (tablePane != null) tablePane.resetCardAnimation();
        handNumber++; handOver = false; heroFolded = false; heroTurn = true; hero.lastAction = "";
        hero.actedThisRound = false; hero.showCards = false;
        replaySaved = false;
        timeBankEarnedThisHand = false;
        hero.actedThisRound = false;
        int localSeatCount = opponents.size() + 1;
        buttonSeat = (handNumber - 1) % localSeatCount;
        int smallBlindSeat = (buttonSeat + 1) % localSeatCount;
        int bigBlindSeat = (buttonSeat + 2) % localSeatCount;
        int ante = levelIndex >= 2 ? Math.max(50, roundHundred(BIG_BLINDS[levelIndex] / 8.0) / 2) : 0;
        currentBet = BIG_BLINDS[levelIndex] + ante;
        pot = 0;
            int heroBlind = smallBlindSeat == 0 ? SMALL_BLINDS[levelIndex] : (bigBlindSeat == 0 ? BIG_BLINDS[levelIndex] : 0);
        heroStreetBet = Math.min(ante + heroBlind, hero.stack);
        hero.stack -= heroStreetBet;
        pot += heroStreetBet;
            animateChips(hero, heroStreetBet);
        heroCards.clear(); board.clear(); deck.clear(); discardPile.clear(); heroDiscards.clear();
        drawPending = false; drawCompleted = false;
        for (int rank = 2; rank <= 14; rank++) for (int suit = 0; suit < 4; suit++) deck.add(new Card(rank, suit));
        Collections.shuffle(deck, random);
        for (int i = 0; i < gameMode.holeCardCount; i++) { heroCards.add(draw()); heroDiscards.add(false); }
        for (int i = 0; i < opponents.size(); i++) {
            Player player = opponents.get(i);
            player.foldedThisHand = player.stack <= 0;
            player.streetBet = 0;
            player.holeCards.clear();
            player.lastAction = player.foldedThisHand ? "OUT" : "";
            player.isActing = false; player.showCards = false;
            player.actedThisRound = false;
            if (player.foldedThisHand) continue;
            for (int card = 0; card < gameMode.holeCardCount; card++) player.holeCards.add(draw());
            int seat = i + 1;
            int blind = smallBlindSeat == seat ? SMALL_BLINDS[levelIndex] : (bigBlindSeat == seat ? BIG_BLINDS[levelIndex] : 0);
            int paid = Math.min(ante + blind, player.stack);
            player.stack -= paid; player.streetBet = paid; pot += paid;
            animateChips(player, paid);
        }
        handLabel.setText(String.format("HAND %02d  ·  %s", handNumber, streetName()));
        statusLabel.setText("Blinds are in. You're first to act."); actionPrompt = "PREFLOP  ·  YOUR ACTION";
        playSoundAlert(SoundCue.DEAL);
        refreshTable(); refreshActions(); updateTournamentInfo();
        startActionClock();
    }

    private Card draw() { return deck.remove(deck.size() - 1); }

    private void recycleDiscardsIfNeeded() {
        if (deck.isEmpty() && !discardPile.isEmpty()) {
            deck.addAll(discardPile); discardPile.clear(); Collections.shuffle(deck, random);
        }
    }

    private void fold() {
        if (handOver) return;
        stopActionClock();
        heroFolded = true;
        hero.actedThisRound = true;
        hero.lastAction = "FOLD";
        playSoundAlert(SoundCue.FOLD);
        actionPrompt = "YOU FOLD";
        statusLabel.setText("You fold. The table plays out the hand.");
        refreshTable();
        queueOpponentActions();
    }

    private void checkOrCall() {
        if (handOver) return;
        stopActionClock();
        if (drawPending) { completePlayerDraw(); return; }
        int paid = chipsToCall(currentBet, heroStreetBet, hero.stack);
        if (paid > 0) {
            hero.stack -= paid; heroStreetBet += paid; pot += paid;
            animateChips(hero, paid);
            hero.lastAction = hero.stack == 0 ? "ALL-IN" : "CALL " + formatChips(paid);
            statusLabel.setText("You called " + formatChips(paid) + ".");
            playSoundAlert(SoundCue.CALL);
        } else { hero.lastAction = "CHECK"; statusLabel.setText("You checked."); playSoundAlert(SoundCue.CHECK); }
        hero.actedThisRound = true;
        refreshTable();
        queueOpponentActions();
    }

    private void raise(int target) {
        if (handOver) return;
        stopActionClock();
        int maximumRaiseTo = heroStreetBet + hero.stack;
        if (gameMode == GameMode.POT_LIMIT_OMAHA) maximumRaiseTo = Math.min(maximumRaiseTo, currentBet + pot + Math.max(0, currentBet - heroStreetBet));
        int to = Math.min(Math.max(target, currentBet + BIG_BLINDS[levelIndex]), maximumRaiseTo);
        int paid = to - heroStreetBet;
        if (paid <= 0) { checkOrCall(); return; }
        hero.stack -= paid; heroStreetBet = to; pot += paid; currentBet = to;
        hero.actedThisRound = true;
        animateChips(hero, paid); playSoundAlert(SoundCue.RAISE);
        hero.lastAction = hero.stack == 0 ? "ALL-IN" : "RAISE " + formatChips(to);
        statusLabel.setText("You raise to " + formatChips(to) + ". Players respond.");
        refreshTable();
        queueOpponentActions();
    }

    private void queueOpponentActions() {
        stopActionClock();
        if (actionDelay != null) actionDelay.stop();
        heroTurn = false;
        actionPrompt = "TABLE ACTION  ·  PLAYERS RESPOND";
        refreshActions(); refreshTable();
        actionPlayers = new ArrayList<>(handOpponents().stream()
            .filter(player -> player.stack > 0 && (!player.actedThisRound || player.streetBet < currentBet)).toList());
        actionIndex = 0;
        processNextOpponent();
    }

    private void processNextOpponent() {
        if (actionIndex >= actionPlayers.size()) { completeActionRound(); return; }
        Player player = actionPlayers.get(actionIndex++);
        player.isActing = true;
        actionPrompt = player.name.toUpperCase() + "  ·  DECIDING";
        refreshTable();
        actionDelay = new PauseTransition(Duration.millis(700));
        actionDelay.setOnFinished(event -> {
            player.isActing = false;
            player.actedThisRound = true;
            if (player.stack == 0) player.lastAction = "ALL-IN";
            else {
                int toCall = Math.max(0, currentBet - player.streetBet);
                AiDecision decision = chooseAiDecision(player, toCall);
                if (decision.action() == AiAction.FOLD) {
                    player.foldedThisHand = true;
                    player.lastAction = "FOLD";
                    statusLabel.setText(player.name + " folds.");
                } else if (decision.action() == AiAction.RAISE) {
                    int raiseTo = Math.min(decision.raiseTo(), player.streetBet + player.stack);
                    int paid = raiseTo - player.streetBet;
                    player.stack -= paid; player.streetBet += paid; pot += paid; currentBet = raiseTo;
                    animateChips(player, paid);
                    player.lastAction = player.stack == 0 ? "ALL-IN" : "RAISE " + formatChips(raiseTo);
                    statusLabel.setText(player.name + " raises to " + formatChips(raiseTo) + ".");
                } else if (toCall > 0) {
                    int paid = chipsToCall(currentBet, player.streetBet, player.stack);
                    player.stack -= paid; player.streetBet += paid; pot += paid;
                    animateChips(player, paid);
                    player.lastAction = player.stack == 0 ? "ALL-IN" : "CALL " + formatChips(paid);
                    statusLabel.setText(player.name + (player.stack == 0 ? " is all-in." : " calls " + formatChips(paid) + "."));
                } else if (decision.action() == AiAction.BET) {
                    int bet = Math.min(decision.raiseTo(), player.stack);
                    player.stack -= bet; player.streetBet += bet; pot += bet; currentBet = bet;
                    animateChips(player, bet);
                    player.lastAction = player.stack == 0 ? "ALL-IN" : "BET " + formatChips(bet);
                    statusLabel.setText(player.name + " bets " + formatChips(bet) + ".");
                } else {
                    player.lastAction = "CHECK";
                    statusLabel.setText(player.name + " checks.");
                }
            }
            actionPrompt = player.name.toUpperCase() + "  ·  " + player.lastAction;
            if (player.lastAction.startsWith("RAISE") || player.lastAction.startsWith("BET")) {
                for (Player waiting : handOpponents()) {
                    if (waiting != player && waiting.stack > 0 && waiting.streetBet < currentBet
                            && !actionPlayers.subList(actionIndex, actionPlayers.size()).contains(waiting)) actionPlayers.add(waiting);
                }
            }
                SoundCue cue = player.lastAction.startsWith("FOLD") ? SoundCue.FOLD
                    : player.lastAction.startsWith("CALL") ? SoundCue.CALL
                    : player.lastAction.startsWith("RAISE") || player.lastAction.startsWith("BET") ? SoundCue.RAISE
                    : player.lastAction.equals("CHECK") ? SoundCue.CHECK : SoundCue.DEAL;
                playSoundAlert(cue);
            refreshTable();
            processNextOpponent();
        });
        actionDelay.play();
    }

    private AiDecision chooseAiDecision(Player player, int toCall) {
        double strength = estimateHandStrength(player.holeCards, board);
        double potOdds = toCall == 0 ? 0 : (double) toCall / Math.max(1, pot + toCall);
        double foldMargin = switch (difficulty) {
            case EASY -> 0.18;
            case MEDIUM -> 0.02;
            case HARD -> -0.08;
        };
        double raiseThreshold = switch (difficulty) {
            case EASY -> 0.84;
            case MEDIUM -> 0.74;
            case HARD -> 0.68;
        };
        boolean bluff = difficulty == Difficulty.HARD && board.size() >= 3
                && random.nextDouble() < (board.size() == 5 ? 0.10 : 0.07);
        if (toCall > 0 && strength + foldMargin < potOdds + 0.12) {
            return new AiDecision(AiAction.FOLD, 0);
        }
        if (strength >= raiseThreshold || bluff) {
            double baseSizing = difficulty == Difficulty.EASY ? 0.35 : difficulty == Difficulty.MEDIUM ? 0.48 : 0.58;
            double sizing = Math.min(0.95, baseSizing + Math.max(0, strength - raiseThreshold) * 0.8);
            int raiseTo = currentBet + Math.max(BIG_BLINDS[levelIndex], (int) Math.ceil(pot * sizing / 100.0) * 100);
            if (gameMode == GameMode.POT_LIMIT_OMAHA) raiseTo = Math.min(raiseTo, currentBet + pot + toCall);
            int maximumRaiseTo = player.streetBet + player.stack;
            if (raiseTo >= maximumRaiseTo && strength < 0.94) {
                return new AiDecision(toCall == 0 ? AiAction.CHECK : AiAction.CALL, 0);
            }
            raiseTo = Math.min(raiseTo, maximumRaiseTo);
            return new AiDecision(toCall == 0 ? AiAction.BET : AiAction.RAISE, raiseTo);
        }
        return new AiDecision(toCall == 0 ? AiAction.CHECK : AiAction.CALL, 0);
    }

    private double estimateHandStrength(List<Card> hole, List<Card> community) {
        if (hole.isEmpty()) return 0;
        if (gameMode.draw && drawCompleted) return drawHandStrength(hole);
        if (community.isEmpty()) {
            if (hole.size() < 2) return 0.35;
            if (gameMode.draw) {
            int sum = hole.stream().mapToInt(Card::rank).sum();
            long uniqueRanks = hole.stream().map(Card::rank).distinct().count();
            long maxSuit = hole.stream().collect(java.util.stream.Collectors.groupingBy(Card::suit, java.util.stream.Collectors.counting()))
                .values().stream().mapToLong(Long::longValue).max().orElse(0);
            double value = 0.98 - Math.max(0, (double) sum / hole.size() - 2) * 0.075
                - (hole.size() - uniqueRanks) * 0.18 - Math.max(0, maxSuit - 2) * 0.025;
            return Math.min(0.96, Math.max(0.06, value));
            }
            if (gameMode.omaha) {
            double value = 0.30;
            for (Card card : hole) value += Math.max(0, card.rank() - 10) * 0.012;
            long pairs = hole.stream().collect(java.util.stream.Collectors.groupingBy(Card::rank, java.util.stream.Collectors.counting()))
                .values().stream().filter(count -> count >= 2).count();
            long suited = hole.stream().collect(java.util.stream.Collectors.groupingBy(Card::suit, java.util.stream.Collectors.counting()))
                .values().stream().filter(count -> count >= 2).count();
            return Math.min(0.88, value + pairs * 0.09 + Math.min(2, suited) * 0.055);
            }
            int high = Math.max(hole.get(0).rank(), hole.get(1).rank());
            int low = Math.min(hole.get(0).rank(), hole.get(1).rank());
            boolean pair = high == low;
            int gap = high - low - 1;
            double value = pair ? 0.48 + (high - 2) * 0.028
                    : 0.10 + (high - 2) * 0.025 + (low - 2) * 0.012 - Math.min(gap, 4) * 0.025;
            if (!pair && hole.get(0).suit() == hole.get(1).suit()) value += 0.055;
            if (!pair && gap == 0) value += 0.045;
            return Math.min(0.92, Math.max(0.08, value));
        }
        if (gameMode.draw) {
            return drawHandStrength(hole);
        }
        List<Card> cards = new ArrayList<>(hole); cards.addAll(community);
        HandValue hand = gameMode.omaha ? evaluateOmahaBest(hole, community) : evaluateBest(cards);
        int category = hand.ranks().get(0);
        double strength = switch (category) {
            case 8 -> 0.995;
            case 7 -> 0.97;
            case 6 -> 0.94;
            case 5 -> 0.89;
            case 4 -> 0.84;
            case 3 -> 0.77;
            case 2 -> 0.69;
            case 1 -> 0.37 + hand.ranks().get(1) * 0.022;
            default -> 0.12 + hand.ranks().get(1) * 0.018;
        };
        if (gameMode.hiLo) {
            LowValue low = evaluateOmahaLow(hole, community);
            strength = low == null ? strength * 0.86 : Math.min(0.99, Math.max(strength, 0.61 + low.ranks().get(0) * 0.025));
        }
        if (community.size() < 5) {
            List<Card> combined = new ArrayList<>(cards);
            for (int suit = 0; suit < 4; suit++) {
                int suitCount = 0;
                for (Card card : combined) if (card.suit() == suit) suitCount++;
                if (suitCount == 4) { strength += 0.11; break; }
            }
            if (hasStraightDraw(combined)) strength += 0.08;
        }
        return Math.min(0.99, strength);
    }

    private double drawHandStrength(List<Card> hole) {
        LowballValue lowball = evaluateTwoSeven(hole);
        double kickerQuality = lowball.ranks().stream().skip(1).mapToInt(Integer::intValue).sum() / 5.0;
        return Math.min(0.99, 0.24 + lowball.ranks().get(0) * 0.075 + kickerQuality * 0.018);
    }

    private boolean hasStraightDraw(List<Card> cards) {
        List<Integer> ranks = cards.stream().map(Card::rank).distinct().sorted().toList();
        if (ranks.contains(14)) { List<Integer> aceLow = new ArrayList<>(ranks); aceLow.add(1); ranks = aceLow; }
        for (int start = 1; start <= 10; start++) {
            int present = 0;
            for (int rank = start; rank < start + 5; rank++) if (ranks.contains(rank)) present++;
            if (present == 4) return true;
        }
        return false;
    }

    private void completeActionRound() {
        List<Player> active = handOpponents();
        if (active.isEmpty()) {
            if (heroFolded) finishHand(null, "The table folds. Hand over.");
            else finishHand(hero, "Everyone folds. You take the pot.");
            return;
        }
        if (shouldEndAfterFolds(heroFolded, active.size())) {
            Player winner = active.get(0);
            winner.stack += pot;
            finishHand(null, winner.name + " wins the pot uncontested.");
            return;
        }
        boolean playersOweAction = active.stream().anyMatch(player -> player.stack > 0
            && (!player.actedThisRound || player.streetBet < currentBet));
        if (playersOweAction) { queueOpponentActions(); return; }
        if (!heroFolded && hero.stack > 0 && (!hero.actedThisRound || heroStreetBet < currentBet)) {
            heroTurn = true;
            actionPrompt = "BET TO YOU  ·  " + formatChips(currentBet - heroStreetBet);
            statusLabel.setText("Action is back to you. " + formatChips(currentBet - heroStreetBet) + " to call.");
            refreshTable(); refreshActions();
            startActionClock();
            return;
        }
        if (gameMode.draw && !drawCompleted) { beginDrawPhase(); return; }
        if (gameMode.draw && drawCompleted) { showdown(); return; }
        if (board.size() == 5) { showdown(); return; }
        advanceStreet();
        if (heroFolded || hero.stack == 0) queueOpponentActions();
    }

    private void advanceStreet() {
        if (board.isEmpty()) { board.add(draw()); board.add(draw()); board.add(draw()); }
        else if (board.size() < 5) board.add(draw());
        currentBet = 0; heroStreetBet = 0; hero.lastAction = "";
        hero.actedThisRound = false;
        for (Player player : opponents) {
            player.streetBet = 0;
            player.actedThisRound = false;
            player.lastAction = player.foldedThisHand ? "FOLDED" : (player.stack == 0 ? "ALL-IN" : "");
        }
        heroTurn = !heroFolded && hero.stack > 0;
        handLabel.setText(String.format("HAND %02d  ·  %s", handNumber, streetName()));
        actionPrompt = heroFolded || hero.stack == 0 ? streetName() + "  ·  TABLE ACTION" : streetName() + "  ·  YOUR ACTION";
        statusLabel.setText(board.size() == 3 ? "The flop is dealt." : board.size() == 4 ? "The turn is dealt." : "The river is dealt.");
        playSoundAlert(SoundCue.DEAL);
        refreshTable(); refreshActions();
        if (heroTurn) startActionClock();
    }

    private void beginDrawPhase() {
        for (Player player : handOpponents()) {
            player.isActing = true; refreshTable();
            List<Card> kept = new ArrayList<>();
            for (Card card : player.holeCards) {
                boolean paired = player.holeCards.stream().anyMatch(other -> other != card && other.rank() == card.rank());
                if (card.rank() < 10 && !paired) kept.add(card);
            }
            int drawCount = gameMode.holeCardCount - kept.size();
            for (Card card : player.holeCards) if (!kept.contains(card)) discardPile.add(card);
            player.holeCards.clear(); player.holeCards.addAll(kept);
            for (int i = 0; i < drawCount; i++) { recycleDiscardsIfNeeded(); player.holeCards.add(draw()); }
            player.isActing = false; player.lastAction = "DRAWS " + drawCount;
        }
        drawPending = !heroFolded && hero.stack > 0;
        drawCompleted = !drawPending;
        heroTurn = drawPending;
        if (!drawPending) { queueOpponentActions(); return; }
        actionPrompt = "2-7 DRAW  ·  SELECT CARDS TO DISCARD";
        statusLabel.setText("Choose cards to discard, then press Draw & Continue.");
        playSoundAlert(SoundCue.DRAW); refreshTable(); refreshActions();
        startActionClock();
    }

    private void completePlayerDraw() {
        List<Card> kept = new ArrayList<>();
        for (int i = 0; i < heroCards.size(); i++) if (i >= heroDiscards.size() || !heroDiscards.get(i)) kept.add(heroCards.get(i));
        int drawCount = heroCards.size() - kept.size();
        for (int i = 0; i < heroCards.size(); i++) if (i < heroDiscards.size() && heroDiscards.get(i)) discardPile.add(heroCards.get(i));
        heroCards.clear(); heroCards.addAll(kept);
        for (int i = 0; i < drawCount; i++) { recycleDiscardsIfNeeded(); heroCards.add(draw()); }
        heroDiscards.clear();
        for (int i = 0; i < heroCards.size(); i++) heroDiscards.add(false);
        drawPending = false; drawCompleted = true; heroTurn = true;
        actionPrompt = "POST-DRAW  ·  YOUR ACTION";
        statusLabel.setText("You drew " + drawCount + (drawCount == 1 ? " card." : " cards."));
        playSoundAlert(SoundCue.DRAW); refreshTable(); refreshActions();
        startActionClock();
    }

    private void startActionClock() {
        startActionClock(true);
    }

    private void startActionClock(boolean resetRemaining) {
        stopActionClock();
        if (handOver || !heroTurn || heroFolded || observedTable != null) return;
        if (resetRemaining) actionSecondsRemaining = ACTION_SECONDS;
        refreshActionTimerDisplay();
        actionClock = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            actionSecondsRemaining--;
            refreshActionTimerDisplay();
            if (actionSecondsRemaining <= 0) {
                stopActionClock();
                if (drawPending) completePlayerDraw();
                else if (actionTimeoutShouldFold(currentBet, heroStreetBet, hero.stack)) fold();
                else checkOrCall();
            }
        }));
        actionClock.setCycleCount(Timeline.INDEFINITE);
        actionClock.play();
    }

    private void stopActionClock() {
        if (actionClock != null) actionClock.stop();
    }

    private void useTimeBankChip() {
        if (timeBankChips <= 0 || handOver || !heroTurn || observedTable != null) return;
        timeBankChips--;
        actionSecondsRemaining += timeBankExtensionSeconds();
        if (actionClock == null || actionClock.getStatus() != javafx.animation.Animation.Status.RUNNING) startActionClock(false);
        else refreshActionTimerDisplay();
        playSoundAlert(SoundCue.TIME_BANK);
        Label chip = new Label("TIME +30");
        chip.setStyle("-fx-background-color: #d4bd81; -fx-text-fill: #19251f; -fx-font-weight: bold; -fx-padding: 5 9; -fx-background-radius: 12;");
        chip.relocate(360, 590); tablePane.getChildren().add(chip);
        FadeTransition fade = new FadeTransition(Duration.millis(850), chip); fade.setFromValue(1); fade.setToValue(0);
        fade.setOnFinished(event -> tablePane.getChildren().remove(chip)); fade.play();
        refreshActionTimerDisplay();
    }

    private void refreshActionTimerDisplay() {
        if (actionTimerLabel != null) {
            actionTimerLabel.setText(Integer.toString(Math.max(0, actionSecondsRemaining)));
            actionTimerLabel.setStyle(actionSecondsRemaining <= 5 ? "-fx-text-fill: #bd5548; -fx-font-size: 18px; -fx-font-weight: 700;"
                    : "-fx-text-fill: #e8cf91; -fx-font-size: 18px; -fx-font-weight: 700;");
        }
        if (timeBankLabel != null) timeBankLabel.setText(timeBankChips + " CHIPS");
        if (timeBankButton != null) timeBankButton.setDisable(timeBankChips <= 0 || handOver || !heroTurn || observedTable != null);
    }

    private void showdown() {
        List<Player> contenders = new ArrayList<>(handOpponents());
        if (!heroFolded) contenders.add(hero);
        hero.showCards = !heroFolded;
        hero.lastAction = heroFolded ? "FOLDED" : "SHOWDOWN";
        for (Player player : handOpponents()) {
            player.showCards = true;
            player.lastAction = "SHOWDOWN";
        }
        if (gameMode.draw) {
            LowballValue best = null;
            List<Player> winners = new ArrayList<>();
            for (Player player : contenders) {
                List<Card> cards = player == hero ? heroCards : player.holeCards;
                LowballValue value = evaluateTwoSeven(cards);
                int comparison = best == null ? 1 : value.compareTo(best);
                if (comparison > 0) { best = value; winners.clear(); winners.add(player); }
                else if (comparison == 0) winners.add(player);
            }
            if (winners.isEmpty()) { finishHand(null, "No live hand remains."); return; }
            awardPot(winners, pot);
            playSoundAlert(SoundCue.WIN);
            finishHand(null, winners.contains(hero) ? "You win the 2-7 lowball hand." : winners.get(0).name + " wins the 2-7 lowball hand.");
            return;
        }

        HandValue bestHigh = null;
        List<Player> highWinners = new ArrayList<>();
        LowValue bestLow = null;
        List<Player> lowWinners = new ArrayList<>();
        for (Player player : contenders) {
            List<Card> hole = player == hero ? heroCards : player.holeCards;
            HandValue high = gameMode.omaha ? evaluateOmahaBest(hole, board) : evaluateBest(joinCards(hole, board));
            int highComparison = bestHigh == null ? 1 : high.compareTo(bestHigh);
            if (highComparison > 0) { bestHigh = high; highWinners.clear(); highWinners.add(player); }
            else if (highComparison == 0) highWinners.add(player);
            if (gameMode.hiLo) {
                LowValue low = evaluateOmahaLow(hole, board);
                if (low != null) {
                    int lowComparison = bestLow == null ? 1 : low.compareTo(bestLow);
                    if (lowComparison > 0) { bestLow = low; lowWinners.clear(); lowWinners.add(player); }
                    else if (lowComparison == 0) lowWinners.add(player);
                }
            }
        }
        if (highWinners.isEmpty()) { finishHand(null, "No live hand remains."); return; }
        if (gameMode.hiLo && !lowWinners.isEmpty()) {
            awardPot(highWinners, (pot + 1) / 2);
            awardPot(lowWinners, pot / 2);
        } else awardPot(highWinners, pot);
        playSoundAlert(SoundCue.WIN);
        finishHand(null, highWinners.contains(hero) ? "You win or split the high hand at showdown." : highWinners.get(0).name + " wins at showdown.");
    }

    private static List<Card> joinCards(List<Card> hole, List<Card> community) {
        List<Card> cards = new ArrayList<>(hole); cards.addAll(community); return cards;
    }

    private String handNameFor(List<Card> hole, List<Card> community) {
        if (gameMode.draw) {
            LowballValue lowball = evaluateTwoSeven(hole);
            return "2-7 low · " + handCategoryName(8 - lowball.ranks().get(0));
        }
        HandValue value = gameMode.omaha ? evaluateOmahaBest(hole, community) : evaluateBest(joinCards(hole, community));
        String name = handCategoryName(value.ranks().get(0));
        if (gameMode.hiLo && evaluateOmahaLow(hole, community) != null) name += " + qualifying low";
        return name;
    }

    static String handCategoryName(int category) {
        return switch (category) {
            case 8 -> "Straight flush";
            case 7 -> "Four of a kind";
            case 6 -> "Full house";
            case 5 -> "Flush";
            case 4 -> "Straight";
            case 3 -> "Three of a kind";
            case 2 -> "Two pair";
            case 1 -> "One pair";
            default -> "High card";
        };
    }

    private void awardPot(List<Player> winners, int amount) {
        int share = amount / winners.size(); int remainder = amount % winners.size();
        for (Player winner : winners) winner.stack += share + (remainder-- > 0 ? 1 : 0);
        if (winners.contains(hero)) awardTimeBankChip();
    }

    private void awardTimeBankChip() {
        if (timeBankEarnedThisHand) return;
        timeBankEarnedThisHand = true;
        timeBankChips = rewardTimeBankChip(timeBankChips, MAX_TIME_BANK_CHIPS);
        refreshActionTimerDisplay();
    }

    static int rewardTimeBankChip(int current, int maximum) { return Math.min(maximum, current + 1); }
    static int timeBankExtensionSeconds() { return TIME_BANK_SECONDS; }
    static boolean actionTimeoutShouldFold(int currentBet, int streetBet, int stack) {
        return chipsToCall(currentBet, streetBet, stack) > 0;
    }

    private void finishHand(Player winner, String message) {
        stopActionClock();
        if (winner == hero) hero.stack += pot;
        if (winner == hero) awardTimeBankChip();
        if (actionDelay != null) actionDelay.stop();
        handOver = true; heroTurn = false; statusLabel.setText(message); actionPrompt = winner == hero ? "POT WON" : "HAND COMPLETE";
        heroStreetBet = 0; currentBet = 0; updateTournamentInfo(); refreshTable(); refreshActions();
        saveReplay(message);
        rebalanceTablesBetweenHands();
        refreshRemoteTableList();
        refreshTable();
        scheduleAutoDeal();
    }

    private void saveReplay(String result) {
        if (replaySaved || handNumber == 0) return;
        replaySaved = true;
        List<ReplaySeat> seats = new ArrayList<>();
        if (hero.showCards) seats.add(new ReplaySeat(hero.name, hero.lastAction, List.copyOf(heroCards), handNameFor(heroCards, board)));
        for (Player player : opponents) {
            List<Card> shownCards = player.showCards ? List.copyOf(player.holeCards) : List.of();
            String action = player.foldedThisHand ? "FOLDED" : player.lastAction;
            seats.add(new ReplaySeat(player.name, action, shownCards,
                    player.showCards ? handNameFor(player.holeCards, board) : ""));
        }
        recentHands.addLast(new HandReplay(handNumber, List.copyOf(heroCards), List.copyOf(board), pot,
                result, List.copyOf(seats)));
        while (recentHands.size() > 5) recentHands.removeFirst();
        if (replayButton != null) {
            replayButton.setText("REPLAY  " + recentHands.size() + "/5");
            replayButton.setDisable(false);
        }
        updateTournamentInfo();
    }

    private void simulateRemoteTables() {
        for (RemoteTable table : remoteTables) {
            List<Player> active = table.players.stream().filter(player -> player.stack > 0).toList();
            if (active.size() < 2) continue;
            List<Card> remoteDeck = new ArrayList<>();
            List<Card> remoteDiscards = new ArrayList<>();
            for (int rank = 2; rank <= 14; rank++) for (int suit = 0; suit < 4; suit++) remoteDeck.add(new Card(rank, suit));
            Collections.shuffle(remoteDeck, random);
            table.board.clear(); table.pot = 0; table.handNumber++;
            for (Player player : table.players) {
                player.holeCards.clear(); player.showCards = false; player.foldedThisHand = player.stack <= 0;
                player.lastAction = player.foldedThisHand ? "OUT" : "";
                if (player.foldedThisHand) continue;
                for (int card = 0; card < gameMode.holeCardCount; card++) player.holeCards.add(remoteDeck.remove(remoteDeck.size() - 1));
                int contribution = Math.min(BIG_BLINDS[levelIndex], player.stack);
                if (player.stack > contribution && random.nextDouble() < 0.24) {
                    contribution += Math.min(BIG_BLINDS[levelIndex] * (2 + random.nextInt(5)), player.stack - contribution);
                }
                player.stack -= contribution; table.pot += contribution;
            }
            if (gameMode.draw) {
                for (Player player : active) {
                    List<Card> kept = new ArrayList<>();
                    for (Card card : player.holeCards) {
                        boolean paired = player.holeCards.stream().anyMatch(other -> other != card && other.rank() == card.rank());
                        if (card.rank() < 10 && !paired) kept.add(card);
                    }
                    int drawCount = gameMode.holeCardCount - kept.size();
                    for (Card card : player.holeCards) if (!kept.contains(card)) remoteDiscards.add(card);
                    player.holeCards.clear(); player.holeCards.addAll(kept);
                    for (int i = 0; i < drawCount; i++) {
                        if (remoteDeck.isEmpty() && !remoteDiscards.isEmpty()) {
                            remoteDeck.addAll(remoteDiscards); remoteDiscards.clear(); Collections.shuffle(remoteDeck, random);
                        }
                        if (!remoteDeck.isEmpty()) player.holeCards.add(remoteDeck.remove(remoteDeck.size() - 1));
                    }
                }
            } else {
                for (int card = 0; card < 5; card++) table.board.add(remoteDeck.remove(remoteDeck.size() - 1));
            }
            Player winner = active.get(0);
            Player lowWinner = null;
            HandValue bestHigh = null;
            LowValue bestLow = null;
            LowballValue bestLowball = null;
            int winnerCategory = -1;
            for (Player player : active) {
                if (gameMode.draw) {
                    LowballValue value = evaluateTwoSeven(player.holeCards);
                    if (bestLowball == null || value.compareTo(bestLowball) > 0) { winner = player; bestLowball = value; }
                } else {
                    HandValue value = gameMode.omaha ? evaluateOmahaBest(player.holeCards, table.board)
                            : evaluateBest(joinCards(player.holeCards, table.board));
                    if (bestHigh == null || value.compareTo(bestHigh) > 0) {
                        winner = player; bestHigh = value; winnerCategory = value.ranks().get(0);
                    }
                    if (gameMode.hiLo) {
                        LowValue low = evaluateOmahaLow(player.holeCards, table.board);
                        if (low != null && (bestLow == null || low.compareTo(bestLow) > 0)) { lowWinner = player; bestLow = low; }
                    }
                }
                player.showCards = true;
            }
            if (gameMode.hiLo && lowWinner != null && lowWinner != winner) {
                awardPot(List.of(winner), (table.pot + 1) / 2); awardPot(List.of(lowWinner), table.pot / 2);
            } else winner.stack += table.pot;
            for (Player player : active) player.lastAction = player == winner || player == lowWinner ? "SHOWDOWN" : "CALLED";
            table.lastAction = String.format("HAND %d  ·  %s WINS", table.handNumber, winner.name.toUpperCase());
                boolean premiumHand = winnerCategory >= 7;
                boolean bigPot = table.pot >= Math.max(BIG_BLINDS[levelIndex] * active.size() * 2, eventOption.startingStack() / 8);
                if (premiumHand || bigPot) {
                String reason = premiumHand ? (winnerCategory == 8 ? "STRAIGHT FLUSH" : "QUADS") : "BIG POT";
                List<ReplaySeat> seats = active.stream().map(player -> new ReplaySeat(player.name, player.lastAction,
                    List.copyOf(player.holeCards), handNameFor(player.holeCards, table.board))).toList();
                String title = String.format("HOT HAND  ·  TABLE %02d  ·  %s  ·  %s POT",
                    table.number, reason, formatChips(table.pot));
                hotHands.addLast(new HandReplay(table.handNumber, List.copyOf(winner.holeCards),
                    List.copyOf(table.board), table.pot, title, seats));
                while (hotHands.size() > 20) hotHands.removeFirst();
                refreshHotHandList();
                }
        }
        rebalanceTablesBetweenHands();
        refreshRemoteTableList();
        tablePane.render();
        updateTournamentInfo();
    }

    private void rebalanceTablesBetweenHands() {
        if (!handOver) return;
        List<Player> localSurvivors = new ArrayList<>(opponents.stream().filter(player -> player.stack > 0).toList());
        List<Player> remoteSurvivors = new ArrayList<>();
        for (RemoteTable table : remoteTables) for (Player player : table.players) if (player.stack > 0) remoteSurvivors.add(player);
        int totalPlayers = localSurvivors.size() + remoteSurvivors.size() + (hero.stack > 0 ? 1 : 0);
        if (totalPlayers == 0) return;
        int tableCount = (totalPlayers + 8) / 9;
        int baseSize = totalPlayers / tableCount;
        int extraSeats = totalPlayers % tableCount;
        int targetLocalSize = baseSize + (extraSeats > 0 ? 1 : 0);
        int targetLocalOpponents = Math.max(0, targetLocalSize - (hero.stack > 0 ? 1 : 0));
        while (localSurvivors.size() > targetLocalOpponents) remoteSurvivors.add(localSurvivors.remove(localSurvivors.size() - 1));
        while (localSurvivors.size() < targetLocalOpponents && !remoteSurvivors.isEmpty()) {
            localSurvivors.add(remoteSurvivors.remove(0));
        }
        opponents.clear(); opponents.addAll(localSurvivors);
        refreshLocalPlayerList();

        int remoteCount = Math.max(0, tableCount - 1);
        while (remoteTables.size() < remoteCount) remoteTables.add(new RemoteTable(nextRemoteTableNumber++, new ArrayList<>()));
        while (remoteTables.size() > remoteCount) {
            RemoteTable removed = remoteTables.remove(remoteTables.size() - 1);
            if (observedTable == removed) { observedTable = null; tablePane.showLocalTable(); }
        }
        int cursor = 0;
        for (int index = 0; index < remoteTables.size(); index++) {
            int tableIndex = index + 1;
            int seats = baseSize + (tableIndex < extraSeats ? 1 : 0);
            RemoteTable table = remoteTables.get(index);
            table.players.clear();
            for (int seat = 0; seat < seats && cursor < remoteSurvivors.size(); seat++) table.players.add(remoteSurvivors.get(cursor++));
        }
    }

    private void refreshRemoteTableList() {
        if (remoteTableList == null) return;
        remoteTableList.getChildren().clear();
        for (RemoteTable table : remoteTables) {
            Button tableButton = new Button(table.label());
            tableButton.getStyleClass().add("utility-button"); tableButton.setMaxWidth(Double.MAX_VALUE);
            tableButton.setOnAction(event -> observeTable(table));
            remoteTableList.getChildren().add(tableButton);
        }
    }

    private void scheduleAutoDeal() {
        if (autoDealCountdown != null) autoDealCountdown.stop();
        if (hero.stack <= 0) return;
        final int[] secondsRemaining = {5};
        startButton.setText("DEAL NEXT HAND  ·  5");
        autoDealCountdown = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            secondsRemaining[0]--;
            if (secondsRemaining[0] > 0) startButton.setText("DEAL NEXT HAND  ·  " + secondsRemaining[0]);
        }));
        autoDealCountdown.setCycleCount(5);
        autoDealCountdown.setOnFinished(event -> { if (handOver && hero.stack > 0) startHand(); });
        autoDealCountdown.play();
    }

    private List<Player> liveOpponents() { return opponents.stream().filter(p -> p.stack > 0).toList(); }
    private List<Player> handOpponents() { return opponents.stream().filter(p -> !p.foldedThisHand && !p.holeCards.isEmpty()).toList(); }

    private String streetName() {
        return switch (board.size()) { case 0 -> "PREFLOP"; case 3 -> "THE FLOP"; case 4 -> "THE TURN"; case 5 -> "THE RIVER"; default -> "IN PLAY"; };
    }

    private void refreshActions() {
        if (checkCallButton == null) return;
        boolean active = !handOver && hero.stack > 0 && heroTurn && observedTable == null;
        foldButton.setDisable(!active || drawPending); checkCallButton.setDisable(!active); raiseButton.setDisable(!active || drawPending);
        startButton.setDisable(!handOver || hero.stack <= 0); startButton.setVisible(handOver); startButton.setManaged(handOver);
        int call = Math.max(0, currentBet - heroStreetBet);
        checkCallButton.setText(drawPending ? "DRAW & CONTINUE" : (call == 0 ? "CHECK" : "CALL  " + formatChips(call)));
        int raiseMaximum = hero.stack + heroStreetBet;
        if (gameMode == GameMode.POT_LIMIT_OMAHA) raiseMaximum = Math.min(raiseMaximum, currentBet + pot + Math.max(0, currentBet - heroStreetBet));
        int minimum = Math.min(raiseMaximum, currentBet + BIG_BLINDS[levelIndex]);
        raiseSlider.setMin(Math.min(minimum, 4000));
        raiseSlider.setMax(Math.max(raiseSlider.getMin(), Math.min(raiseMaximum, 15_000)));
        raiseSlider.setValue(Math.max(raiseSlider.getMin(), Math.min(raiseSlider.getMax(), currentBet == 0 ? 600 : currentBet + BIG_BLINDS[levelIndex])));
        raiseSlider.setDisable(!active || drawPending);
        refreshActionTimerDisplay();
    }

    private void refreshTable() {
        if (tablePane == null) return;
        tablePane.render();
        heroStackLabel.setText(formatChips(hero.stack) + " CHIPS");
        heroNameLabel.setText(hero.stack > 0 ? "YOU  ·  " + heroPosition() : "YOU  ·  ELIMINATED");
        for (Node node : localPlayerList.getChildren()) if (node instanceof HBox row && row.getUserData() instanceof Player player) {
            VBox details = (VBox) row.getChildren().get(1);
            ((Label) details.getChildren().get(1)).setText(player.stack > 0 ? formatChips(player.stack) : "ELIMINATED");
            row.setOpacity(player.stack > 0 ? 1 : 0.42);
        }
        opponentCount.setText((opponents.size() + 1) + " PLAYERS");
        recordStackHistory(); refreshRanking();
    }

    private void tickClock() {
        secondsInLevel++;
        if (secondsInLevel >= blindIntervalSeconds) {
            secondsInLevel = 0; levelIndex = Math.min(levelIndex + 1, SMALL_BLINDS.length - 1);
            statusLabel.setText("Blinds increased to " + formatChips(SMALL_BLINDS[levelIndex]) + " / " + formatChips(BIG_BLINDS[levelIndex]) + ".");
            refreshActions();
        }
        updateTournamentInfo();
    }

    private void updateTournamentInfo() {
        if (blindValue == null) return;
        int prizePool = fieldSize * eventOption.buyIn();
        levelValue.setText(String.format("%02d", levelIndex + 1));
        blindValue.setText(formatChips(SMALL_BLINDS[levelIndex]) + " / " + formatChips(BIG_BLINDS[levelIndex]));
        int remaining = blindIntervalSeconds - secondsInLevel;
        levelClock.setText(String.format("%02d:%02d", remaining / 60, remaining % 60));
        int remainingPlayers = liveOpponents().size() + (hero.stack > 0 ? 1 : 0)
            + remoteTables.stream().mapToInt(table -> (int) table.players.stream().filter(player -> player.stack > 0).count()).sum();
        fieldValue.setText(String.valueOf(remainingPlayers));
        if (lastDisplayedFieldSize >= 0 && remainingPlayers < lastDisplayedFieldSize) {
            fieldValue.setStyle("-fx-text-fill: #e4c26c; -fx-font-size: 16px; -fx-font-weight: 700;");
            Timeline goldFlash = new Timeline(new KeyFrame(Duration.millis(1100), event -> fieldValue.setStyle("")));
            goldFlash.play();
        }
        lastDisplayedFieldSize = remainingPlayers;
        recordStackHistory();
        refreshRanking();
        prizePoolValue.setText(formatMoney(prizePool)); firstPrizeValue.setText(formatMoney(prizePool * PAYOUT_TENTHS[0] / 1000));
        StringBuilder payouts = new StringBuilder();
        for (int i = 0; i < 7; i++)
            payouts.append(String.format("%d%s   %s%n", i + 1, ordinal(i + 1), formatMoney(prizePool * PAYOUT_TENTHS[i] / 1000)));
        payoutLabel.setText(payouts.toString().stripTrailing());
    }

    private void recordStackHistory() {
        List<Player> players = new ArrayList<>(); players.add(hero); players.addAll(opponents);
        for (RemoteTable table : remoteTables) players.addAll(table.players);
        for (Player player : players) {
            if (player.stackHistory.isEmpty() || player.stackHistory.get(player.stackHistory.size() - 1) != player.stack) {
                player.stackHistory.add(player.stack);
            }
        }
    }

    private static String ordinal(int place) { return place == 1 ? "st" : place == 2 ? "nd" : place == 3 ? "rd" : "th"; }
    private String heroPosition() {
        if (buttonSeat == 0) return "BUTTON";
        if ((buttonSeat + 1) % (opponents.size() + 1) == 0) return "SMALL BLIND";
        if ((buttonSeat + 2) % (opponents.size() + 1) == 0) return "BIG BLIND";
        return "SEAT 09";
    }
    private static String formatChips(int chips) { return String.format("%,d", Math.max(0, chips)); }
    private static String formatMoney(int amount) { return String.format("€%,d", amount); }
    private static int roundHundred(double value) { return (int) (Math.round(value / 100) * 100); }

    static List<Integer> balancedTableSizes(int participants) {
        if (participants < 1) throw new IllegalArgumentException("A tournament needs at least one player.");
        int tableCount = (participants + 8) / 9;
        int baseSize = participants / tableCount;
        int largerTables = participants % tableCount;
        List<Integer> sizes = new ArrayList<>(tableCount);
        for (int table = 0; table < tableCount; table++) sizes.add(baseSize + (table < largerTables ? 1 : 0));
        return List.copyOf(sizes);
    }

    static int chipsToCall(int currentBet, int streetBet, int stack) {
        return Math.min(Math.max(0, currentBet - streetBet), Math.max(0, stack));
    }

    static boolean shouldEndAfterFolds(boolean heroFolded, int liveOpponents) {
        return heroFolded && liveOpponents == 1;
    }

    private static Label cardLabel(Card card, boolean hidden) {
        Label label = new Label(hidden ? "◆" : card.display()); label.getStyleClass().add("playing-card");
        if (hidden) label.getStyleClass().add("card-back");
        else if (card.suit == 1 || card.suit == 2) label.getStyleClass().add("red-card");
        return label;
    }

    private final class TablePane extends Pane {
        private final Ellipse felt = new Ellipse();
        private final Label tableName = new Label("MONTE CARLO  ·  TABLE 07");
        private final Label potCaption = new Label("POT"), potAmount = new Label("0"), tableAction = new Label("WAITING FOR NEXT HAND");
        private final HBox community = new HBox(8), holeCards = new HBox(9);
        private final List<SeatView> seats = new ArrayList<>();
        private final Label stack = new Label(), name = new Label();
        private RemoteTable viewedTable;
        private int renderedBoardCount, renderedHoleCardCount;

        TablePane() {
            getStyleClass().add("table-area"); felt.getStyleClass().add("felt");
            tableName.getStyleClass().add("table-name"); potCaption.getStyleClass().add("pot-caption");
            potAmount.getStyleClass().add("pot-amount"); tableAction.getStyleClass().add("table-action");
            stack.getStyleClass().add("hero-stack"); name.getStyleClass().add("hero-name");
            for (int i = 0; i < 5; i++) { Label slot = new Label(); slot.getStyleClass().add("card-slot"); boardSlots.add(slot); community.getChildren().add(slot); }
            for (int i = 0; i < gameMode.holeCardCount; i++) { Label slot = new Label("?"); slot.getStyleClass().addAll("playing-card", "card-back"); heroSlots.add(slot); holeCards.getChildren().add(slot); }
            getChildren().addAll(felt, tableName, potCaption, potAmount, tableAction, community, holeCards, stack, name);
            setDisplayedPlayers(opponents);
            heroStackLabel = stack; heroNameLabel = name; actionHint = tableAction;
            widthProperty().addListener((o, oldV, newV) -> layoutTable());
            heightProperty().addListener((o, oldV, newV) -> layoutTable());
        }

        void render() {
            boolean localView = viewedTable == null;
            List<Card> visibleBoard = localView ? board : viewedTable.board;
            List<Player> visiblePlayers = localView ? opponents : viewedTable.players;
            setDisplayedPlayers(visiblePlayers);
            int priorBoardCount = renderedBoardCount;
            int priorHoleCardCount = renderedHoleCardCount;
            potAmount.setText(formatChips(localView ? pot : viewedTable.pot));
            tableAction.setText(localView ? (handOver ? "WAITING FOR NEXT HAND" : actionPrompt)
                    : "TABLE " + viewedTable.number + "  ·  " + viewedTable.lastAction);
            for (int i = 0; i < 5; i++) {
                Label card = i < visibleBoard.size() ? cardLabel(visibleBoard.get(i), false) : new Label();
                if (i >= visibleBoard.size()) card.getStyleClass().add("card-slot");
                card.setPrefSize(54, 76); community.getChildren().set(i, card);
                if (i >= priorBoardCount && i < visibleBoard.size()) animateReveal(card, i - priorBoardCount);
            }
            for (int i = 0; i < gameMode.holeCardCount; i++) {
                Label card = localView && i < heroCards.size() ? cardLabel(heroCards.get(i), false) : cardLabel(new Card(14, 0), true);
                card.setPrefSize(58, 82); holeCards.getChildren().set(i, card);
                if (localView && i >= priorHoleCardCount && i < heroCards.size()) animateReveal(card, i - priorHoleCardCount);
            }
            if (localView && gameMode.draw) {
                for (int i = 0; i < heroCards.size(); i++) {
                    int cardIndex = i;
                    Label card = cardIndex < holeCards.getChildren().size() ? (Label) holeCards.getChildren().get(cardIndex) : null;
                    if (card == null) continue;
                    boolean selected = drawPending && cardIndex < heroDiscards.size() && heroDiscards.get(cardIndex);
                    card.setTranslateY(selected ? -12 : 0);
                    if (drawPending) card.setOnMouseClicked(event -> {
                        heroDiscards.set(cardIndex, !heroDiscards.get(cardIndex));
                        render();
                    });
                    else card.setOnMouseClicked(null);
                }
            }
            renderedBoardCount = visibleBoard.size();
            renderedHoleCardCount = localView ? heroCards.size() : 0;
            holeCards.setVisible(localView); holeCards.setManaged(localView);
            stack.setText(localView ? formatChips(hero.stack) + " CHIPS" : "REMOTE TABLE");
            name.setText(localView ? (hero.stack > 0 ? "YOU  ·  " + heroPosition() : "YOU  ·  ELIMINATED") : "TABLE " + viewedTable.number);
            seats.forEach(SeatView::refresh); layoutTable();
        }

        void showRemoteTable(RemoteTable table) { viewedTable = table; resetCardAnimation(); render(); }
        void showLocalTable() { viewedTable = null; resetCardAnimation(); render(); }

        private void setDisplayedPlayers(List<Player> players) {
            while (seats.size() > players.size()) {
                SeatView removed = seats.remove(seats.size() - 1);
                getChildren().remove(removed);
            }
            while (seats.size() < players.size()) {
                SeatView seat = new SeatView(players.get(seats.size()));
                seats.add(seat); getChildren().add(seat);
            }
            for (int i = 0; i < players.size(); i++) seats.get(i).setPlayer(players.get(i));
        }

        void resetCardAnimation() { renderedBoardCount = 0; renderedHoleCardCount = 0; }

        private void animateReveal(Node node, int order) {
            node.setOpacity(0);
            node.setScaleX(0.75);
            node.setScaleY(0.75);
            FadeTransition fade = new FadeTransition(Duration.millis(260), node);
            fade.setFromValue(0); fade.setToValue(1);
            ScaleTransition scale = new ScaleTransition(Duration.millis(260), node);
            scale.setFromX(0.75); scale.setFromY(0.75); scale.setToX(1); scale.setToY(1);
            ParallelTransition reveal = new ParallelTransition(fade, scale);
            reveal.setDelay(Duration.millis(order * 110L));
            reveal.play();
        }

        void animateChips(Player player, int amount) {
            if (amount <= 0 || getWidth() <= 0 || getHeight() <= 0) return;
            double centerX = getWidth() * 0.52, centerY = getHeight() * 0.51;
            double fromX = centerX, fromY = getHeight() - 105;
            if (player != hero) {
                int seatIndex = (viewedTable == null ? opponents : viewedTable.players).indexOf(player);
                if (seatIndex >= 0 && seatIndex < seats.size()) {
                    fromX = seats.get(seatIndex).getLayoutX() + 55;
                    fromY = seats.get(seatIndex).getLayoutY() + 34;
                }
            }
            Label chips = new Label("●  " + formatChips(amount));
            chips.setStyle("-fx-background-color: #d3b878; -fx-text-fill: #19251f; -fx-padding: 4 7; -fx-background-radius: 12; -fx-font-size: 10px; -fx-font-weight: bold;");
            chips.relocate(fromX, fromY); getChildren().add(chips);
            TranslateTransition move = new TranslateTransition(Duration.millis(430), chips);
            move.setToX(centerX - fromX); move.setToY(centerY - 20 - fromY);
            FadeTransition fade = new FadeTransition(Duration.millis(430), chips);
            fade.setFromValue(1); fade.setToValue(0.25);
            ParallelTransition flight = new ParallelTransition(move, fade);
            flight.setOnFinished(event -> getChildren().remove(chips)); flight.play();
        }

        private void layoutTable() {
            double w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
            double cx = w * 0.52, cy = h * 0.51;
            felt.setCenterX(cx); felt.setCenterY(cy); felt.setRadiusX(w * 0.405); felt.setRadiusY(h * 0.34);
            tableName.relocate(cx - 105, cy - h * 0.20); potCaption.relocate(cx - 22, cy - 34);
            potAmount.relocate(cx - 50, cy - 11); tableAction.relocate(cx - 105, cy + 87);
            community.resizeRelocate(cx - 153, cy + 8, 306, 78); holeCards.resizeRelocate(cx - Math.min(170, gameMode.holeCardCount * 30), h - 146, Math.min(360, gameMode.holeCardCount * 68), 84);
            stack.relocate(cx - 48, h - 55); name.relocate(cx - 58, h - 29);
            for (int i = 0; i < seats.size(); i++) {
                double angle = Math.toRadians(215 + i * (290.0 / (seats.size() - 1)));
                seats.get(i).resizeRelocate(cx + Math.cos(angle) * w * 0.37 - 68, cy + Math.sin(angle) * h * 0.30 - 43, 136, 86);
            }
        }
    }

    private final class SeatView extends VBox {
        private Player player;
        private final Label playerName = new Label(), playerStack = new Label(), playerAction = new Label();
        private final HBox revealedCards = new HBox(3);
        SeatView(Player player) {
            this.player = player; playerName.getStyleClass().add("seat-name"); playerStack.getStyleClass().add("seat-stack");
            playerAction.getStyleClass().add("seat-action");
            revealedCards.getStyleClass().add("revealed-cards");
            setAlignment(Pos.CENTER); setSpacing(2); getStyleClass().add("seat");
            getChildren().addAll(playerName, playerStack, playerAction, revealedCards); refresh();
        }
        void setPlayer(Player player) { this.player = player; refresh(); }
        void refresh() {
            playerName.setText(player.name); playerStack.setText(player.stack > 0 ? formatChips(player.stack) : "OUT");
                String state = player.stack == 0 && player.holeCards.isEmpty() ? "ELIMINATED"
                    : (player.foldedThisHand ? "FOLDED" : (player.stack == 0 ? "ALL-IN" : player.lastAction));
                playerAction.setText(player.isActing ? "DECIDING..." : (state.isBlank() ? "IN HAND" : state));
            getStyleClass().removeAll("acting-seat", "folded-seat");
            if (player.isActing) getStyleClass().add("acting-seat");
                if (player.foldedThisHand && !player.holeCards.isEmpty()) getStyleClass().add("folded-seat");
            revealedCards.getChildren().clear();
            for (Card card : player.holeCards) if (player.showCards) {
                Label face = new Label(card.display()); face.getStyleClass().add("revealed-card");
                if (card.suit == 1 || card.suit == 2) face.setStyle("-fx-text-fill: #a53c35;");
                face.setOpacity(0);
                revealedCards.getChildren().add(face);
                FadeTransition reveal = new FadeTransition(Duration.millis(240), face);
                reveal.setFromValue(0); reveal.setToValue(1); reveal.play();
            }
            revealedCards.setVisible(player.showCards); revealedCards.setManaged(player.showCards);
            setOpacity(player.foldedThisHand ? 0.48 : (player.stack > 0 ? 1 : 0.72));
            if (player.isActing) {
                ScaleTransition pulse = new ScaleTransition(Duration.millis(280), this);
                pulse.setFromX(0.97); pulse.setFromY(0.97); pulse.setToX(1); pulse.setToY(1); pulse.play();
            }
        }
    }

    private record EventOption(String label, int buyIn, int fee, int startingStack) {
        @Override public String toString() { return label; }
    }

    private enum Difficulty {
        EASY("Easy  ·  loose"), MEDIUM("Medium  ·  tight-aggressive"), HARD("Hard  ·  aggressive, tight, occasional bluffs");
        private final String label;
        Difficulty(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private enum GameMode {
        NO_LIMIT_HOLDEM("No-Limit Texas Hold'em", 2, false, false, false),
        OMAHA("Omaha", 4, true, false, false),
        OMAHA_HI_LO("Omaha Hi-Lo", 4, true, true, false),
        TWO_SEVEN_DRAW("2-7 Single Draw", 5, false, false, true),
        POT_LIMIT_OMAHA("Pot-Limit Omaha", 4, true, false, false),
        FIVE_CARD_OMAHA("Five-Card Omaha", 5, true, false, false);
        private final String label;
        private final int holeCardCount;
        private final boolean omaha;
        private final boolean hiLo;
        private final boolean draw;
        GameMode(String label, int holeCardCount, boolean omaha, boolean hiLo, boolean draw) {
            this.label = label; this.holeCardCount = holeCardCount; this.omaha = omaha; this.hiLo = hiLo; this.draw = draw;
        }
        @Override public String toString() { return label; }
    }
    private enum SoundCue {
        UI(750), DEAL(680), CHECK(440), CALL(520, 660), FOLD(280, 190), RAISE(620, 820, 980), DRAW(700, 560), TIME_BANK(880, 1040), WIN(523, 659, 784);
        private final int[] frequencies;
        SoundCue(int... frequencies) { this.frequencies = frequencies; }
    }
    private enum AiAction { FOLD, CHECK, CALL, BET, RAISE }
    private record AiDecision(AiAction action, int raiseTo) { }

    private record ReplaySeat(String name, String action, List<Card> cards, String handName) {
        ReplaySeat(String name, String action, List<Card> cards) { this(name, action, cards, ""); }
    }

    private record HandReplay(int number, List<Card> heroCards, List<Card> board, int pot,
                              String result, List<ReplaySeat> seats) { }

    private record Card(int rank, int suit) {
        private static final String[] RANKS = {"", "", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
        String display() { return RANKS[rank] + SUITS[suit]; }
    }

    private static final class Player {
        private final String name;
        private final List<Card> holeCards = new ArrayList<>();
        private final List<Integer> stackHistory = new ArrayList<>();
        private int stack;
        private int streetBet;
        private boolean foldedThisHand, isActing, showCards, actedThisRound;
        private String lastAction = "";
        Player(String name, int stack) { this.name = name; this.stack = stack; stackHistory.add(stack); }
    }

    private static final class RemoteTable {
        private final int number;
        private final List<Player> players;
        private final List<Card> board = new ArrayList<>();
        private int pot, handNumber;
        private String lastAction = "Waiting for next hand";
        RemoteTable(int number, List<Player> players) { this.number = number; this.players = players; }
        String label() {
            long remaining = players.stream().filter(player -> player.stack > 0).count();
            return String.format("TABLE %02d  ·  %d PLAYERS", number, remaining);
        }
    }

    private record HandValue(List<Integer> ranks) implements Comparable<HandValue> {
        @Override public int compareTo(HandValue other) {
            for (int i = 0; i < Math.min(ranks.size(), other.ranks.size()); i++) {
                int result = Integer.compare(ranks.get(i), other.ranks.get(i)); if (result != 0) return result;
            }
            return Integer.compare(ranks.size(), other.ranks.size());
        }
    }

    private record LowValue(List<Integer> ranks) implements Comparable<LowValue> {
        @Override public int compareTo(LowValue other) {
            for (int i = 0; i < Math.min(ranks.size(), other.ranks.size()); i++) {
                int result = Integer.compare(other.ranks.get(i), ranks.get(i)); if (result != 0) return result;
            }
            return Integer.compare(other.ranks.size(), ranks.size());
        }
    }

    private record LowballValue(List<Integer> ranks) implements Comparable<LowballValue> {
        @Override public int compareTo(LowballValue other) {
            for (int i = 0; i < Math.min(ranks.size(), other.ranks.size()); i++) {
                int result = Integer.compare(ranks.get(i), other.ranks.get(i)); if (result != 0) return result;
            }
            return Integer.compare(ranks.size(), other.ranks.size());
        }
    }

    private static HandValue evaluateOmahaBest(List<Card> holeCards, List<Card> communityCards) {
        HandValue[] best = {new HandValue(List.of(-1))};
        if (holeCards.size() < 2 || communityCards.size() < 3) return best[0];
        for (int first = 0; first < holeCards.size() - 1; first++) {
            for (int second = first + 1; second < holeCards.size(); second++) {
                for (int a = 0; a < communityCards.size() - 2; a++) {
                    for (int b = a + 1; b < communityCards.size() - 1; b++) {
                        for (int c = b + 1; c < communityCards.size(); c++) {
                            HandValue value = evaluateFive(List.of(holeCards.get(first), holeCards.get(second),
                                    communityCards.get(a), communityCards.get(b), communityCards.get(c)));
                            if (value.compareTo(best[0]) > 0) best[0] = value;
                        }
                    }
                }
            }
        }
        return best[0];
    }

    private static LowValue evaluateOmahaLow(List<Card> holeCards, List<Card> communityCards) {
        LowValue best = null;
        if (holeCards.size() < 2 || communityCards.size() < 3) return null;
        for (int first = 0; first < holeCards.size() - 1; first++) {
            for (int second = first + 1; second < holeCards.size(); second++) {
                for (int a = 0; a < communityCards.size() - 2; a++) {
                    for (int b = a + 1; b < communityCards.size() - 1; b++) {
                        for (int c = b + 1; c < communityCards.size(); c++) {
                            LowValue value = evaluateEightOrBetter(List.of(holeCards.get(first), holeCards.get(second),
                                    communityCards.get(a), communityCards.get(b), communityCards.get(c)));
                            if (value != null && (best == null || value.compareTo(best) > 0)) best = value;
                        }
                    }
                }
            }
        }
        return best;
    }

    private static LowValue evaluateEightOrBetter(List<Card> cards) {
        List<Integer> ranks = new ArrayList<>();
        for (Card card : cards) {
            int rank = card.rank() == 14 ? 1 : card.rank();
            if (rank > 8 || ranks.contains(rank)) return null;
            ranks.add(rank);
        }
        ranks.sort(Comparator.reverseOrder());
        return new LowValue(ranks);
    }

    private static LowballValue evaluateTwoSeven(List<Card> cards) {
        HandValue high = evaluateFive(cards);
        List<Integer> score = new ArrayList<>();
        score.add(8 - high.ranks().get(0));
        for (int i = 1; i < high.ranks().size(); i++) score.add(14 - high.ranks().get(i));
        return new LowballValue(score);
    }

    private static HandValue evaluateBest(List<Card> cards) {
        HandValue[] best = {new HandValue(List.of(-1))}; chooseFive(cards, 0, new ArrayList<>(), best); return best[0];
    }

    private static void chooseFive(List<Card> cards, int start, List<Card> chosen, HandValue[] best) {
        if (chosen.size() == 5) {
            HandValue value = evaluateFive(chosen); if (value.compareTo(best[0]) > 0) best[0] = value; return;
        }
        for (int i = start; i <= cards.size() - (5 - chosen.size()); i++) {
            chosen.add(cards.get(i)); chooseFive(cards, i + 1, chosen, best); chosen.remove(chosen.size() - 1);
        }
    }

    private static HandValue evaluateFive(List<Card> cards) {
        int[] counts = new int[15]; for (Card card : cards) counts[card.rank]++;
        List<Integer> ranks = new ArrayList<>();
        for (int rank = 14; rank >= 2; rank--) if (counts[rank] > 0) ranks.add(rank);
        ranks.sort(Comparator.<Integer>comparingInt(rank -> counts[rank]).reversed().thenComparing(Comparator.reverseOrder()));
        boolean flush = cards.stream().allMatch(card -> card.suit == cards.get(0).suit);
        int straight = straightHigh(ranks);
        if (flush && straight > 0) return new HandValue(List.of(8, straight));
        if (counts[ranks.get(0)] == 4) return new HandValue(List.of(7, ranks.get(0), ranks.get(1)));
        if (counts[ranks.get(0)] == 3 && counts[ranks.get(1)] == 2) return new HandValue(List.of(6, ranks.get(0), ranks.get(1)));
        if (flush) return withRanks(5, ranks);
        if (straight > 0) return new HandValue(List.of(4, straight));
        if (counts[ranks.get(0)] == 3) return withRanks(3, ranks);
        if (counts[ranks.get(0)] == 2 && counts[ranks.get(1)] == 2) return withRanks(2, ranks);
        if (counts[ranks.get(0)] == 2) return withRanks(1, ranks);
        return withRanks(0, ranks);
    }

    private static int straightHigh(List<Integer> ranks) {
        List<Integer> unique = ranks.stream().distinct().sorted(Comparator.reverseOrder()).toList();
        if (unique.contains(14)) { List<Integer> aceLow = new ArrayList<>(unique); aceLow.add(1); unique = aceLow; }
        for (int i = 0; i <= unique.size() - 5; i++) {
            int high = unique.get(i); boolean sequence = true;
            for (int j = 1; j < 5; j++) if (unique.get(i + j) != high - j) { sequence = false; break; }
            if (sequence) return high;
        }
        return 0;
    }

    private static HandValue withRanks(int category, List<Integer> ranks) {
        List<Integer> value = new ArrayList<>(); value.add(category); value.addAll(ranks); return new HandValue(value);
    }

    public static void main(String[] args) { launch(args); }
}