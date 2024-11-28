package com.dazednconfused.catalauncher.gui.listener;

import com.dazednconfused.catalauncher.gui.ConfirmDialog;
import com.dazednconfused.catalauncher.helper.FileExplorerManager;
import com.dazednconfused.catalauncher.soundpack.SoundpackManager;

import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import li.flor.nativejfilechooser.NativeJFileChooser;

import lombok.NonNull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class comprises all actions related to Soundpack Management and its associated GUI elements.
 * */
public class SoundpackActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(SoundpackActions.class);

    private final JPanel mainPanel;
    private final JProgressBar globalProgressBar;
    private final JTable soundpacksTable;
    private final JButton installSoundpackButton;
    private final JButton uninstallSoundpackButton;

    /**
     * Public constructor.
     * */
    public SoundpackActions(
        @NonNull JPanel mainPanel,
        @NonNull JProgressBar globalProgressBar,
        @NonNull JTable soundpacksTable,
        @NonNull JButton installSoundpackButton,
        @NonNull JButton uninstallSoundpackButton
    ) {
        this.mainPanel = mainPanel;
        this.globalProgressBar = globalProgressBar;
        this.soundpacksTable = soundpacksTable;
        this.installSoundpackButton = installSoundpackButton;
        this.uninstallSoundpackButton = uninstallSoundpackButton;
    }

    /**
     * The action to be performed on {@link #installSoundpackButton}'s click.
     * */
    public ActionListener onInstallSoundpackButtonClicked() {
        return e -> {
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
                        refreshSoundpackGui();
                    }
                };

                // start the worker thread
                worker.execute();
            } else {
                LOGGER.trace("Exiting soundpack finder dialog with no selection...");
            }
        };
    }

    /**
     * The action to be performed on {@link #uninstallSoundpackButton}'s click.
     * */
    public ActionListener onUninstallSoundpackButtonClicked() {
        return e -> {
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

                    this.refreshSoundpackGui();
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        };
    }

    /**
     * The action to be performed on {@link #soundpacksTable}'s row selection.
     * */
    public ListSelectionListener onSoundpacksTableRowSelected() {
        return event -> {
            LOGGER.trace("Soundpacks table row selected");

            if (soundpacksTable.getSelectedRow() > -1) {
                this.uninstallSoundpackButton.setEnabled(true);
            }
        };
    }

    /**
     * The action to be performed on {@link #soundpacksTable}'s click.
     * */
    public void onSoundpacksTableClicked(MouseEvent e) {
        LOGGER.trace("Soundpacks table clicked");

        int r = this.soundpacksTable.rowAtPoint(e.getPoint());
        if (r >= 0 && r < this.soundpacksTable.getRowCount()) {
            this.soundpacksTable.setRowSelectionInterval(r, r);
        } else {
            this.soundpacksTable.clearSelection();
        }

        int rowindex = this.soundpacksTable.getSelectedRow();
        if (rowindex < 0) {
            return;
        }

        if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
            LOGGER.trace("Opening right-click popup for [{}]", this.soundpacksTable.getName());
            File targetFile = ((File) this.soundpacksTable.getValueAt(this.soundpacksTable.getSelectedRow(), 1));

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
    }

    /**
     * Refreshes all GUI components corresponding to Soundpack Management.
     * */
    public void refreshSoundpackGui() {
        LOGGER.trace("Refreshing soundpack-management GUI elements...");

        // SET SOUNDPACKS TABLE ---
        this.refreshSoundpacksTable();

        // DETERMINE IF SOUNDPACK DELETE BUTTON SHOULD BE DISABLED ---
        // (ie: if last backup was just deleted)
        if (SoundpackManager.listAllSoundpacks().isEmpty() || this.soundpacksTable.getSelectedRow() == -1) {
            this.uninstallSoundpackButton.setEnabled(false);
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
}
