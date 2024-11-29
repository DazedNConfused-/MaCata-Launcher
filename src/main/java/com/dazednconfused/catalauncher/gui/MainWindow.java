package com.dazednconfused.catalauncher.gui;

import static com.dazednconfused.catalauncher.helper.Constants.APP_NAME;

import com.dazednconfused.catalauncher.configuration.ConfigurationManager;
import com.dazednconfused.catalauncher.gui.listener.ExecutableLauncherActions;
import com.dazednconfused.catalauncher.gui.listener.LauncherMenuBar;
import com.dazednconfused.catalauncher.gui.listener.ModActions;
import com.dazednconfused.catalauncher.gui.listener.SaveBackupActions;
import com.dazednconfused.catalauncher.gui.listener.SoundpackActions;
import com.dazednconfused.catalauncher.helper.GitInfoManager;
import com.dazednconfused.catalauncher.helper.LogLevelManager;
import com.dazednconfused.catalauncher.helper.sysinfo.SystemInfoManager;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;

import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainWindow {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainWindow.class);

    /**
     * The array of all {@link Runnable}s to be executed on invocation of {@link #refreshAllGuiElements()}.
     * */
    private final Runnable[] guiRefreshingRunnables;

    private JPanel mainPanel;
    private JProgressBar globalProgressBar; // global between all tabs
    private JTabbedPane tabbedPane;

    // LAUNCHER TAB ---
    private JFormattedTextField cddaExecutableFTextField;
    private JButton openExecutableFinderButton;
    private JButton runButton;
    private JButton runLatestWorldButton;
    private final ExecutableLauncherActions executableLauncherActions = new ExecutableLauncherActions(
        mainPanel,
        globalProgressBar,
        cddaExecutableFTextField,
        openExecutableFinderButton,
        runButton,
        runLatestWorldButton
    );

    // SAVE BACKUPS TAB ---
    private JTable saveBackupsTable;
    private JButton backupNowButton;
    private JButton backupDeleteButton;
    private JButton backupRestoreButton;
    private JCheckBox backupOnExitCheckBox;
    private final SaveBackupActions saveBackupActions = new SaveBackupActions(
        mainPanel,
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
    private final SoundpackActions soundpackActions = new SoundpackActions(
        mainPanel,
        globalProgressBar,
        soundpacksTable,
        installSoundpackButton,
        uninstallSoundpackButton
    );

    // MODS TAB ---
    private JTable modsTable;
    private JButton installModButton;
    private JButton uninstallModButton;
    private final ModActions modActions = new ModActions(
        mainPanel,
        globalProgressBar,
        modsTable,
        installModButton,
        uninstallModButton
    );

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

        frame.setJMenuBar(new LauncherMenuBar(frame).getMenuBar());

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
                this.setupSaveBackupGui(),
                this.setupSoundpacksGui(),
                this.setupModsGui()
        };

        // DRAW/REFRESH ALL GUI ELEMENTS ---
        this.refreshAllGuiElements();

        // CHECK FOR SOFTWARE UPDATES ---
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
        this.globalProgressBar.addChangeListener(this.executableLauncherActions.onGlobalProgressBarChangeListener());

        // RUN BUTTON LISTENER ---
        this.runButton.addActionListener(this.executableLauncherActions.onRunButtonClicked());

        // RUN LATEST WORLD BUTTON LISTENER ---
        this.runLatestWorldButton.addActionListener(this.executableLauncherActions.onRunLatestWorldClicked());

        // OPEN EXECUTABLE FINDER BUTTON LISTENER ---
        this.openExecutableFinderButton.addActionListener(this.executableLauncherActions.onOpenExecutableFinderButtonClicked());

        // GUI COMPONENT'S REFRESH ACTION ---
        return this.executableLauncherActions::refreshExecutableLauncherGui;
    }

    /**
     * Setups all GUI elements related to save backup management.
     *
     * @return The {@link Runnable} in charge or refreshing all GUI elements related to this setup on-demand.
     * */
    private Runnable setupSaveBackupGui() {

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

        // GUI COMPONENT'S REFRESH ACTION ---
        return this.saveBackupActions::refreshSaveBackupGui;
    }

    /**
     * Setups all GUI elements related to soundpack management.
     *
     * @return The {@link Runnable} in charge or refreshing all GUI elements related to this setup on-demand.
     * */
    private Runnable setupSoundpacksGui() {

        // SOUNDPACK INSTALL BUTTON LISTENER ---
        this.installSoundpackButton.addActionListener(this.soundpackActions.onInstallSoundpackButtonClicked());

        // SOUNDPACK DELETE BUTTON LISTENER ---
        this.uninstallSoundpackButton.addActionListener(this.soundpackActions.onUninstallSoundpackButtonClicked());

        // SOUNDPACKS TABLE LISTENER(S) ---
        this.soundpacksTable.getSelectionModel().addListSelectionListener(this.soundpackActions.onSoundpacksTableRowSelected());

        this.soundpacksTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { // mousedPressed event needed for macOS - https://stackoverflow.com/a/3558324
                soundpackActions.onSoundpacksTableClicked(e);
            }

            public void mouseReleased(MouseEvent e) { // mouseReleased event needed for other OSes
                soundpackActions.onSoundpacksTableClicked(e);
            }
        });

        // GUI COMPONENT'S REFRESH ACTION ---
        return this.soundpackActions::refreshSoundpackGui;
    }

    /**
     * Setups all GUI elements related to mod management.
     *
     * @return The {@link Runnable} in charge or refreshing all GUI elements related to this setup on-demand.
     * */
    private Runnable setupModsGui() {

        // MOD INSTALL BUTTON LISTENER ---
        final JPopupMenu installModButtonPopupMenu = new JPopupMenu();
        installModButtonPopupMenu.add(new JMenuItem(new AbstractAction("...from .ZIP file") {
            public void actionPerformed(ActionEvent e) {
                modActions.onInstallModButtonClickedFor(e, JFileChooser.FILES_ONLY);
            }
        }));
        installModButtonPopupMenu.add(new JMenuItem(new AbstractAction("...from directory") {
            public void actionPerformed(ActionEvent e) {
                modActions.onInstallModButtonClickedFor(e, JFileChooser.DIRECTORIES_ONLY);
            }
        }));

        this.installModButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                installModButtonPopupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        // MOD DELETE BUTTON LISTENER ---
        this.uninstallModButton.addActionListener(this.modActions.onUninstallModButtonClicked());

        // MODS TABLE LISTENER(S) ---
        this.modsTable.getSelectionModel().addListSelectionListener(this.modActions.onModsTableRowSelected());

        this.modsTable.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { // mousedPressed event needed for macOS - https://stackoverflow.com/a/3558324
                modActions.onModsTableClicked(e);
            }

            public void mouseReleased(MouseEvent e) { // mouseReleased event needed for other OSes
                modActions.onModsTableClicked(e);
            }
        });

        // GUI COMPONENT'S REFRESH ACTION ---
        return this.modActions::refreshModGui;
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
     * Refreshes all GUI elements.
     *
     * @apiNote This is an expensive operation. Use with discretion.
     * @implNote Refresh is done in the background by means of individual {@link Thread}s.
     */
    private void refreshAllGuiElements() {
        for (Runnable guiRefreshRunnable : this.guiRefreshingRunnables) {
            new Thread(guiRefreshRunnable).start();
        }
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
