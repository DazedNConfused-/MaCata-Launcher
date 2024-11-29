package com.dazednconfused.catalauncher.gui;

import com.dazednconfused.catalauncher.configuration.ConfigurationManager;

import com.dazednconfused.catalauncher.helper.LogLevelManager;

import io.vavr.control.Try;

import lombok.Getter;

import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

import java.awt.Component;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * This class comprises all actions related to the Launcher's Menu Bar and its associated GUI elements.
 * */
public class LauncherMenuBar {

    private static final Logger LOGGER = LoggerFactory.getLogger(LauncherMenuBar.class);

    @Getter
    private final JMenuBar menuBar;

    private final JMenu helpMenu;
    private final JMenu developerToolsMenu;

    private final JMenuItem showConsoleLogMenuItem;
    private final JCheckBoxMenuItem debugModeCheckBoxMenuItem;
    private final JMenuItem aboutMenuItem;

    /**
     * Public constructor.
     * */
    public LauncherMenuBar(Component parent) {

        LOGGER.trace("Building menu bar...");

        // main menu bar ---
        this.menuBar = new JMenuBar();

        // help menu ---
        this.helpMenu = new JMenu("Help");
        this.helpMenu.setMnemonic(KeyEvent.VK_H);
        this.menuBar.add(helpMenu);

        // developer tools submenu --
        this.developerToolsMenu = new JMenu("Developer Tools");
        this.helpMenu.add(developerToolsMenu);

        // show console log button -
        this.showConsoleLogMenuItem = new JMenuItem("Show console log");
        this.showConsoleLogMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK));
        this.showConsoleLogMenuItem.addActionListener(LauncherMenuBar.onShowConsoleButtonClicked(parent));
        this.developerToolsMenu.add(this.showConsoleLogMenuItem);

        // debug mode checkbox -
        this.debugModeCheckBoxMenuItem = new JCheckBoxMenuItem("Debug mode");
        this.debugModeCheckBoxMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_DOWN_MASK));
        this.debugModeCheckBoxMenuItem.setState(ConfigurationManager.getInstance().isDebug());
        this.debugModeCheckBoxMenuItem.addActionListener(LauncherMenuBar.onDebugModeButtonClicked(this.debugModeCheckBoxMenuItem));
        this.developerToolsMenu.add(this.debugModeCheckBoxMenuItem);

        // separator --
        helpMenu.addSeparator();

        // about button --
        this.aboutMenuItem = new JMenuItem("About");
        this.aboutMenuItem.setMnemonic(KeyEvent.VK_T);
        this.aboutMenuItem.addActionListener(LauncherMenuBar.onAboutButtonClicked(parent));
        this.helpMenu.add(this.aboutMenuItem);
    }

    /**
     * The action to be performed on {@link #showConsoleLogMenuItem}'s click.
     * */
    private static ActionListener onShowConsoleButtonClicked(Component parent) {
        return e -> {
            LOGGER.trace("Show console button clicked");
            Try.of(ConsoleLogReader::new)
                .andThen(consoleLogReader -> consoleLogReader.packCenterAndShow(parent))
                .onFailure(throwable -> LOGGER.error("There was an error while ConsoleLogReader window: [{}]", throwable.getMessage()));
        };
    }

    /**
     * The action to be performed on {@link #debugModeCheckBoxMenuItem}'s click.
     * */
    private static ActionListener onDebugModeButtonClicked(JCheckBoxMenuItem debugMode) {
        return e -> {
            LOGGER.trace("Debug mode checkbox clicked. Enabled: [{}]", debugMode.getState());
            ConfigurationManager.getInstance().setDebug(debugMode.getState());
            LogLevelManager.changeGlobalLogLevelTo(debugMode.getState() ? Level.TRACE : Level.INFO);
        };
    }

    /**
     * The action to be performed on {@link #aboutMenuItem}'s click.
     * */
    private static ActionListener onAboutButtonClicked(Component parent) {
        return e -> {
            LOGGER.trace("About button clicked");

            VersionManagerWindow versionManagerWindow = new VersionManagerWindow();
            versionManagerWindow.packCenterAndShow(parent);
        };
    }
}
