package com.dazednconfused.catalauncher.gui;

import static com.dazednconfused.catalauncher.helper.Constants.APP_NAME;
import static com.dazednconfused.catalauncher.helper.Constants.CUSTOM_SAVE_PATH;
import static com.dazednconfused.catalauncher.helper.Constants.CUSTOM_TRASHED_SAVE_PATH;
import static com.dazednconfused.catalauncher.helper.Constants.CUSTOM_USER_DIR;

import com.dazednconfused.catalauncher.backup.SaveManager;
import com.dazednconfused.catalauncher.configuration.ConfigurationManager;
import com.dazednconfused.catalauncher.helper.FileExplorerManager;
import com.dazednconfused.catalauncher.helper.GitInfoManager;
import com.dazednconfused.catalauncher.helper.LogLevelManager;
import com.dazednconfused.catalauncher.launcher.CDDALauncherManager;
import com.dazednconfused.catalauncher.soundpack.SoundpackManager;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import io.vavr.control.Try;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

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
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainWindow {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainWindow.class);

    private static final String[] CUSTOM_SAVE_DIR_ARGS = { "--savedir", CUSTOM_SAVE_PATH };
    private static final String[] CUSTOM_USER_DIR_ARGS = { "--userdir", CUSTOM_USER_DIR };

    private JPanel mainPanel;
    private JProgressBar globalProgressBar; // global between all tabs

    // LAUNCHER TAB ---
    private JFormattedTextField cddaExecutableFTextField;
    private JButton openFinderButton;
    private JButton runButton;
    private JButton runLatestWorldButton;

    // SAVE BACKUPS TAB ---
    private JTable saveBackupTable;
    private JButton backupNowButton;
    private JButton backupDeleteButton;
    private JButton backupRestoreButton;
    private JCheckBox backupOnExitCheckBox;

    // SOUNDPACKS TAB ---
    private JTable soundpacksTable;
    private JButton installSoundpackButton;
    private JButton uninstallSoundpackButton;

    /**
     * {@link MainWindow}'s main entrypoint.
     * */
    public static void main(String[] args) {
        LOGGER.info(
                "{} - Version {} - Build {} {}",
                APP_NAME,
                GitInfoManager.getInstance().getBuildVersion(),
                GitInfoManager.getInstance().getCommitIdFull(),
                GitInfoManager.getInstance().getBuildTime()
        );

        LOGGER.debug("Initializing main window [{}]...", APP_NAME);

        LogLevelManager.changeGlobalLogLevelTo(ConfigurationManager.getInstance().isDebug() ? Level.TRACE : Level.INFO);

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

        // SETUP ALL GUI ELEMENTS ---
        this.refreshGuiElements();

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

            String[] launcherArgs = ArrayUtils.addAll(CUSTOM_SAVE_DIR_ARGS, CUSTOM_USER_DIR_ARGS);

            CDDALauncherManager.executeCddaApplication(
                    ConfigurationManager.getInstance().getCddaPath(), launcherArgs
            );

            this.refreshGuiElements();
        });

        // RUN LATEST WORLD BUTTON LISTENER ---
        runLatestWorldButton.addActionListener(e -> {
            LOGGER.trace("Run Latest World clicked");

            String[] lastWorldArgs = SaveManager.getLatestSave().map(latestSave -> new String[]{ "--world", latestSave.getName() }).orElse(new String[]{});
            String[] launcherArgs = ArrayUtils.addAll(
                    ArrayUtils.addAll(CUSTOM_SAVE_DIR_ARGS, CUSTOM_USER_DIR_ARGS), lastWorldArgs
            );

            CDDALauncherManager.executeCddaApplication(
                    ConfigurationManager.getInstance().getCddaPath(), launcherArgs
            );

            this.refreshGuiElements();
        });

        // OPEN FINDER BUTTON LISTENER ---
        this.openFinderButton.addActionListener(e -> {
            LOGGER.trace("Explore button clicked");

            this.openFinder(this.mainPanel);

            this.refreshGuiElements();
        });

        // BACKUP NOW BUTTON LISTENER ---
        this.backupNowButton.addActionListener(e -> {
            LOGGER.trace("Backup button clicked");

            // enable backup progressbar
            this.globalProgressBar.setEnabled(true);

            // disable backup buttons (don't want to do multiple operations simultaneously)
            this.disableSaveBackupButtons();

            SaveManager.backupCurrentSaves(percentageComplete -> this.globalProgressBar.setValue(percentageComplete)).ifPresent(Thread::start);
        });

        // BACKUP RESTORE BUTTON LISTENER ---
        backupRestoreButton.addActionListener(e -> {
            LOGGER.trace("Backup restore button clicked");

            File selectedBackup = (File) this.saveBackupTable.getValueAt(this.saveBackupTable.getSelectedRow(), 0);
            LOGGER.trace("Backup currently on selection: [{}]", selectedBackup);

            ConfirmDialog confirmDialog = new ConfirmDialog(
                String.format("Are you sure you want to restore the backup [%s]? Current save will be moved to trash folder [%s]", selectedBackup.getName(), CUSTOM_TRASHED_SAVE_PATH),
                ConfirmDialog.ConfirmDialogType.INFO,
                confirmed -> {
                    LOGGER.trace("Confirmation dialog result: [{}]", confirmed);

                    if (confirmed) {
                        // enable backup progressbar
                        this.globalProgressBar.setEnabled(true);

                        // disable backup buttons (don't want to do multiple operations simultaneously)
                        this.disableSaveBackupButtons();

                        SaveManager.restoreBackup(
                            selectedBackup,
                            percentageComplete -> this.globalProgressBar.setValue(percentageComplete)
                        ).ifPresent(Thread::start);
                    }

                    this.refreshGuiElements();
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        });

        // BACKUP DELETE BUTTON LISTENER ---
        this.backupDeleteButton.addActionListener(e -> {
            LOGGER.trace("Delete backup button clicked");

            File selectedBackup = (File) this.saveBackupTable.getValueAt(this.saveBackupTable.getSelectedRow(), 0);
            LOGGER.trace("Backup currently on selection: [{}]", selectedBackup);

            ConfirmDialog confirmDialog = new ConfirmDialog(
                String.format("Are you sure you want to delete the backup [%s]? This action is irreversible!", selectedBackup.getName()),
                ConfirmDialog.ConfirmDialogType.WARNING,
                confirmed -> {
                    LOGGER.trace("Confirmation dialog result: [{}]", confirmed);

                    if (confirmed) {
                        SaveManager.deleteBackup(selectedBackup);
                    }

                    this.refreshGuiElements();
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        });

        // BACKUP TABLE LISTENER ---
        this.saveBackupTable.getSelectionModel().addListSelectionListener(event -> {
            LOGGER.trace("Backup table row selected");

            if (saveBackupTable.getSelectedRow() > -1) {
                this.backupDeleteButton.setEnabled(true);
                this.backupRestoreButton.setEnabled(true);
            }
        });

        // SOUNDPACK INSTALL BUTTON LISTENER ---
        this.installSoundpackButton.addActionListener(e -> {
            LOGGER.trace("Install soundpack button clicked");

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select soundpack folder to install");
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = fileChooser.showSaveDialog(mainPanel);

            if (result == JFileChooser.APPROVE_OPTION && fileChooser.getSelectedFile() != null) {
                // setup dummy timer to give user visual feedback that his operation is in progress...
                Timer dummyTimer = new Timer(10, e1 -> {
                    if (this.globalProgressBar.getValue() <= 100) {
                        this.globalProgressBar.setValue(this.globalProgressBar.getValue() + 1);
                    }
                });

                // start timer before triggering installation
                dummyTimer.start();

                // start installation and give it a callback to stop the dummy timer
                SoundpackManager.installSoundpack(fileChooser.getSelectedFile(), p -> dummyTimer.stop());
            } else {
                LOGGER.trace("Exiting soundpack finder dialog with no selection...");
            }

            this.refreshGuiElements();
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
                    }

                    this.refreshGuiElements();
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        });

        // SOUNDPACKS TABLE LISTENER ---
        this.soundpacksTable.getSelectionModel().addListSelectionListener(event -> {
            LOGGER.trace("Soundpacks table row selected");

            if (soundpacksTable.getSelectedRow() > -1) {
                this.uninstallSoundpackButton.setEnabled(true);
            }
        });

       this.soundpacksTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { // mousedPressed event needed for macOS - https://stackoverflow.com/a/3558324
                LOGGER.trace("Soundpacks table clicked");
                super.mouseClicked(e);

                int r = soundpacksTable.rowAtPoint(e.getPoint());
                if (r >= 0 && r < soundpacksTable.getRowCount()) {
                    soundpacksTable.setRowSelectionInterval(r, r);
                } else {
                    soundpacksTable.clearSelection();
                }

                int rowindex = soundpacksTable.getSelectedRow();
                if (rowindex < 0)
                    return;
                if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
                    LOGGER.trace("Opening right-click popup for soundpacks table");

                    JPopupMenu popup = new JPopupMenu();

                    JMenuItem openInFinder = new JMenuItem("Open folder in file explorer");
                    openInFinder.addActionListener(e1 -> {
                        File selectedSoundpack = (File) soundpacksTable.getValueAt(soundpacksTable.getSelectedRow(), 1);
                        FileExplorerManager.openFileInFileExplorer(selectedSoundpack);
                    });
                    popup.add(openInFinder);

                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Disables all backup-section buttons.
     */
    private void disableSaveBackupButtons() {
        LOGGER.trace("Disabling save backup buttons...");

        this.backupNowButton.setEnabled(false);
        this.backupDeleteButton.setEnabled(false);
        this.backupRestoreButton.setEnabled(false);
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
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                 UnsupportedLookAndFeelException e) {
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

            AboutDialog aboutDialog = new AboutDialog();
            aboutDialog.packCenterAndShow(parent);
        });
        helpMenu.add(about);

        return menuBar;
    }

    /**
     * Refreshes all GUI elements according to diverse app statuses.
     */
    private void refreshGuiElements() {
        LOGGER.trace("Refreshing all GUI elements...");

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
        if (pathPointsToValidGameExecutable && saveFilesExist) {
            this.runLatestWorldButton.setEnabled(true);
        } else {
            this.runLatestWorldButton.setEnabled(false);
        }

        // DETERMINE IF BACKUP NOW BUTTON SHOULD BE ENABLED ---
        if (saveFilesExist) {
            this.backupNowButton.setEnabled(true);
        } else {
            this.backupNowButton.setEnabled(false);
        }

        // SET SAVE BACKUPS TABLE ---
        this.refreshSaveBackupsTable();

        // DETERMINE IF BACKUP RESTORE BUTTON SHOULD BE DISABLED  ---
        // DETERMINE IF BACKUP DELETE BUTTON SHOULD BE DISABLED ---
        // (ie: if last backup was just deleted)
        if (SaveManager.listAllBackups().size() == 0 || this.saveBackupTable.getSelectedRow() == -1) {
            this.backupDeleteButton.setEnabled(false);
            this.backupRestoreButton.setEnabled(false);
        }

        // SET SOUNDPACKS TABLE ---
        this.refreshSoundpacksTable();

        // DETERMINE IF SOUNDPACK DELETE BUTTON SHOULD BE DISABLED ---
        // (ie: if last backup was just deleted)
        if (SoundpackManager.listAllSoundpacks().size() == 0 || this.soundpacksTable.getSelectedRow() == -1) {
            this.uninstallSoundpackButton.setEnabled(false);
        }

    }

    /**
     * Opens a finder dialog and populates the executable field with the selected file/folder.
     */
    private void openFinder(Component parent) {
        this.cddaExecutableFTextField.setText(""); // clear your JTextArea.

        JFileChooser cddaAppChooser = new JFileChooser();
        cddaAppChooser.setDialogTitle("Select your CDDA Executable");
        cddaAppChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        cddaAppChooser.setFileFilter(new FileNameExtensionFilter("CDDA .app file", ".app"));
        int result = cddaAppChooser.showSaveDialog(parent);

        if (result == JFileChooser.APPROVE_OPTION && cddaAppChooser.getSelectedFile() != null) {
            String fileName = cddaAppChooser.getSelectedFile().getPath();
            ConfigurationManager.getInstance().setCddaPath(fileName);
            this.cddaExecutableFTextField.setText(fileName);
        } else {
            LOGGER.trace("Exiting CDDA .app finder with no selection...");
        }
    }

    /**
     * Refreshes current {@link #saveBackupTable} with latest info coming from {@link SaveManager}.
     */
    private void refreshSaveBackupsTable() {
        LOGGER.trace("Refreshing save backups table...");

        String[] columns = new String[]{"Backup", "Size", "Date"};

        List<Object[]> values = new ArrayList<>();
        SaveManager.listAllBackups().stream().sorted(Comparator.comparing(File::lastModified).reversed()).forEach(backup ->
            values.add(new Object[]{
                backup,
                backup.length() / (1024 * 1024) + " MB",
                new Date(backup.lastModified())
            })
        );

        TableModel tableModel = new DefaultTableModel(values.toArray(new Object[][]{}), columns);
        this.saveBackupTable.setModel(tableModel);
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

        TableModel tableModel = new DefaultTableModel(values.toArray(new Object[][]{}), columns);
        this.soundpacksTable.setModel(tableModel);
    }
}
