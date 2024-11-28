package com.dazednconfused.catalauncher.gui;

import static com.dazednconfused.catalauncher.helper.Constants.APP_NAME;

import com.dazednconfused.catalauncher.backup.SaveManager;
import com.dazednconfused.catalauncher.configuration.ConfigurationManager;
import com.dazednconfused.catalauncher.gui.action.SaveBackupActions;
import com.dazednconfused.catalauncher.helper.FileExplorerManager;
import com.dazednconfused.catalauncher.helper.GitInfoManager;
import com.dazednconfused.catalauncher.helper.LogLevelManager;
import com.dazednconfused.catalauncher.helper.Paths;
import com.dazednconfused.catalauncher.helper.sysinfo.SystemInfoManager;
import com.dazednconfused.catalauncher.launcher.CDDALauncherManager;
import com.dazednconfused.catalauncher.mod.ModManager;
import com.dazednconfused.catalauncher.mod.dto.ModDTO;
import com.dazednconfused.catalauncher.soundpack.SoundpackManager;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import io.vavr.control.Try;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import li.flor.nativejfilechooser.NativeJFileChooser;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainWindow {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainWindow.class);

    private static final String[] CUSTOM_SAVE_DIR_ARGS = { "--savedir", Paths.getCustomSavePath() + "/" };
    private static final String[] CUSTOM_USER_DIR_ARGS = { "--userdir", Paths.getCustomUserDir()  + "/" };

    /**
     * The array of all {@link Runnable}s to be executed on invocation of {@link #refreshGuiElements()}.
     * */
    private final Runnable[] guiRefreshingRunnables;

    private JPanel mainPanel;
    private JProgressBar globalProgressBar; // global between all tabs
    private JTabbedPane tabbedPane;

    // LAUNCHER TAB ---
    private JFormattedTextField cddaExecutableFTextField;
    private JButton openFinderButton;
    private JButton runButton;
    private JButton runLatestWorldButton;

    // SAVE BACKUPS TAB ---
    private JTable saveBackupsTable;
    private JButton backupNowButton;
    private JButton backupDeleteButton;
    private JButton backupRestoreButton;
    private JCheckBox backupOnExitCheckBox;
    private final SaveBackupActions saveBackupActions = new SaveBackupActions(
        mainPanel,
        this::refreshGuiElements,
        globalProgressBar,
        saveBackupsTable,
        backupNowButton,
        backupDeleteButton,
        backupRestoreButton,
        backupOnExitCheckBox
    );

    // SOUNDPACKS TAB ---
    private JTable soundpacksTable;
    private JButton installSoundpackButton;
    private JButton uninstallSoundpackButton;

    // MODS TAB ---
    private JTable modsTable;
    private JButton installModButton;
    private JButton uninstallModButton;

    /**
     * {@link MainWindow}'s main entrypoint.
     * */
    public static void main(String[] args) {
        LogLevelManager.changeGlobalLogLevelTo(ConfigurationManager.getInstance().isDebug() ? Level.TRACE : Level.INFO);

        SystemInfoManager.logSystemInformation(Level.DEBUG);

        LOGGER.info(
                "{} - Version {} - Build {} {}",
                APP_NAME,
                GitInfoManager.getInstance().getBuildVersion(),
                GitInfoManager.getInstance().getCommitIdFull(),
                GitInfoManager.getInstance().getBuildTime()
        );

        LOGGER.debug("Initializing main window [{}]...", APP_NAME);

        initializeLookAndFeel();

        JFrame frame = new JFrame(APP_NAME);

        frame.setJMenuBar(buildMenuBarFor(frame));

        frame.setContentPane(new MainWindow().mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        frame.setLocationRelativeTo(null); // center window
    }

    /**
     * Constructor.
     * */
    public MainWindow() {

        // INITIALIZE ALL GUI ELEMENTS ---
        this.guiRefreshingRunnables = new Runnable[] {
                this.setupTabbedPane(),
                this.setupMainExecutableGui(),
                this.setupSaveBackupsGui(),
                this.setupSoundpacksGui(),
                this.setupModsGui()
        };

        this.refreshGuiElements();

        new Thread(this::checkForUpdates).start(); // check for updates on a background thread, to not slow down application's startup
    }

    /**
     * Setups all GUI elements related to the tabbed pane management.
     * */
    private Runnable setupTabbedPane() {

        // TABBED PANE LISTENER ---
        this.tabbedPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                LOGGER.trace("Tabbed pane - key pressed [{}]", e.getKeyCode());
                super.keyTyped(e);

                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    LOGGER.trace("Down arrow pressed. Shifting focus to underlying panel, if possible...");

                    saveBackupsTable.requestFocusInWindow();
                    soundpacksTable.requestFocusInWindow();
                    modsTable.requestFocusInWindow();
                }
            }
        });

        return () -> { }; // no GUI-refreshing action necessary for tabbed pane
    }

    /**
     * Setups all GUI elements related to the main executable management.
     *
     * @return The {@link Runnable} in charge or refreshing all GUI elements related to this setup on-demand.
     * */
    private Runnable setupMainExecutableGui() {

        // GLOBAL PROGRESS BAR LISTENER ---
        this.globalProgressBar.addChangeListener(e -> {
            if (this.globalProgressBar.getValue() == 100) {
                // refresh gui
                this.refreshGuiElements();

                // reset backup progressbar
                this.globalProgressBar.setValue(0);

                // disable until next backup
                this.globalProgressBar.setEnabled(false);
            }
        });

        // RUN BUTTON LISTENER ---
        this.runButton.addActionListener(e -> {
            LOGGER.trace("Run button clicked");
            this.runButton.setEnabled(false);

            String[] launcherArgs = ArrayUtils.addAll(CUSTOM_SAVE_DIR_ARGS, CUSTOM_USER_DIR_ARGS);

            Process cddaProcess = CDDALauncherManager.executeCddaApplication(
                    ConfigurationManager.getInstance().getCddaPath(), launcherArgs
            );
            CDDALauncherManager.monitorCddaProcess(cddaProcess, this::refreshGuiElements);
        });

        // RUN LATEST WORLD BUTTON LISTENER ---
        this.runLatestWorldButton.addActionListener(e -> {
            LOGGER.trace("Run Latest World clicked");
            this.runLatestWorldButton.setEnabled(false);

            String[] lastWorldArgs = SaveManager.getLatestSave().map(latestSave -> new String[]{ "--world", latestSave.getName() }).orElse(new String[]{});
            String[] launcherArgs = ArrayUtils.addAll(
                    ArrayUtils.addAll(CUSTOM_SAVE_DIR_ARGS, CUSTOM_USER_DIR_ARGS), lastWorldArgs
            );

            Process cddaProcess = CDDALauncherManager.executeCddaApplication(
                    ConfigurationManager.getInstance().getCddaPath(), launcherArgs
            );
            CDDALauncherManager.monitorCddaProcess(cddaProcess, this::refreshGuiElements);
        });

        // OPEN FINDER BUTTON LISTENER ---
        this.openFinderButton.addActionListener(e -> {
            LOGGER.trace("Find executable button clicked");

            this.cddaExecutableFTextField.setText(""); // clear your JTextArea.

            JFileChooser cddaAppChooser = new NativeJFileChooser();
            cddaAppChooser.setDialogTitle("Select your CDDA Executable");
            cddaAppChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            cddaAppChooser.setFileFilter(new FileNameExtensionFilter("CDDA .app file", ".app"));
            int result = cddaAppChooser.showOpenDialog(this.mainPanel);

            if (result == JFileChooser.APPROVE_OPTION && cddaAppChooser.getSelectedFile() != null) {
                String fileName = cddaAppChooser.getSelectedFile().getPath();
                ConfigurationManager.getInstance().setCddaPath(fileName);
                this.cddaExecutableFTextField.setText(fileName);
            } else {
                LOGGER.trace("Exiting CDDA .app finder with no selection...");
            }

            this.refreshGuiElements();
        });

        return () -> {
            LOGGER.trace("Refreshing executable-management GUI elements...");

            String cddaPath = ConfigurationManager.getInstance().getCddaPath();

            boolean saveFilesExist = SaveManager.saveFilesExist();
            boolean pathPointsToValidGameExecutable = cddaPath != null && !cddaPath.isBlank();

            // SET EXECUTABLE TEXT FIELD WITH CDDA PATH FROM CONFIG ---
            // DETERMINE IF RUN BUTTON SHOULD BE ENABLED ---
            if (pathPointsToValidGameExecutable) {
                this.cddaExecutableFTextField.setText(cddaPath);
                this.runButton.setEnabled(true);
            } else {
                this.cddaExecutableFTextField.setText(null);
                this.runButton.setEnabled(false);
            }

            // DETERMINE IF RUN LATEST WORLD BUTTON SHOULD BE ENABLED ---
            if (pathPointsToValidGameExecutable && saveFilesExist && SaveManager.getLatestSave().isPresent()) {
                this.runLatestWorldButton.setEnabled(true);
            } else {
                this.runLatestWorldButton.setEnabled(false);
            }
        };
    }

    /**
     * Setups all GUI elements related to save backup management.
     *
     * @return The {@link Runnable} in charge or refreshing all GUI elements related to this setup on-demand.
     * */
    private Runnable setupSaveBackupsGui() {

        // BACKUP NOW BUTTON LISTENER ---
        this.backupNowButton.addActionListener(this.saveBackupActions.onSaveBackupButtonClicked());

        // BACKUP RESTORE BUTTON LISTENER ---
        this.backupRestoreButton.setMnemonic(KeyEvent.VK_R);
        this.backupRestoreButton.addActionListener(this.saveBackupActions.onSaveBackupRestoreButtonClicked());

        // BACKUP DELETE BUTTON LISTENER ---
        this.backupDeleteButton.setMnemonic(KeyEvent.VK_D);
        this.backupDeleteButton.addActionListener(this.saveBackupActions.onDeleteBackupButtonClicked());

        // BACKUP TABLE LISTENER(S) ---
        this.saveBackupsTable.getSelectionModel().addListSelectionListener(this.saveBackupActions.onSaveBackupsTableRowSelected());

        this.saveBackupsTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { // mousedPressed event needed for macOS - https://stackoverflow.com/a/3558324
                saveBackupActions.onSaveBackupsTableClicked(e);
            }

            public void mouseReleased(MouseEvent e) { // mouseReleased event needed for other OSes
                saveBackupActions.onSaveBackupsTableClicked(e);
            }
        });

        return this.saveBackupActions::refreshSaveBackupTab;
    }

    /**
     * Setups all GUI elements related to soundpack management.
     *
     * @return The {@link Runnable} in charge or refreshing all GUI elements related to this setup on-demand.
     * */
    private Runnable setupSoundpacksGui() {

        // SOUNDPACK INSTALL BUTTON LISTENER ---
        this.installSoundpackButton.addActionListener(e -> {
            LOGGER.trace("Install soundpack button clicked");

            JFileChooser fileChooser = new NativeJFileChooser();
            fileChooser.setDialogTitle("Select soundpack folder to install");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showOpenDialog(mainPanel);

            if (result == JFileChooser.APPROVE_OPTION && fileChooser.getSelectedFile() != null) {

                // enable global progressbar
                this.globalProgressBar.setEnabled(true);

                // setup dummy timer to give user visual feedback that his operation is in progress...
                Timer dummyTimer = new Timer(10, e1 -> {
                    if (this.globalProgressBar.getValue() < 99) { // it's important to keep this from hitting 100% while it is in its dummy-loop...
                        this.globalProgressBar.setValue(this.globalProgressBar.getValue() + 1);
                    }
                });

                // start timer before triggering installation
                dummyTimer.start();

                // execute the installation in a background thread, outside the Event Dispatch Thread (EDT)
                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() {
                        SoundpackManager.installSoundpack(fileChooser.getSelectedFile(), p -> dummyTimer.stop());
                        return null;
                    }

                    @Override
                    protected void done() {
                        dummyTimer.stop(); // ensure the timer is stopped when the task is complete
                        globalProgressBar.setValue(100); // this will refresh the GUI upon hitting 100%
                    }
                };

                // start the worker thread
                worker.execute();
            } else {
                LOGGER.trace("Exiting soundpack finder dialog with no selection...");
            }
        });

        // SOUNDPACK DELETE BUTTON LISTENER ---
        this.uninstallSoundpackButton.addActionListener(e -> {
            LOGGER.trace("Uninstall soundpack button clicked");

            File selectedSoundpack = (File) this.soundpacksTable.getValueAt(this.soundpacksTable.getSelectedRow(), 1);
            LOGGER.trace("Soundpack currently on selection: [{}]", selectedSoundpack);

            ConfirmDialog confirmDialog = new ConfirmDialog(
                String.format("Are you sure you want to delete the soundpack [%s]? This action is irreversible!", selectedSoundpack.getName()),
                ConfirmDialog.ConfirmDialogType.WARNING,
                confirmed -> {
                    LOGGER.trace("Confirmation dialog result: [{}]", confirmed);

                    if (confirmed) {
                        SoundpackManager.deleteSoundpack(selectedSoundpack);
                        this.refreshGuiElements();
                    }
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        });

        // SOUNDPACKS TABLE LISTENER(S) ---
        this.soundpacksTable.getSelectionModel().addListSelectionListener(event -> {
            LOGGER.trace("Soundpacks table row selected");

            if (soundpacksTable.getSelectedRow() > -1) {
                this.uninstallSoundpackButton.setEnabled(true);
            }
        });

        BiConsumer<MouseEvent, JTable> onSoundpacksTableRightClickEvent = (e, table) -> {
            LOGGER.trace("Soundpacks table clicked");

            int r = table.rowAtPoint(e.getPoint());
            if (r >= 0 && r < table.getRowCount()) {
                table.setRowSelectionInterval(r, r);
            } else {
                table.clearSelection();
            }

            int rowindex = table.getSelectedRow();
            if (rowindex < 0) {
                return;
            }

            if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
                LOGGER.trace("Opening right-click popup for [{}]", table.getName());
                File targetFile = ((File) table.getValueAt(table.getSelectedRow(), 1));

                JPopupMenu popup = new JPopupMenu();

                JMenuItem openInFinder = new JMenuItem("Open folder in file explorer");
                openInFinder.addActionListener(e1 -> {
                    FileExplorerManager.openFileInFileExplorer(targetFile, false);
                });
                popup.add(openInFinder);

                JMenuItem uninstall = new JMenuItem("Uninstall...");
                uninstall.addActionListener(e1 -> uninstallSoundpackButton.doClick());
                popup.add(uninstall);

                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        };

        this.soundpacksTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { // mousedPressed event needed for macOS - https://stackoverflow.com/a/3558324
                onSoundpacksTableRightClickEvent.accept(e, (JTable) e.getComponent());
            }

            public void mouseReleased(MouseEvent e) { // mouseReleased event needed for other OSes
                onSoundpacksTableRightClickEvent.accept(e, (JTable) e.getComponent());
            }
        });

        return () -> {
            LOGGER.trace("Refreshing soundpack-management GUI elements...");

            // SET SOUNDPACKS TABLE ---
            this.refreshSoundpacksTable();

            // DETERMINE IF SOUNDPACK DELETE BUTTON SHOULD BE DISABLED ---
            // (ie: if last backup was just deleted)
            if (SoundpackManager.listAllSoundpacks().isEmpty() || this.soundpacksTable.getSelectedRow() == -1) {
                this.uninstallSoundpackButton.setEnabled(false);
            }
        };
    }

    /**
     * Setups all GUI elements related to mod management.
     *
     * @return The {@link Runnable} in charge or refreshing all GUI elements related to this setup on-demand.
     * */
    private Runnable setupModsGui() {

        // MOD INSTALL BUTTON LISTENER ---

        // since JavaFX doesn't support a File Dialog for both files _AND_ directories, we have to get creative...
        //
        // (PS: sometimes I hate you Java, it's 2024 ffs. The only reason why this is not a feature already is that there supposedly
        // is no true way to achieve this cross-platform; and the unsupported platforms are Windows XP and Linux/GTK. Windows XP
        // is not even compatible with the Java version required to run JavaFX!!!)
        // https://stackoverflow.com/a/18237547
        //
        // the install button is going to open a tiny popup that will, in turn, open a dialog for either zip files OR directories
        // this is in order to support modpacks that can either be present as isolated directories, or fresh zip downloads

        ObjIntConsumer<ActionEvent> installModButtonClickedFor = (e, jFileChooserType) -> {
            LOGGER.trace("Install mod button clicked");

            JFileChooser fileChooser = new NativeJFileChooser();
            fileChooser.setDialogTitle("Select mod folder/zip to install");
            fileChooser.setFileSelectionMode(jFileChooserType);
            fileChooser.setFileFilter(new FileFilter() {
                // set a custom file filter to allow only ZIP files or directories
                @Override
                public boolean accept(File file) {
                    // accept directories
                    if (file.isDirectory()) {
                        return true;
                    }
                    // accept files that end with .zip
                    String fileName = file.getName().toLowerCase();
                    return fileName.endsWith(".zip");
                }

                @Override
                public String getDescription() {
                    return "ZIP files and directories";
                }
            });

            int result = fileChooser.showOpenDialog(mainPanel);

            if (result == JFileChooser.APPROVE_OPTION && fileChooser.getSelectedFile() != null) {

                // enable global progressbar
                this.globalProgressBar.setEnabled(true);

                // setup dummy timer to give user visual feedback that his operation is in progress...
                Timer dummyTimer = new Timer(10, e1 -> {
                    if (this.globalProgressBar.getValue() < 99) { // it's important to keep this from hitting 100% while it is in its dummy-loop...
                        this.globalProgressBar.setValue(this.globalProgressBar.getValue() + 1);
                    }
                });

                // start timer before triggering installation
                dummyTimer.start();

                // execute the installation in a background thread, outside the Event Dispatch Thread (EDT)
                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() {
                        ModManager.getInstance().installMod(fileChooser.getSelectedFile(), p -> dummyTimer.stop()).toEither().fold(
                            failure -> {
                                LOGGER.error("There was a problem while installing mod [{}]", fileChooser.getSelectedFile(), failure.getError());

                                // show error dialog on the EDT
                                SwingUtilities.invokeLater(() -> {
                                    dummyTimer.stop(); // stop dummyTimer on error
                                    ErrorDialog.showErrorDialog(
                                        String.format("There was a problem while installing mod [%s]", fileChooser.getSelectedFile().getName()),
                                        failure.getError()
                                    ).packCenterAndShow(mainPanel);
                                });
                                return null;
                            },
                            success -> {
                                LOGGER.info("Mod [{}] has been successfully installed!", fileChooser.getSelectedFile());
                                return null;
                            }
                        );
                        return null;
                    }

                    @Override
                    protected void done() {
                        dummyTimer.stop(); // ensure the timer is stopped when the task is complete
                        globalProgressBar.setValue(100); // this will refresh the GUI upon hitting 100%
                    }
                };

                // start the worker thread
                worker.execute();
            } else {
                LOGGER.trace("Exiting mod finder dialog with no selection...");
            }
        };

        final JPopupMenu installModButtonPopupMenu = new JPopupMenu();
        installModButtonPopupMenu.add(new JMenuItem(new AbstractAction("...from .ZIP file") {
            public void actionPerformed(ActionEvent e) {
                installModButtonClickedFor.accept(e, JFileChooser.FILES_ONLY);
            }
        }));
        installModButtonPopupMenu.add(new JMenuItem(new AbstractAction("...from directory") {
            public void actionPerformed(ActionEvent e) {
                installModButtonClickedFor.accept(e, JFileChooser.DIRECTORIES_ONLY);
            }
        }));

        this.installModButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                installModButtonPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        // MOD DELETE BUTTON LISTENER ---
        this.uninstallModButton.addActionListener(e -> {
            LOGGER.trace("Uninstall mod button clicked");

            File selectedMod = (File) this.modsTable.getValueAt(this.modsTable.getSelectedRow(), 1);
            LOGGER.trace("Mod currently on selection: [{}]", selectedMod);

            ConfirmDialog confirmDialog = new ConfirmDialog(
                String.format(
                    "Are you sure you want to uninstall the mod [%s]? It will be moved to trash folder [%s]",
                    selectedMod.getName(),
                    Paths.getCustomTrashedModsPath()
                ),
                ConfirmDialog.ConfirmDialogType.WARNING,
                confirmed -> {
                    LOGGER.trace("Confirmation dialog result: [{}]", confirmed);

                    if (confirmed) {
                        ModManager.getInstance().uninstallMod(
                            ModManager.getInstance().getModFor(selectedMod).orElseThrow(),
                            ModManager.DO_NOTHING_ACTION
                        );

                        this.refreshGuiElements();
                    }
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        });

        // MODS TABLE LISTENER(S) ---
        this.modsTable.getSelectionModel().addListSelectionListener(event -> {
            LOGGER.trace("Mods table row selected");

            if (modsTable.getSelectedRow() > -1) {
                this.uninstallModButton.setEnabled(true);
            }
        });

        BiConsumer<MouseEvent, JTable> onModsTableRightClickEvent = (e, table) -> {
            LOGGER.trace("Mods table clicked");

            int r = table.rowAtPoint(e.getPoint());
            if (r >= 0 && r < table.getRowCount()) {
                table.setRowSelectionInterval(r, r);
            } else {
                table.clearSelection();
            }

            int rowindex = table.getSelectedRow();
            if (rowindex < 0) {
                return;
            }

            if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
                LOGGER.trace("Opening right-click popup for [{}]", table.getName());
                File targetFile = ((File) table.getValueAt(table.getSelectedRow(), 1));

                JPopupMenu popup = new JPopupMenu();

                JMenuItem openInFinder = new JMenuItem("Open folder in file explorer");
                openInFinder.addActionListener(e1 -> {
                    FileExplorerManager.openFileInFileExplorer(targetFile, false);
                });
                popup.add(openInFinder);

                JMenuItem uninstall = new JMenuItem("Uninstall...");
                uninstall.addActionListener(e1 -> uninstallModButton.doClick());
                popup.add(uninstall);

                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        };

        this.modsTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { // mousedPressed event needed for macOS - https://stackoverflow.com/a/3558324
                onModsTableRightClickEvent.accept(e, (JTable) e.getComponent());
            }

            public void mouseReleased(MouseEvent e) { // mouseReleased event needed for other OSes
                onModsTableRightClickEvent.accept(e, (JTable) e.getComponent());
            }
        });

        return () -> {
            LOGGER.trace("Refreshing mod-management GUI elements...");

            // SET MODS TABLE ---
            this.refreshModsTable();

            // DETERMINE IF MOD DELETE BUTTON SHOULD BE DISABLED ---
            // (ie: if last mod was just deleted)
            if (ModManager.getInstance().listAllRegisteredMods().isEmpty() || this.modsTable.getSelectedRow() == -1) {
                this.uninstallModButton.setEnabled(false);
            }
        };
    }

    /**
     * Initializes {@link FlatLaf}'s Look & Feel (and other MacOS-specific goodies).
     */
    private static void initializeLookAndFeel() {
        LOGGER.trace("Initializing Look & Feel...");

        System.setProperty("apple.awt.application.name", APP_NAME);
        System.setProperty("apple.awt.application.appearance", "system");

        FlatDarkLaf.setup();
        try {
            UIManager.setLookAndFeel("com.formdev.flatlaf.FlatDarculaLaf"); // cascade look & feel to all children widgets from now on
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Builds the main {@link Component}'s {@link JMenuBar}.
     */
    private static JMenuBar buildMenuBarFor(Component parent) {
        LOGGER.trace("Building menu bar...");

        // main menu bar ---
        JMenuBar menuBar = new JMenuBar();

        // help menu ---
        JMenu helpMenu = new JMenu("Help");
        helpMenu.setMnemonic(KeyEvent.VK_H);
        menuBar.add(helpMenu);

        // developer tools submenu --
        JMenu developerTools = new JMenu("Developer Tools");
        helpMenu.add(developerTools);

        // show console log button -
        JMenuItem showConsoleLog = new JMenuItem("Show console log");
        showConsoleLog.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK));
        showConsoleLog.addActionListener(e -> {
            LOGGER.trace("Show console button clicked");
            Try.of(ConsoleLogReader::new)
                    .andThen(consoleLogReader -> consoleLogReader.packCenterAndShow(parent))
                    .onFailure(throwable -> LOGGER.error("There was an error while ConsoleLogReader window: [{}]", throwable.getMessage()));
        });
        developerTools.add(showConsoleLog);

        // debug mode checkbox -
        JCheckBoxMenuItem debugMode = new JCheckBoxMenuItem("Debug mode");
        debugMode.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK));
        debugMode.setState(ConfigurationManager.getInstance().isDebug());
        debugMode.addActionListener(e -> {
            LOGGER.trace("Debug mode checkbox clicked. Enabled: [{}]", debugMode.getState());
            ConfigurationManager.getInstance().setDebug(debugMode.getState());
            LogLevelManager.changeGlobalLogLevelTo(debugMode.getState() ? Level.TRACE : Level.INFO);
        });
        developerTools.add(debugMode);

        // separator --
        helpMenu.addSeparator();

        // about button --
        JMenuItem about = new JMenuItem("About");
        about.setMnemonic(KeyEvent.VK_T);
        about.addActionListener(e -> {
            LOGGER.trace("About button clicked");

            VersionManagerWindow versionManagerWindow = new VersionManagerWindow();
            versionManagerWindow.packCenterAndShow(parent);
        });
        helpMenu.add(about);

        return menuBar;
    }

    /**
     * Refreshes all GUI elements according to diverse app statuses.
     *
     * @implNote Refresh is done in the background by means of individual {@link Thread}s.
     */
    private void refreshGuiElements() {
        for (Runnable guiRefreshRunnable : this.guiRefreshingRunnables) {
            new Thread(guiRefreshRunnable).start();
        }
    }

    /**
     * Refreshes current {@link #soundpacksTable} with latest info coming from {@link SoundpackManager}.
     */
    private void refreshSoundpacksTable() {
        LOGGER.trace("Refreshing soundpacks table...");

        String[] columns = new String[]{"Name", "Path", "Size", "Date"};

        List<Object[]> values = new ArrayList<>();
        SoundpackManager.listAllSoundpacks().stream().sorted(Comparator.comparing(File::lastModified).reversed()).forEach(soundpack ->
                values.add(new Object[]{
                    soundpack.getName(),
                    soundpack,
                    FileUtils.sizeOfDirectory(soundpack) / (1024 * 1024) + " MB",
                    new Date(soundpack.lastModified())
                })
        );

        TableModel tableModel = new DefaultTableModel(values.toArray(new Object[][]{}), columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.soundpacksTable.setModel(tableModel);
    }

    /**
     * Refreshes current {@link #modsTable} with latest info coming from {@link ModManager}.
     */
    private void refreshModsTable() {
        LOGGER.trace("Refreshing mods table...");

        String[] columns = new String[]{"Name", "Path", "Size", "Install date", "Last updated"};

        List<Object[]> values = new ArrayList<>();
        ModManager.getInstance().listAllRegisteredMods().stream().sorted(Comparator.comparing(ModDTO::getId)).forEach(mod -> {
            Path modPath = ModManager.getInstance().getPathFor(mod);
            File modFile = new File(modPath.toString());
            values.add(new Object[]{
                mod.getName(),
                modFile,
                FileUtils.sizeOfDirectory(modFile) / (1024) + " KB",
                mod.getCreatedDate(),
                mod.getUpdatedDate()
            });
        });

        TableModel tableModel = new DefaultTableModel(values.toArray(new Object[][]{}), columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.modsTable.setModel(tableModel);
    }

    /**
     * Checks for new software releases if the {@link ConfigurationManager} dictates said process should be carried out.
     * */
    private void checkForUpdates() {
        if (ConfigurationManager.getInstance().isShouldLookForUpdates()) {
            VersionManagerWindow.checkForUpdates(this.mainPanel, false);
        }
    }
}
