package com.dazednconfused.catalauncher.gui.listener;

import com.dazednconfused.catalauncher.backup.SaveManager;
import com.dazednconfused.catalauncher.gui.ConfirmDialog;
import com.dazednconfused.catalauncher.gui.ErrorDialog;
import com.dazednconfused.catalauncher.gui.StringInputDialog;
import com.dazednconfused.catalauncher.helper.FileExplorerManager;
import com.dazednconfused.catalauncher.helper.Paths;

import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import lombok.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class comprises all actions related to Save Backup Management and its associated GUI elements.
 * */
public class SaveBackupActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(SaveBackupActions.class);

    private final JPanel mainPanel;
    private final JProgressBar globalProgressBar;
    private final JTable saveBackupsTable;
    private final JButton backupNowButton;
    private final JButton backupDeleteButton;
    private final JButton backupRestoreButton;
    private final JCheckBox backupOnExitCheckBox;

    /**
     * Public constructor.
     * */
    public SaveBackupActions(
        @NonNull JPanel mainPanel,
        @NonNull JProgressBar globalProgressBar,
        @NonNull JTable saveBackupsTable,
        @NonNull JButton backupNowButton,
        @NonNull JButton backupDeleteButton,
        @NonNull JButton backupRestoreButton,
        @NonNull JCheckBox backupOnExitCheckBox
    ) {
        this.mainPanel = mainPanel;
        this.saveBackupsTable = saveBackupsTable;
        this.backupNowButton = backupNowButton;
        this.backupDeleteButton = backupDeleteButton;
        this.backupRestoreButton = backupRestoreButton;
        this.backupOnExitCheckBox = backupOnExitCheckBox;
        this.globalProgressBar = globalProgressBar;
    }

    /**
     * The action to be performed on {@link #backupNowButton}'s click.
     * */
    public ActionListener onSaveBackupButtonClicked() {
        return e -> {
            LOGGER.trace("Save backup button clicked");

            // enable backup progressbar
            this.globalProgressBar.setEnabled(true);

            // disable backup buttons (don't want to do multiple operations simultaneously)
            this.disableSaveBackupButtons();

            SaveManager.backupCurrentSaves(this.globalProgressBar::setValue).ifPresent(saveBackupThread -> new Thread(() -> {
                try {
                    saveBackupThread.start();
                    saveBackupThread.join();
                } catch (InterruptedException ex) {
                    LOGGER.error("An error has occurred while waiting for the save-backup-ing thread to terminate. GUI elements may not refresh properly as a result.", ex);
                    Thread.currentThread().interrupt();
                } finally {
                    this.refreshSaveBackupGui();
                }
            }).start());

            this.refreshSaveBackupGui();
        };
    }

    /**
     * The action to be performed on {@link #backupRestoreButton}'s click.
     * */
    public ActionListener onSaveBackupRestoreButtonClicked() {
        return e -> {
            LOGGER.trace("Save backup restore button clicked");

            File selectedBackup = (File) this.saveBackupsTable.getValueAt(this.saveBackupsTable.getSelectedRow(), 1);
            LOGGER.trace("Save backup currently on selection: [{}]", selectedBackup);

            ConfirmDialog confirmDialog = new ConfirmDialog(
                String.format("Are you sure you want to restore the backup [%s]? Current save will be moved to trash folder [%s]", selectedBackup.getName(), Paths.getCustomTrashedSavePath()),
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
                            this.globalProgressBar::setValue
                        ).ifPresent(restoreBackupThread -> new Thread(() -> {
                            try {
                                restoreBackupThread.start();
                                restoreBackupThread.join();
                            } catch (InterruptedException ex) {
                                LOGGER.error("An error has occurred while waiting for the save-backup-restoring thread to terminate. GUI elements may not refresh properly as a result.", ex);
                                Thread.currentThread().interrupt();
                            } finally {
                                this.refreshSaveBackupGui();
                            }
                        }).start());
                    }
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        };
    }

    /**
     * The action to be performed on {@link #backupDeleteButton}'s click.
     * */
    public ActionListener onDeleteBackupButtonClicked() {
        return e -> {
            LOGGER.trace("Delete backup button clicked");

            File selectedBackup = (File) this.saveBackupsTable.getValueAt(this.saveBackupsTable.getSelectedRow(), 1);
            LOGGER.trace("Save backup currently on selection: [{}]", selectedBackup);

            ConfirmDialog confirmDialog = new ConfirmDialog(
                String.format("Are you sure you want to delete the backup [%s]? This action is irreversible!", selectedBackup.getName()),
                ConfirmDialog.ConfirmDialogType.WARNING,
                confirmed -> {
                    LOGGER.trace("Confirmation dialog result: [{}]", confirmed);

                    if (confirmed) {
                        SaveManager.deleteBackup(selectedBackup);
                    }

                    this.refreshSaveBackupGui();
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        };
    }

    /**
     * The action to be performed on {@link #saveBackupsTable}'s row selection.
     * */
    public ListSelectionListener onSaveBackupsTableRowSelected() {
        return event -> {
            LOGGER.trace("Save backups table row selected");

            if (saveBackupsTable.getSelectedRow() > -1) {
                this.backupDeleteButton.setEnabled(true);
                this.backupRestoreButton.setEnabled(true);
            }
        };
    }

    /**
     * The action to be performed on {@link #saveBackupsTable}'s click.
     * */
    public void onSaveBackupsTableClicked(MouseEvent e) {
        LOGGER.trace("Save backups table clicked");

        int r = this.saveBackupsTable.rowAtPoint(e.getPoint());
        if (r >= 0 && r < this.saveBackupsTable.getRowCount()) {
            this.saveBackupsTable.setRowSelectionInterval(r, r);
        } else {
            this.saveBackupsTable.clearSelection();
        }

        int rowindex = this.saveBackupsTable.getSelectedRow();
        if (rowindex < 0) {
            return;
        }

        if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
            LOGGER.trace("Opening right-click popup for [{}]", this.saveBackupsTable.getName());
            File targetFile = ((File) this.saveBackupsTable.getValueAt(this.saveBackupsTable.getSelectedRow(), 1));
            LOGGER.trace("File under selection: [{}]", targetFile);

            JPopupMenu popup = new JPopupMenu();

            JMenuItem openInFinder = new JMenuItem("Open in file explorer");
            openInFinder.addActionListener(e1 -> FileExplorerManager.openFileInFileExplorer(targetFile, true));
            popup.add(openInFinder);

            JMenuItem renameTo = new JMenuItem("Rename to...");
            renameTo.addActionListener(e1 -> {
                LOGGER.trace("Rename backup menu clicked");

                StringInputDialog confirmDialog = new StringInputDialog(
                    String.format("Rename backup [%s] to...", targetFile.getName()),
                    newNameOptional -> {
                        LOGGER.trace("User input dialog result: [{}]", newNameOptional);

                        newNameOptional.ifPresent(newName ->
                            SaveManager.renameBackup(targetFile, newName).toEither().peekLeft(error ->
                                ErrorDialog.showErrorDialog("Could not rename save backup!", error.getError()).packCenterAndShow(this.mainPanel)
                            )
                        );

                        this.refreshSaveBackupGui();
                    }
                );

                confirmDialog.packCenterAndShow(this.mainPanel);
            });
            popup.add(renameTo);

            JMenuItem deleteBackup = new JMenuItem("Delete...");
            deleteBackup.addActionListener(e1 -> this.backupDeleteButton.doClick());
            popup.add(deleteBackup);

            JMenuItem restoreBackup = new JMenuItem("Restore...");
            restoreBackup.addActionListener(e1 -> this.backupRestoreButton.doClick());
            popup.add(restoreBackup);

            popup.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /**
     * Refreshes all GUI components corresponding to Save Backup Management.
     * */
    public void refreshSaveBackupGui() {
        LOGGER.trace("Refreshing save-backup-management GUI elements...");

        boolean saveFilesExist = SaveManager.saveFilesExist();

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
        if (SaveManager.listAllBackups().isEmpty() || this.saveBackupsTable.getSelectedRow() == -1) {
            this.backupDeleteButton.setEnabled(false);
            this.backupRestoreButton.setEnabled(false);
        }
    }

    /**
     * Refreshes current {@link #saveBackupsTable} with latest info coming from {@link SaveManager}.
     */
    private void refreshSaveBackupsTable() {
        LOGGER.trace("Refreshing save backups table...");

        String[] columns = new String[]{"Name", "Path", "Size", "Date"};

        List<Object[]> values = new ArrayList<>();
        SaveManager.listAllBackups().stream().sorted(Comparator.comparing(File::lastModified).reversed()).forEach(backup ->
            values.add(new Object[]{
                backup.getName(),
                backup,
                backup.length() / (1024 * 1024) + " MB",
                new Date(backup.lastModified())
            })
        );

        TableModel tableModel = new DefaultTableModel(values.toArray(new Object[][]{}), columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        this.saveBackupsTable.setModel(tableModel);
    }

    /**
     * Disables all Save Backup Management buttons.
     */
    private void disableSaveBackupButtons() {
        LOGGER.trace("Disabling save backup buttons...");

        this.backupNowButton.setEnabled(false);
        this.backupDeleteButton.setEnabled(false);
        this.backupRestoreButton.setEnabled(false);
    }

}
