package com.dazednconfused.catalauncher.gui.listener;

import com.dazednconfused.catalauncher.backup.SaveManager;
import com.dazednconfused.catalauncher.configuration.ConfigurationManager;
import com.dazednconfused.catalauncher.helper.Paths;
import com.dazednconfused.catalauncher.launcher.CDDALauncherManager;

import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFormattedTextField;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import li.flor.nativejfilechooser.NativeJFileChooser;

import lombok.NonNull;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class comprises all actions related to Launcher Management and its associated GUI elements.
 * */
public class ExecutableLauncherActions {

    private static final String[] CUSTOM_SAVE_DIR_ARGS = { "--savedir", Paths.getCustomSavePath() + "/" };
    private static final String[] CUSTOM_USER_DIR_ARGS = { "--userdir", Paths.getCustomUserDir()  + "/" };

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutableLauncherActions.class);

    private final JPanel mainPanel;
    private final JProgressBar globalProgressBar;
    private final JFormattedTextField cddaExecutableFTextField;
    private final JButton openExecutableFinderButton;
    private final JButton runButton;
    private final JButton runLatestWorldButton;

    /**
     * Public constructor.
     * */
    public ExecutableLauncherActions(
        @NonNull JPanel mainPanel,
        @NonNull JProgressBar globalProgressBar,
        @NonNull JFormattedTextField cddaExecutableFTextField,
        @NonNull JButton openExecutableFinderButton,
        @NonNull JButton runButton,
        @NonNull JButton runLatestWorldButton
    ) {
        this.mainPanel = mainPanel;
        this.globalProgressBar = globalProgressBar;
        this.cddaExecutableFTextField = cddaExecutableFTextField;
        this.openExecutableFinderButton = openExecutableFinderButton;
        this.runButton = runButton;
        this.runLatestWorldButton = runLatestWorldButton;
    }

    /**
     * The action to be performed on {@link #globalProgressBar}'s percent change.
     * */
    public ChangeListener onGlobalProgressBarChangeListener() {
        return e -> {
            if (this.globalProgressBar.getValue() == 100) {

                // reset backup progressbar
                this.globalProgressBar.setValue(0);

                // disable until next backup
                this.globalProgressBar.setEnabled(false);
            }
        };
    }

    /**
     * The action to be performed on {@link #runButton}'s click.
     * */
    public ActionListener onRunButtonClicked() {
        return e -> {
            LOGGER.trace("Run button clicked");
            this.runButton.setEnabled(false);

            String[] launcherArgs = ArrayUtils.addAll(CUSTOM_SAVE_DIR_ARGS, CUSTOM_USER_DIR_ARGS);

            Process cddaProcess = CDDALauncherManager.executeCddaApplication(
                ConfigurationManager.getInstance().getCddaPath(), launcherArgs
            );
            CDDALauncherManager.monitorCddaProcess(cddaProcess, this::refreshExecutableLauncherGui);
        };
    }

    /**
     * The action to be performed on {@link #runLatestWorldButton}'s click.
     * */
    public ActionListener onRunLatestWorldClicked() {
        return e -> {
            LOGGER.trace("Run Latest World clicked");
            this.runLatestWorldButton.setEnabled(false);

            String[] lastWorldArgs = SaveManager.getLatestSave().map(latestSave -> new String[]{"--world", latestSave.getName()}).orElse(new String[]{});
            String[] launcherArgs = ArrayUtils.addAll(
                ArrayUtils.addAll(CUSTOM_SAVE_DIR_ARGS, CUSTOM_USER_DIR_ARGS), lastWorldArgs
            );

            Process cddaProcess = CDDALauncherManager.executeCddaApplication(
                ConfigurationManager.getInstance().getCddaPath(), launcherArgs
            );
            CDDALauncherManager.monitorCddaProcess(cddaProcess, this::refreshExecutableLauncherGui);
        };
    }

    /**
     * The action to be performed on {@link #openExecutableFinderButton}'s click.
     * */
    public ActionListener onOpenExecutableFinderButtonClicked() {
        return e -> {
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

            this.refreshExecutableLauncherGui();
        };
    }

    /**
     * Refreshes all GUI components corresponding to Launcher Management.
     * */
    public void refreshExecutableLauncherGui() {
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
    }
}
