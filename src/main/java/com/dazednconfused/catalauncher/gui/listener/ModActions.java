package com.dazednconfused.catalauncher.gui.listener;

import com.dazednconfused.catalauncher.gui.ConfirmDialog;
import com.dazednconfused.catalauncher.gui.ErrorDialog;
import com.dazednconfused.catalauncher.helper.FileExplorerManager;
import com.dazednconfused.catalauncher.helper.Paths;
import com.dazednconfused.catalauncher.mod.ModManager;
import com.dazednconfused.catalauncher.mod.dto.ModDTO;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

import li.flor.nativejfilechooser.NativeJFileChooser;

import lombok.NonNull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class comprises all actions related to Mod Management and its associated GUI elements.
 * */
public class ModActions {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModActions.class);

    private final JPanel mainPanel;
    private final JProgressBar globalProgressBar;
    private final JTable modsTable;
    private final JButton installModButton;
    private final JButton uninstallModButton;

    /**
     * Public constructor.
     * */
    public ModActions(
        @NonNull JPanel mainPanel,
        @NonNull JProgressBar globalProgressBar,
        @NonNull JTable modsTable,
        @NonNull JButton installModButton,
        @NonNull JButton uninstallModButton
    ) {
        this.mainPanel = mainPanel;
        this.globalProgressBar = globalProgressBar;
        this.modsTable = modsTable;
        this.installModButton = installModButton;
        this.uninstallModButton = uninstallModButton;
    }

    /**
     * The action to be performed on {@link #installModButton}'s click.
     * */
    public void onInstallModButtonClickedFor(ActionEvent e, int jFileChooserType) {

        // since JavaFX doesn't support a File Dialog for both files _AND_ directories, we have to get creative...
        //
        // (PS: sometimes I hate you Java, it's 2024 ffs. The only reason why this is not a feature already is that there supposedly
        // is no true way to achieve this cross-platform; and the unsupported platforms are Windows XP and Linux/GTK. Windows XP
        // is not even compatible with the Java version required to run JavaFX!!!)
        // https://stackoverflow.com/a/18237547
        //
        // the install button is going to open a tiny popup that will, in turn, open a dialog for either zip files OR directories
        // this is in order to support modpacks that can either be present as isolated directories, or fresh zip downloads

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
                    refreshModGui();
                }
            };

            // start the worker thread
            worker.execute();
        } else {
            LOGGER.trace("Exiting mod finder dialog with no selection...");
        }
    }

    /**
     * The action to be performed on {@link #uninstallModButton}'s click.
     * */
    public ActionListener onUninstallModButtonClicked() {
        return e -> {
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
                    }

                    this.refreshModGui();
                }
            );

            confirmDialog.packCenterAndShow(this.mainPanel);
        };
    }

    /**
     * The action to be performed on {@link #modsTable}'s row selection.
     * */
    public ListSelectionListener onModsTableRowSelected() {
        return event -> {
            LOGGER.trace("Mods table row selected");

            if (modsTable.getSelectedRow() > -1) {
                this.uninstallModButton.setEnabled(true);
            }
        };
    }

    /**
     * The action to be performed on {@link #modsTable}'s click.
     * */
    public void onModsTableClicked(MouseEvent e) {
        LOGGER.trace("Mods table clicked");

        int r = this.modsTable.rowAtPoint(e.getPoint());
        if (r >= 0 && r < this.modsTable.getRowCount()) {
            this.modsTable.setRowSelectionInterval(r, r);
        } else {
            this.modsTable.clearSelection();
        }

        int rowindex = this.modsTable.getSelectedRow();
        if (rowindex < 0) {
            return;
        }

        if (e.isPopupTrigger() && e.getComponent() instanceof JTable) {
            LOGGER.trace("Opening right-click popup for [{}]", this.modsTable.getName());
            File targetFile = ((File) this.modsTable.getValueAt(this.modsTable.getSelectedRow(), 1));

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
    }

    /**
     * Refreshes all GUI components corresponding to Mod Management.
     * */
    public void refreshModGui() {
        LOGGER.trace("Refreshing mod-management GUI elements...");

        // SET MODS TABLE ---
        this.refreshModsTable();

        // DETERMINE IF MOD DELETE BUTTON SHOULD BE DISABLED ---
        // (ie: if last mod was just deleted)
        if (ModManager.getInstance().listAllRegisteredMods().isEmpty() || this.modsTable.getSelectedRow() == -1) {
            this.uninstallModButton.setEnabled(false);
        }
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
}
