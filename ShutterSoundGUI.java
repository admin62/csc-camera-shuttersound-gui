import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ShutterSoundGUI extends JFrame {

    private final JLabel statusLabel;
    private final JProgressBar progressBar;
    private final JTextArea logArea;
    private final JLabel headerLabel;
    private final JButton donateButton;
    private final JButton langButton;
    private ResourceBundle bundle;
    private final float scaleFactor;

    private int scale(int value) {
        return (int) (value * scaleFactor);
    }

    public ShutterSoundGUI() {
        // 기본 언어 설정 (시스템 언어 우선, 없으면 한국어)
        try {
            bundle = ResourceBundle.getBundle("messages");
        } catch (Exception e) {
            bundle = ResourceBundle.getBundle("messages", Locale.KOREAN);
        }

        // Use System Look and Feel for a more native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Calculate Scale Factor based on system font size (base 12)
        Font systemFont = UIManager.getFont("Label.font");
        float baseSize = (systemFont != null) ? systemFont.getSize2D() : 12.0f;
        this.scaleFactor = baseSize / 12.0f;

        setSize(scale(700), scale(500)); 
        setMinimumSize(new Dimension(scale(650), scale(480))); // Set minimum size to prevent layout breakage
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(true);

        // Main panel with a clean background and padding
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(scale(20), scale(30), scale(20), scale(30)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        // Title/Header Label
        headerLabel = new JLabel("", SwingConstants.CENTER);
        headerLabel.setFont(systemFont != null ? systemFont.deriveFont(Font.BOLD, baseSize * 1.5f) : new Font(Font.DIALOG, Font.BOLD, scale(18)));
        headerLabel.setForeground(new Color(33, 33, 33));
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, scale(10), 0);
        panel.add(headerLabel, gbc);

        // Status Label (Current Step)
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(systemFont != null ? systemFont.deriveFont(baseSize * 1.15f) : new Font(Font.DIALOG, Font.PLAIN, scale(14)));
        statusLabel.setForeground(new Color(66, 66, 66));
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, scale(15), 0);
        panel.add(statusLabel, gbc);

        // Progress Bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(progressBar.getPreferredSize().width, scale(8)));
        progressBar.setForeground(new Color(0, 120, 215)); 
        progressBar.setBackground(new Color(230, 230, 230));
        progressBar.setBorderPainted(false);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, scale(15), 0);
        panel.add(progressBar, gbc);

        // Log Area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, scale(12)));
        logArea.setBackground(new Color(245, 245, 245));
        logArea.setBorder(BorderFactory.createEmptyBorder(scale(5), scale(5), scale(5), scale(5)));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(scale(400), scale(150)));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(scrollPane, gbc);

        // Bottom Panel for Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, scale(10), 0));
        bottomPanel.setBackground(Color.WHITE);

        // Language Switch Button
        langButton = new JButton("KO | EN");
        langButton.setFont(systemFont != null ? systemFont.deriveFont(baseSize) : new Font(Font.DIALOG, Font.PLAIN, scale(12)));
        langButton.setForeground(new Color(100, 100, 100));
        langButton.setContentAreaFilled(false);
        langButton.setBorderPainted(false);
        langButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        langButton.addActionListener(e -> toggleLanguage());
        bottomPanel.add(langButton);

        // Donate Button
        donateButton = new JButton("");
        donateButton.setFont(systemFont != null ? systemFont.deriveFont(baseSize) : new Font(Font.DIALOG, Font.PLAIN, scale(12)));
        donateButton.setForeground(new Color(0, 120, 215));
        donateButton.setContentAreaFilled(false);
        donateButton.setBorderPainted(false);
        donateButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        donateButton.addActionListener(e -> showDonateDialog());
        bottomPanel.add(donateButton);
        
        gbc.gridy = 4;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(scale(10), 0, 0, 0);
        panel.add(bottomPanel, gbc);

        add(panel);
        updateTexts(); // 초기 텍스트 설정
    }

    private void updateTexts() {
        setTitle(bundle.getString("window.title"));
        headerLabel.setText(bundle.getString("ui.header"));
        if (statusLabel.getText().isEmpty() || statusLabel.getText().equals("Initializing...") || statusLabel.getText().equals("초기화 중...")) {
            statusLabel.setText(bundle.getString("ui.status.initializing"));
        }
        donateButton.setText(bundle.getString("ui.button.donate"));
    }

    private void toggleLanguage() {
        if (bundle.getLocale().getLanguage().equals("ko")) {
            bundle = ResourceBundle.getBundle("messages", Locale.ENGLISH);
        } else {
            bundle = ResourceBundle.getBundle("messages", Locale.KOREAN);
        }
        updateTexts();
    }

    private void showDonateDialog() {
        JDialog dialog = new JDialog(this, bundle.getString("ui.dialog.donate.title"), true);
        dialog.setSize(scale(600), scale(600));
        dialog.setMinimumSize(new Dimension(scale(400), scale(400)));
        dialog.setLocationRelativeTo(this);
        dialog.setResizable(true);

        JTabbedPane tabbedPane = new JTabbedPane();
        Font systemFont = UIManager.getFont("Label.font");
        if (systemFont != null) {
            tabbedPane.setFont(systemFont.deriveFont(Font.BOLD, systemFont.getSize2D() * 1.15f));
        } else {
            tabbedPane.setFont(new Font(Font.DIALOG, Font.BOLD, scale(14)));
        }

        tabbedPane.addTab(bundle.getString("ui.tab.naver"), createImageLabel("donate_npay.png"));
        tabbedPane.addTab(bundle.getString("ui.tab.toss"), createImageLabel("donate_toss.png"));

        dialog.add(tabbedPane);
        dialog.setVisible(true);
    }

    private JComponent createImageLabel(String fileName) {
        try {
            InputStream is = getClass().getResourceAsStream("/" + fileName);
            if (is == null) is = getClass().getResourceAsStream(fileName);
            if (is != null) {
                Image img = new ImageIcon(is.readAllBytes()).getImage();
                JPanel panel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (img == null) return;
                        
                        double ratio = Math.min((double) getWidth() / img.getWidth(this), 
                                                (double) getHeight() / img.getHeight(this));
                        int nw = (int) (img.getWidth(this) * ratio);
                        int nh = (int) (img.getHeight(this) * ratio);
                        
                        g.drawImage(img, (getWidth() - nw) / 2, (getHeight() - nh) / 2, nw, nh, this);
                    }
                };
                panel.setBackground(Color.WHITE);
                return panel;
            }
        } catch (Exception ignored) {}
        return new JLabel(bundle.getString("ui.error.image_load") + fileName, SwingConstants.CENTER);
    }

    public void startProcess() {
        // Use SwingWorker to perform long task in background
        AdbWorker worker = new AdbWorker();
        worker.execute();
    }

    // SwingWorker to handle ADB logic off the Event Dispatch Thread (EDT)
    private class AdbWorker extends SwingWorker<String, String> {

        private final List<String> ADB_FILES_WINDOWS = Arrays.asList(
            "adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "etc1tool.exe", "fastboot.exe",
            "hprof-conv.exe", "libwinpthread-1.dll", "make_f2fs_casefold.exe", "make_f2fs.exe",
            "mke2fs.conf", "mke2fs.exe", "NOTICE.txt", "source.properties", "sqlite3.exe"
        );

        private final List<String> ADB_FILES_LINUX = Arrays.asList(
            "adb", "etc1tool", "fastboot", "hprof-conv", "make_f2fs", "make_f2fs_casefold",
            "mke2fs", "mke2fs.conf", "NOTICE.txt", "source.properties", "sqlite3",
            "lib64/libc++.so"
        );

        private Path adbExecutable;

        @Override
        protected String doInBackground() throws Exception {
            String os = System.getProperty("os.name").toLowerCase();
            String resourceFolder;
            List<String> fileList;
            String execName;

            if (os.contains("win")) {
                resourceFolder = "/adb-windows/";
                fileList = ADB_FILES_WINDOWS;
                execName = "adb.exe";
            } else if (os.contains("linux")) {
                resourceFolder = "/adb-linux/";
                fileList = ADB_FILES_LINUX;
                execName = "adb";
            } else {
                return "Unsupported Operating System: " + os;
            }

            publish("Unpacking ADB tools...");
            Path tempDir = unpackAdb(resourceFolder, fileList);
            adbExecutable = tempDir.resolve(execName);

            publish("Checking for ADB devices...");
            boolean deviceAuthorized = false;

            for (int i = 0; i < 12; i++) {
                CommandResult adbDevicesResult = executeCommand(adbExecutable.toString(), "devices");
                String adbDevicesOutput = adbDevicesResult.stdout.trim();

                // Log the raw adb devices output
                if (adbDevicesOutput.equals("List of devices attached")) {
                    publish("adb devices: No device found.");
                } else {
                    publish("adb devices output:\n" + adbDevicesOutput);
                }

                if (adbDevicesOutput.matches("(?s).*List of devices attached.*\\sdevice\\s*")) {
                    publish("Device is authorized.");
                    deviceAuthorized = true;
                    break;
                } else if (adbDevicesOutput.contains("unauthorized")) {
                    publish("Device unauthorized. Check phone to allow. Retrying... (" + (i + 1) + "/12)");
                } else {
                    publish("No device found. Connect device & enable USB debugging. (" + (i + 1) + "/12)");
                }
                Thread.sleep(5000);
            }

            if (deviceAuthorized) {
                publish("Checking shutter sound setting...");
                CommandResult getSoundResult = executeCommand(adbExecutable.toString(), "shell", "settings", "get", "system", "csc_pref_camera_forced_shutter_sound_key");
                String currentSetting = getSoundResult.stdout.trim();

                if ("1".equals(currentSetting)) {
                    publish("Disabling shutter sound...");
                    executeCommand(adbExecutable.toString(), "shell", "settings", "put", "system", "csc_pref_camera_forced_shutter_sound_key", "0");
                    CommandResult verifyResult = executeCommand(adbExecutable.toString(), "shell", "settings", "get", "system", "csc_pref_camera_forced_shutter_sound_key");
                    if ("0".equals(verifyResult.stdout.trim())) {
                        return "Success! Camera shutter sound disabled.";
                    } else {
                        return "Error: Failed to disable shutter sound. (Value: " + verifyResult.stdout.trim() + ")";
                    }
                } else if ("0".equals(currentSetting)) {
                    return "Already disabled. No action needed.";
                } else {
                    publish("Unexpected status: " + currentSetting);
                    return "Could not determine shutter sound status. (Output: " + currentSetting + ")";
                }
            } else {
                return "Timeout: No authorized device found.";
            }
        }

        private String getTimestamp() {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        @Override
        protected void process(List<String> chunks) {
            for (String message : chunks) {
                // Show only the first line on the status label to keep it clean
                if (message.contains("\n")) {
                    statusLabel.setText(message.split("\n")[0] + " ...");
                } else {
                    statusLabel.setText(message);
                }
                logArea.append("[" + getTimestamp() + "] > " + message + "\n");
            }
            // Auto-scroll to the bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }

        @Override
        protected void done() {
            // Task is complete
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            String timestamp = getTimestamp();
            try {
                String finalStatus = get();
                statusLabel.setText(finalStatus);
                logArea.append("\n[" + timestamp + "] [FINISH] " + finalStatus + "\n");
            } catch (InterruptedException | ExecutionException e) {
                String errorMsg = "Error: " + e.getCause().getMessage();
                statusLabel.setText(errorMsg);
                logArea.append("\n[" + timestamp + "] [ERROR] " + errorMsg + "\n");
            }
            // Auto-scroll to the bottom
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }

        private Path unpackAdb(String resourceFolder, List<String> fileList) throws IOException {
            Path tempDir = Files.createTempDirectory("adb-gui-temp-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.walk(tempDir).sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                        try { Files.delete(path); } catch (IOException ex) { /* ignore */ }
                    });
                } catch (IOException ex) { /* ignore */ }
            }));
            for (String fileName : fileList) {
                Path targetPath = tempDir.resolve(fileName);
                Files.createDirectories(targetPath.getParent()); // Ensure subdirectories exist
                
                try (InputStream is = ShutterSoundGUI.class.getResourceAsStream(resourceFolder + fileName)) {
                    if (is == null) throw new IOException("Cannot find resource: " + resourceFolder + fileName);
                    Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                
                // Set executable permission for Linux
                if (!System.getProperty("os.name").toLowerCase().contains("win")) {
                    targetPath.toFile().setExecutable(true);
                }
            }
            return tempDir;
        }

        private CommandResult executeCommand(String... command) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Combine stdout and stderr
            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stdout.append(line).append(System.lineSeparator());
                }
            }
            process.waitFor();
            return new CommandResult(stdout.toString());
        }

        private class CommandResult {
            final String stdout;
            CommandResult(String stdout) { this.stdout = stdout; }
        }
    }

    public static void main(String[] args) {
        // Run the GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ShutterSoundGUI gui = new ShutterSoundGUI();
            gui.setVisible(true);
            gui.startProcess();
        });
    }
}
