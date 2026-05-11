package com.minecraft.installer;

import com.google.gson.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class ModInstaller extends JFrame {

    // ── Paths ────────────────────────────────────────────────────────────────

    private static final String APPDATA = resolveAppdata();
    private static final String USER_HOME = System.getProperty("user.home");

    private static final Path PROFILE_18     = Paths.get(USER_HOME, ".lunarclient", "profiles", "lunar", "1.8", "profile.json");
    private static final Path PROFILE_17     = Paths.get(USER_HOME, ".lunarclient", "profiles", "lunar", "1.7", "profile.json");
    private static final Path PROFILE_18_BAK = PROFILE_18.resolveSibling("profile.json.bak");
    private static final Path PROFILE_17_BAK = PROFILE_17.resolveSibling("profile.json.bak");
    private static final Path LAUNCHER_JSON  = Paths.get(USER_HOME, ".lunarclient", "settings", "launcher.json");
    private static final Path AGENTLOADER_DIR = Paths.get(APPDATA, ".minecraft", "agentloader");
    private static final Path MOD_LOADER_JAR  = AGENTLOADER_DIR.resolve("mod-loader.jar");
    private static final Path AGENTMODS_DIR   = Paths.get(APPDATA, ".minecraft", "agentmods");

    private static String resolveAppdata() {
        String v = System.getenv("APPDATA");
        return v != null ? v : System.getProperty("user.home")
                + File.separator + "AppData" + File.separator + "Roaming";
    }

    // ── UI fields ────────────────────────────────────────────────────────────

    private final JTextPane logPane;
    private final StyledDocument logDoc;
    private final JButton installBtn;
    private final JButton uninstallBtn;
    private final JLabel statusLabel;
    private final JCheckBox cb18;
    private final JCheckBox cb17;

    // ── Styles ───────────────────────────────────────────────────────────────

    private final Style styleNormal;
    private final Style styleOk;
    private final Style styleError;
    private final Style styleDim;
    private final Style styleSection;

    public ModInstaller() {
        super("Lunar Client Mod Installer");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(660, 520);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(new Color(30, 30, 30));

        // ── Header ───────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(20, 20, 20));
        header.setBorder(new EmptyBorder(14, 18, 14, 18));

        JLabel title = new JLabel("Lunar Client Mod Installer");
        title.setForeground(new Color(230, 230, 230));
        title.setFont(new Font("SansSerif", Font.BOLD, 16));
        header.add(title, BorderLayout.WEST);

        statusLabel = new JLabel("Ready");
        statusLabel.setForeground(new Color(140, 140, 140));
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        header.add(statusLabel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Log area ─────────────────────────────────────────────────────────
        logPane = new JTextPane();
        logPane.setEditable(false);
        logPane.setBackground(new Color(18, 18, 18));
        logPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logPane.setBorder(new EmptyBorder(6, 10, 6, 10));
        logDoc = logPane.getStyledDocument();

        styleNormal  = addStyle("normal",  new Color(200, 200, 200));
        styleOk      = addStyle("ok",      new Color(100, 210, 100));
        styleError   = addStyle("error",   new Color(230, 80,  80));
        styleDim     = addStyle("dim",     new Color(110, 110, 110));
        styleSection = addStyle("section", new Color(100, 180, 255));

        JScrollPane scroll = new JScrollPane(logPane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        root.add(scroll, BorderLayout.CENTER);

        // ── Bottom panel: version row + button row ────────────────────────────
        JPanel bottom = new JPanel(new BorderLayout(0, 0));
        bottom.setBackground(new Color(25, 25, 25));

        // Version checkboxes
        JPanel versionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 8));
        versionRow.setBackground(new Color(25, 25, 25));
        versionRow.setBorder(new EmptyBorder(2, 8, 0, 8));

        JLabel vLabel = new JLabel("Install for:");
        vLabel.setForeground(new Color(170, 170, 170));
        vLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        versionRow.add(vLabel);

        cb18 = makeCheckbox("Lunar Client 1.8", true);
        cb17 = makeCheckbox("Lunar Client 1.7", true);
        versionRow.add(cb18);
        versionRow.add(cb17);
        bottom.add(versionRow, BorderLayout.NORTH);

        // Buttons
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        btnBar.setBackground(new Color(25, 25, 25));
        btnBar.setBorder(new EmptyBorder(0, 10, 6, 10));

        uninstallBtn = makeButton("Uninstall / Rollback", new Color(160, 60, 60));
        installBtn   = makeButton("Install", new Color(50, 130, 60));
        btnBar.add(uninstallBtn);
        btnBar.add(installBtn);
        bottom.add(btnBar, BorderLayout.SOUTH);

        root.add(bottom, BorderLayout.SOUTH);
        setContentPane(root);

        // ── Initial info ─────────────────────────────────────────────────────
        refreshUninstallState();
        log("Mods bundled in this installer:", styleNormal);
        log("  - hitsound-mod.jar  (block-hit sound)", styleDim);
        log("  - timerr.jar        (MCC timer overlay)", styleDim);
        log("", styleDim);
        log("Shared paths:", styleSection);
        log("  Mod loader  : " + MOD_LOADER_JAR, styleDim);
        log("  Agentmods   : " + AGENTMODS_DIR, styleDim);
        log("  Launcher    : " + LAUNCHER_JSON, styleDim);
        log("", styleDim);
        log("Version profiles:", styleSection);
        log("  1.8 profile : " + PROFILE_18 + (Files.exists(PROFILE_18_BAK) ? "  [backup found]" : ""), styleDim);
        log("  1.7 profile : " + PROFILE_17 + (Files.exists(PROFILE_17_BAK) ? "  [backup found]" : ""), styleDim);
        log("", styleDim);

        // ── Listeners ────────────────────────────────────────────────────────
        installBtn.addActionListener(e -> {
            if (!cb18.isSelected() && !cb17.isSelected()) {
                JOptionPane.showMessageDialog(this,
                        "Select at least one version to install.",
                        "No version selected", JOptionPane.WARNING_MESSAGE);
                return;
            }
            runAsync(this::doInstall);
        });

        uninstallBtn.addActionListener(e -> {
            int choice = JOptionPane.showConfirmDialog(this,
                    "This will restore the original profile.json backup(s)\nand remove the installed mod JARs.\n\nContinue?",
                    "Confirm Uninstall", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                runAsync(this::doUninstall);
            }
        });
    }

    // ── Install ──────────────────────────────────────────────────────────────

    private void doInstall() {
        setButtons(false);
        setStatus("Installing...");
        try {
            int step = 1;
            int total = 4 + (cb18.isSelected() ? 1 : 0) + (cb17.isSelected() ? 1 : 0);

            // 1. Extract mod-loader
            log("[" + step++ + "/" + total + "] Installing mod loader...", styleNormal);
            Files.createDirectories(AGENTLOADER_DIR);
            extractResource("/mod-loader.jar", MOD_LOADER_JAR);
            log("      OK -> " + MOD_LOADER_JAR, styleOk);

            // 2. Patch 1.8 profile
            if (cb18.isSelected()) {
                log("[" + step++ + "/" + total + "] Patching Lunar Client 1.8 profile...", styleNormal);
                patchProfileWithBackup(PROFILE_18, PROFILE_18_BAK, "1.8");
            }

            // 3. Patch 1.7 profile
            if (cb17.isSelected()) {
                log("[" + step++ + "/" + total + "] Patching Lunar Client 1.7 profile...", styleNormal);
                patchProfileWithBackup(PROFILE_17, PROFILE_17_BAK, "1.7");
            }

            // 4. Enable advancedMode
            log("[" + step++ + "/" + total + "] Enabling advanced mode in launcher settings...", styleNormal);
            if (!Files.exists(LAUNCHER_JSON)) {
                log("      WARN -> launcher.json not found, skipping.", styleError);
            } else {
                patchLauncher(LAUNCHER_JSON);
                log("      OK -> advancedMode = true", styleOk);
            }

            // 5. Create agentmods dir
            log("[" + step++ + "/" + total + "] Setting up agentmods folder...", styleNormal);
            if (!Files.exists(AGENTMODS_DIR)) {
                Files.createDirectories(AGENTMODS_DIR);
                log("      OK -> Created: " + AGENTMODS_DIR, styleOk);
            } else {
                log("      OK -> Already exists.", styleDim);
            }

            // 6. Copy mods
            log("[" + step + "/" + total + "] Installing mods...", styleNormal);
            extractResource("/hitsound-mod.jar", AGENTMODS_DIR.resolve("hitsound-mod.jar"));
            log("      OK -> hitsound-mod.jar", styleOk);
            extractResource("/timerr.jar", AGENTMODS_DIR.resolve("timerr.jar"));
            log("      OK -> timerr.jar", styleOk);

            log("", styleNormal);
            String versions = (cb18.isSelected() ? "1.8" : "") +
                              (cb18.isSelected() && cb17.isSelected() ? " + " : "") +
                              (cb17.isSelected() ? "1.7" : "");
            log("Installation complete! Launch Lunar Client " + versions + " to use your mods.", styleOk);
            setStatus("Installed");

        } catch (Exception ex) {
            log("", styleNormal);
            log("ERROR: " + ex.getMessage(), styleError);
            setStatus("Install failed");
        } finally {
            setButtons(true);
            refreshUninstallState();
        }
    }

    private void patchProfileWithBackup(Path profile, Path backup, String version) throws IOException {
        if (!Files.exists(profile)) {
            log("      WARN -> profile.json not found for " + version +
                ". Launch Lunar Client " + version + " at least once first. Skipping.", styleError);
            return;
        }
        if (!Files.exists(backup)) {
            Files.copy(profile, backup);
            log("      Backup saved -> " + backup, styleDim);
        } else {
            log("      Backup already exists, skipping backup.", styleDim);
        }
        patchProfile(profile, MOD_LOADER_JAR.toAbsolutePath().toString());
        log("      OK -> " + version + " profile.json patched", styleOk);
    }

    // ── Uninstall / Rollback ─────────────────────────────────────────────────

    private void doUninstall() {
        setButtons(false);
        setStatus("Uninstalling...");
        boolean any18 = Files.exists(PROFILE_18_BAK);
        boolean any17 = Files.exists(PROFILE_17_BAK);
        int step = 1;
        int total = 2 + (any18 ? 1 : 0) + (any17 ? 1 : 0);
        try {
            if (!any18 && !any17) {
                throw new IOException("No backups found. Nothing to uninstall.");
            }

            if (any18) {
                log("[" + step++ + "/" + total + "] Restoring 1.8 profile.json...", styleNormal);
                Files.copy(PROFILE_18_BAK, PROFILE_18, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(PROFILE_18_BAK);
                log("      OK -> 1.8 profile.json restored.", styleOk);
            }

            if (any17) {
                log("[" + step++ + "/" + total + "] Restoring 1.7 profile.json...", styleNormal);
                Files.copy(PROFILE_17_BAK, PROFILE_17, StandardCopyOption.REPLACE_EXISTING);
                Files.delete(PROFILE_17_BAK);
                log("      OK -> 1.7 profile.json restored.", styleOk);
            }

            log("[" + step++ + "/" + total + "] Removing mod JARs...", styleNormal);
            deleteIfExists(AGENTMODS_DIR.resolve("hitsound-mod.jar"), "hitsound-mod.jar");
            deleteIfExists(AGENTMODS_DIR.resolve("timerr.jar"), "timerr.jar");

            log("[" + step + "/" + total + "] Removing mod loader...", styleNormal);
            deleteIfExists(MOD_LOADER_JAR, "mod-loader.jar");

            log("", styleNormal);
            log("Uninstall complete. Original profile(s) restored.", styleOk);
            setStatus("Uninstalled");

        } catch (Exception ex) {
            log("", styleNormal);
            log("ERROR: " + ex.getMessage(), styleError);
            setStatus("Uninstall failed");
        } finally {
            setButtons(true);
            refreshUninstallState();
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void deleteIfExists(Path path, String label) throws IOException {
        if (Files.deleteIfExists(path)) {
            log("      OK -> removed " + label, styleOk);
        } else {
            log("      Skipped (not found): " + label, styleDim);
        }
    }

    private void refreshUninstallState() {
        SwingUtilities.invokeLater(() ->
                uninstallBtn.setEnabled(Files.exists(PROFILE_18_BAK) || Files.exists(PROFILE_17_BAK)));
    }

    private void setStatus(final String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void setButtons(final boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            installBtn.setEnabled(enabled);
            uninstallBtn.setEnabled(enabled && (Files.exists(PROFILE_18_BAK) || Files.exists(PROFILE_17_BAK)));
            cb18.setEnabled(enabled);
            cb17.setEnabled(enabled);
        });
    }

    private void log(final String text, final Style style) {
        SwingUtilities.invokeLater(() -> {
            try {
                logDoc.insertString(logDoc.getLength(), text + "\n", style);
                logPane.setCaretPosition(logDoc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    private void runAsync(Runnable task) {
        new Thread(task).start();
    }

    private Style addStyle(String name, Color color) {
        Style s = logDoc.addStyle(name, null);
        StyleConstants.setForeground(s, color);
        return s;
    }

    private static JButton makeButton(String label, Color bg) {
        JButton btn = new JButton(label);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("SansSerif", Font.BOLD, 13));
        btn.setBorder(new EmptyBorder(8, 18, 8, 18));
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        return btn;
    }

    private static JCheckBox makeCheckbox(String label, boolean selected) {
        JCheckBox cb = new JCheckBox(label, selected);
        cb.setForeground(new Color(210, 210, 210));
        cb.setBackground(new Color(25, 25, 25));
        cb.setFont(new Font("SansSerif", Font.PLAIN, 12));
        cb.setFocusPainted(false);
        return cb;
    }

    private static void extractResource(String resourcePath, Path destination) throws IOException {
        try (InputStream is = ModInstaller.class.getResourceAsStream(resourcePath)) {
            if (is == null) throw new IOException("Resource not bundled: " + resourcePath);
            Files.copy(is, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void patchLauncher(Path launcherPath) throws IOException {
        String content = new String(Files.readAllBytes(launcherPath), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        if (!root.has("settings") || !root.get("settings").isJsonObject()) {
            root.add("settings", new JsonObject());
        }
        root.getAsJsonObject("settings").addProperty("advancedMode", true);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.write(launcherPath, gson.toJson(root).getBytes(StandardCharsets.UTF_8));
    }

    private static void patchProfile(Path profilePath, String modLoaderAbsPath) throws IOException {
        String content = new String(Files.readAllBytes(profilePath), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(content).getAsJsonObject();
        if (!root.has("overrides") || !root.get("overrides").isJsonObject()) {
            root.add("overrides", new JsonObject());
        }
        root.getAsJsonObject("overrides")
                .addProperty("jvmArguments", "-javaagent:" + modLoaderAbsPath);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        Files.write(profilePath, gson.toJson(root).getBytes(StandardCharsets.UTF_8));
    }

    // ── Entry point ──────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (!"false".equalsIgnoreCase(System.getProperty("sun.java2d.d3d"))) {
            String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            String jar  = new File(ModInstaller.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getAbsolutePath();
            new ProcessBuilder(java, "-Dsun.java2d.d3d=false", "-jar", jar)
                    .inheritIO().start();
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new ModInstaller().setVisible(true));
    }
}
