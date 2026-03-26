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

        // ===== Constants: OS Detection =====
        private static final String OS_WINDOWS_IDENTIFIER = "win";
        private static final String OS_LINUX_IDENTIFIER = "linux";
        private static final String RESOURCE_FOLDER_WINDOWS = "/adb-windows/";
        private static final String RESOURCE_FOLDER_LINUX = "/adb-linux/";
        private static final String ADB_EXECUTABLE_WINDOWS = "adb.exe";
        private static final String ADB_EXECUTABLE_LINUX = "adb";

        // ===== Constants: Device Detection (USB Scanning) =====
        private static final String STEP_1_SCANNING = "[STEP 1] Scanning USB ports for Samsung devices (lsusb)...";
        private static final String STEP_1_SCANNING_WINDOWS = "[STEP 1] Scanning USB ports for Samsung devices (PowerShell)...";
        private static final String LSUSB_SAMSUNG_PATTERN = "ID ([0-9a-fA-F]{4}:[0-9a-fA-F]{4}) Samsung";
        private static final String ISERIAL_PATTERN = "iSerial\\s+\\d+\\s+(\\S+)";
        private static final String SAMSUNG_VENDOR_ID = "VID_04E8";
        private static final String DEVICE_DETECTED_MSG = "> Detected physical device: ";
        private static final String NO_SAMSUNG_DEVICES_MSG = "> No Samsung USB devices detected via lsusb.";
        private static final String FOUND_SAMSUNG_DEVICES_MSG = "> Found ";
        private static final String SAMSUNG_DEVICES_ON_USB = " Samsung device(s) on USB bus. Extracting serials...";

        // ===== Constants: ADB Communication =====
        private static final String STEP_2_ADB_CHECK = "[STEP 2] Checking ADB connectivity and USB debugging status...";
        private static final String ADB_DEVICES_COMMAND = "devices";
        private static final String ADB_DEVICES_EMPTY = "List of devices attached";
        private static final String ADB_DEVICES_OUTPUT = "adb devices output:\n";
        private static final String ADB_AUTHORIZED_PATTERN = "(\\S+)\\s+device";
        private static final String DEVICE_STATE_AUTHORIZED = "\tdevice";
        private static final String DEVICE_STATE_UNAUTHORIZED = "\tunauthorized";
        private static final String DEVICE_AUTHORIZED_MSG = "Device ";
        private static final String DEVICE_AUTHORIZED_READY = " is authorized and ready.";
        private static final String DEVICE_UNAUTHORIZED_MSG = " found but UNAUTHORIZED. Check phone screen.";
        private static final String DEVICE_UNEXPECTED_STATE = " found in unexpected state.";
        private static final String AUTHORIZED_DEVICE_FOUND = "Authorized device found: ";
        private static final String RETRY_MSG = "Retrying in 5 seconds... (";
        private static final int DEVICE_CHECK_MAX_RETRIES = 12;
        private static final long RETRY_INTERVAL_MS = 5000;

        // ===== Constants: Shutter Sound Settings =====
        private static final String SHUTTER_SOUND_SETTING_KEY = "csc_pref_camera_forced_shutter_sound_key";
        private static final String SHUTTER_SOUND_VALUE_ENABLED = "1";
        private static final String SHUTTER_SOUND_VALUE_DISABLED = "0";
        private static final String PROCESSING_DEVICE_MSG = "Processing device: ";
        private static final String CHECKING_SHUTTER_SOUND = "Checking shutter sound setting for ";
        private static final String DISABLING_SHUTTER_SOUND = "Disabling shutter sound on ";
        private static final String SUCCESS_SUFFIX = ": Success";
        private static final String ERROR_SUFFIX = ": Error (Value: ";
        private static final String ALREADY_DISABLED_SUFFIX = ": Already disabled";
        private static final String UNKNOWN_STATUS_SUFFIX = ": Unknown status (";

        // ===== Constants: Result Messages =====
        private static final String STEP_FINISH = "[FINISH] ";
        private static final String RESULT_FINISHED = "Finished: \n";
        private static final String RESULT_TIMEOUT_WITH_DEVICE = "Timeout: Samsung device is connected via USB, but USB Debugging is not enabled or authorized.";
        private static final String RESULT_TIMEOUT_NO_DEVICE = "Timeout: No authorized device found.";
        private static final String UNSUPPORTED_OS = "Unsupported Operating System: ";
        private static final String UNPACKING_ADB = "Unpacking ADB tools...";

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

        // ===== Helper Class: AdbEnvironment =====
        /**
         * Holds information about the ADB environment (OS-specific configuration).
         */
        private class AdbEnvironment {
            final String resourceFolder;
            final List<String> fileList;
            final String executableName;

            AdbEnvironment(String resourceFolder, List<String> fileList, String executableName) {
                this.resourceFolder = resourceFolder;
                this.fileList = fileList;
                this.executableName = executableName;
            }
        }

        // ===== Helper Method: Initialize ADB Environment =====
        /**
         * Determines the OS and returns appropriate ADB environment configuration.
         * @return AdbEnvironment containing OS-specific paths and executable names
         */
        private AdbEnvironment initializeAdbEnvironment() {
            String os = System.getProperty("os.name").toLowerCase();
            boolean isLinux = os.contains(OS_LINUX_IDENTIFIER);
            boolean isWindows = os.contains(OS_WINDOWS_IDENTIFIER);

            if (isWindows) {
                return new AdbEnvironment(RESOURCE_FOLDER_WINDOWS, ADB_FILES_WINDOWS, ADB_EXECUTABLE_WINDOWS);
            } else if (isLinux) {
                return new AdbEnvironment(RESOURCE_FOLDER_LINUX, ADB_FILES_LINUX, ADB_EXECUTABLE_LINUX);
            } else {
                return null; // Unsupported OS
            }
        }

        // ===== Helper Method: Detect Physical Devices =====
        /**
         * Detects Samsung USB devices connected to the system (physical detection via lsusb/PowerShell).
         * @return List of detected device serials
         */
        private List<String> detectPhysicalDevices() {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains(OS_LINUX_IDENTIFIER)) {
                return getLinuxSamsungSerials();
            } else if (os.contains(OS_WINDOWS_IDENTIFIER)) {
                return getWindowsSamsungSerials();
            } else {
                return new java.util.ArrayList<>();
            }
        }

        // ===== Helper Method: Wait for ADB Authorization =====
        /**
         * Waits for ADB to detect and authorize connected devices (up to DEVICE_CHECK_MAX_RETRIES attempts).
         * @param physicalSerials List of physically detected device serials to match against ADB
         * @return List of serials that are authorized and ready
         */
        private List<String> waitForAdbAuthorization(List<String> physicalSerials) throws InterruptedException, IOException {
            List<String> authorizedSerials = new java.util.ArrayList<>();
            boolean deviceAuthorized = false;

            for (int i = 0; i < DEVICE_CHECK_MAX_RETRIES; i++) {
                CommandResult adbDevicesResult = executeCommand(adbExecutable.toString(), ADB_DEVICES_COMMAND);
                String adbDevicesOutput = adbDevicesResult.stdout.trim();

                // Log the raw adb devices output
                if (adbDevicesOutput.equals(ADB_DEVICES_EMPTY)) {
                    publish("adb devices: No device found via ADB.");
                } else {
                    publish(ADB_DEVICES_OUTPUT + adbDevicesOutput);
                }

                if (!physicalSerials.isEmpty()) {
                    // Precise check: Match physical Samsung devices with authorized ones in ADB
                    for (String serial : physicalSerials) {
                        if (adbDevicesOutput.contains(serial + DEVICE_STATE_AUTHORIZED)) {
                            if (!authorizedSerials.contains(serial)) {
                                authorizedSerials.add(serial);
                                publish(DEVICE_AUTHORIZED_MSG + serial + DEVICE_AUTHORIZED_READY);
                            }
                            deviceAuthorized = true;
                        } else if (adbDevicesOutput.contains(serial + DEVICE_STATE_UNAUTHORIZED)) {
                            publish(DEVICE_AUTHORIZED_MSG + serial + DEVICE_UNAUTHORIZED_MSG);
                        } else if (adbDevicesOutput.contains(serial)) {
                            publish(DEVICE_AUTHORIZED_MSG + serial + DEVICE_UNEXPECTED_STATE);
                        } else {
                            // connected physically but not appearing in adb yet
                        }
                    }
                } else {
                    // Fallback: Just look for any authorized device
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(ADB_AUTHORIZED_PATTERN).matcher(adbDevicesOutput);
                    while (m.find()) {
                        String serial = m.group(1);
                        if (!authorizedSerials.contains(serial)) {
                            authorizedSerials.add(serial);
                            publish(AUTHORIZED_DEVICE_FOUND + serial);
                        }
                        deviceAuthorized = true;
                    }
                }

                if (deviceAuthorized) break;

                if (i < DEVICE_CHECK_MAX_RETRIES - 1) {
                    publish(RETRY_MSG + (i + 1) + "/" + DEVICE_CHECK_MAX_RETRIES + ")");
                    Thread.sleep(RETRY_INTERVAL_MS);
                }
            }

            return authorizedSerials;
        }

        // ===== Helper Method: Disable Shutter Sound on Devices =====
        /**
         * Disables the shutter sound setting on each authorized device.
         * @param authorizedSerials List of device serials to process
         * @return Result summary string with success/failure status for each device
         */
        private String disableShutterSoundOnDevices(List<String> authorizedSerials) throws IOException, InterruptedException {
            StringBuilder finalResult = new StringBuilder();

            for (String targetSerial : authorizedSerials) {
                publish(PROCESSING_DEVICE_MSG + targetSerial);
                publish(CHECKING_SHUTTER_SOUND + targetSerial + "...");

                CommandResult getSoundResult = executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "get", "system", SHUTTER_SOUND_SETTING_KEY);
                String currentSetting = getSoundResult.stdout.trim();

                if (SHUTTER_SOUND_VALUE_ENABLED.equals(currentSetting)) {
                    publish(DISABLING_SHUTTER_SOUND + targetSerial + "...");
                    executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "put", "system", SHUTTER_SOUND_SETTING_KEY, SHUTTER_SOUND_VALUE_DISABLED);
                    CommandResult verifyResult = executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "get", "system", SHUTTER_SOUND_SETTING_KEY);
                    if (SHUTTER_SOUND_VALUE_DISABLED.equals(verifyResult.stdout.trim())) {
                        finalResult.append(targetSerial).append(SUCCESS_SUFFIX).append("\n");
                    } else {
                        finalResult.append(targetSerial).append(ERROR_SUFFIX).append(verifyResult.stdout.trim()).append(")\n");
                    }
                } else if (SHUTTER_SOUND_VALUE_DISABLED.equals(currentSetting)) {
                    finalResult.append(targetSerial).append(ALREADY_DISABLED_SUFFIX).append("\n");
                } else {
                    finalResult.append(targetSerial).append(UNKNOWN_STATUS_SUFFIX).append(currentSetting).append(")\n");
                }
            }

            return finalResult.toString().trim();
        }

        private List<String> getLinuxSamsungSerials() {
            List<String> serials = new java.util.ArrayList<>();
            try {
                publish(STEP_1_SCANNING);
                // 1. Find Samsung device IDs
                CommandResult lsusbResult = executeCommand("lsusb");
                java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile(LSUSB_SAMSUNG_PATTERN);
                java.util.regex.Matcher idMatcher = idPattern.matcher(lsusbResult.stdout);
                
                java.util.List<String> ids = new java.util.ArrayList<>();
                while (idMatcher.find()) {
                    ids.add(idMatcher.group(1));
                }

                if (ids.isEmpty()) {
                    publish(NO_SAMSUNG_DEVICES_MSG);
                    return serials;
                }

                publish(FOUND_SAMSUNG_DEVICES_MSG + ids.size() + SAMSUNG_DEVICES_ON_USB);

                // 2. Get iSerial for each Samsung ID
                for (String id : ids) {
                    CommandResult vResult = executeCommand("lsusb", "-v", "-d", id);
                    java.util.regex.Pattern serialPattern = java.util.regex.Pattern.compile(ISERIAL_PATTERN);
                    java.util.regex.Matcher serialMatcher = serialPattern.matcher(vResult.stdout);
                    if (serialMatcher.find()) {
                        String serial = serialMatcher.group(1);
                        if (!serials.contains(serial)) {
                            publish(DEVICE_DETECTED_MSG + serial);
                            serials.add(serial);
                        }
                    }
                }
            } catch (Exception e) {
                publish("Warning: Could not check physical USB devices (lsusb error).");
            }
            return serials;
        }

        private List<String> getWindowsSamsungSerials() {
            List<String> serials = new java.util.ArrayList<>();
            try {
                publish(STEP_1_SCANNING_WINDOWS);
                // Get InstanceId of present devices with Samsung's Vendor ID (04E8)
                CommandResult psResult = executeCommand("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", 
                    "Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -match '" + SAMSUNG_VENDOR_ID + "' } | Select-Object -ExpandProperty InstanceId");
                
                String[] lines = psResult.stdout.split("\\r?\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    int lastBackslash = line.lastIndexOf('\\');
                    if (lastBackslash != -1 && lastBackslash < line.length() - 1) {
                        String serial = line.substring(lastBackslash + 1);
                        // Clean up composite device suffix if present
                        if (serial.contains("&")) {
                            serial = serial.split("&")[0];
                        }
                        if (!serials.contains(serial)) {
                            publish(DEVICE_DETECTED_MSG + serial);
                            serials.add(serial);
                        }
                    }
                }
            } catch (Exception e) {
                publish("Warning: Could not check physical USB devices (PowerShell error).");
            }
            return serials;
        }

        @Override
        protected String doInBackground() throws Exception {
            // Step 1: Initialize ADB environment based on OS
            AdbEnvironment env = initializeAdbEnvironment();
            if (env == null) {
                String os = System.getProperty("os.name").toLowerCase();
                return UNSUPPORTED_OS + os;
            }

            // Step 2: Extract ADB tools
            publish(UNPACKING_ADB);
            Path tempDir = unpackAdb(env.resourceFolder, env.fileList);
            adbExecutable = tempDir.resolve(env.executableName);

            // Step 3: Detect physically connected Samsung devices
            List<String> physicalSerials = detectPhysicalDevices();

            // Step 4: Wait for ADB to authorize devices
            publish(STEP_2_ADB_CHECK);
            List<String> authorizedSerials = waitForAdbAuthorization(physicalSerials);

            // Step 5: Disable shutter sound on authorized devices and report results
            if (!authorizedSerials.isEmpty()) {
                String resultDetails = disableShutterSoundOnDevices(authorizedSerials);
                return RESULT_FINISHED + resultDetails;
            } else {
                if (!physicalSerials.isEmpty()) {
                    return RESULT_TIMEOUT_WITH_DEVICE;
                }
                return RESULT_TIMEOUT_NO_DEVICE;
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
                logArea.append("\n[" + timestamp + "] " + STEP_FINISH + finalStatus + "\n");
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
