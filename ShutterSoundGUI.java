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
import java.util.PropertyResourceBundle;
import java.util.concurrent.ExecutionException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ShutterSoundGUI extends JFrame {

    // ===== UI Constants: Colors =====
    private static final Color COLOR_TEXT_MAIN = new Color(33, 33, 33);
    private static final Color COLOR_TEXT_SUB = new Color(66, 66, 66);
    private static final Color COLOR_ACCENT = new Color(0, 120, 215);
    private static final Color COLOR_BG_LOG = new Color(245, 245, 245);
    private static final Color COLOR_BORDER = new Color(220, 220, 220);

    // ===== Environment Helpers =====
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

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

    /**
     * Helper to load ResourceBundle with UTF-8 support and Korean fallback
     */
    private static ResourceBundle loadBundle(Locale locale) {
        try {
            return (locale == null) 
                ? ResourceBundle.getBundle("messages", new UTF8Control())
                : ResourceBundle.getBundle("messages", locale, new UTF8Control());
        } catch (Exception e) {
            return ResourceBundle.getBundle("messages", Locale.KOREAN, new UTF8Control());
        }
    }

    /**
     * Appends a message to the log area with a timestamp and handles auto-scrolling
     */
    private void appendLog(String message, String timestamp) {
        JScrollPane scrollPane = (logArea.getParent() instanceof JViewport && logArea.getParent().getParent() instanceof JScrollPane)
                ? (JScrollPane) logArea.getParent().getParent() : null;
        
        boolean shouldAutoScroll = true;
        if (scrollPane != null) {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            shouldAutoScroll = (vertical.getValue() + vertical.getVisibleAmount() >= vertical.getMaximum() - 10);
        }

        logArea.append("[" + timestamp + "] " + message + "\n");

        if (shouldAutoScroll) {
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    /**
     * Apply font to all UIManager components for consistent Korean text rendering
     */
    private void setUIFont(Font font) {
        java.util.Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof Font) {
                UIManager.put(key, font);
            }
        }
    }

    /**
     * Show disclaimer dialog at startup with theme matching main window
     * @param bundle ResourceBundle for localization
     * @return true if user accepts, false otherwise
     */
    private static boolean showDisclaimerDialog(ResourceBundle bundle) {
        String title = bundle.getString("disclaimer.title");
        String message = bundle.getString("disclaimer.message");
        String agreeText = bundle.getString("disclaimer.agree");
        String declineText = bundle.getString("disclaimer.decline");
        
        // Replace escaped newlines in properties file
        message = message.replace("\\n", "\n");
        
        // Calculate scale factor (same logic as main window)
        Font systemFont = UIManager.getFont("Label.font");
        float baseSize = (systemFont != null) ? systemFont.getSize2D() : 12.0f;
        float scaleFactor = baseSize / 12.0f;
        
        // Get preferred font
        Font preferredFont = getPreferredFontStatic(baseSize);
        if (preferredFont != null) {
            systemFont = preferredFont;
        }
        
        // Use CountDownLatch to block main thread until user responds
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final int[] result = {1}; // 1 = declined by default
        
        // Create JFrame instead of JDialog to ensure it appears in the taskbar
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize((int)(550 * scaleFactor), (int)(400 * scaleFactor));
        frame.setMinimumSize(new Dimension((int)(500 * scaleFactor), (int)(350 * scaleFactor)));
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        
        // Main panel with white background matching main window
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createEmptyBorder((int)(15 * scaleFactor), (int)(20 * scaleFactor), 
                                                              (int)(15 * scaleFactor), (int)(20 * scaleFactor)));
        
        // Message text area with larger font - Use Dialog (logical font) for better symbol fallback
        JTextArea messageArea = new JTextArea(message);
        messageArea.setEditable(false);
        messageArea.setFocusable(false);
        messageArea.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font(Font.DIALOG, Font.PLAIN, (int)baseSize));
        messageArea.setForeground(new Color(33, 33, 33));
        messageArea.setBackground(Color.WHITE);
        messageArea.setBorder(BorderFactory.createEmptyBorder((int)(10 * scaleFactor), 0, (int)(10 * scaleFactor), 0));
        
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, (int)(10 * scaleFactor), 0));
        buttonPanel.setBackground(Color.WHITE);
        
        JButton agreeButton = new JButton(agreeText);
        agreeButton.setFont(new Font(Font.DIALOG, Font.PLAIN, (int)baseSize));
        agreeButton.setPreferredSize(new Dimension((int)(120 * scaleFactor), (int)(38 * scaleFactor)));
        agreeButton.setBackground(COLOR_ACCENT);
        agreeButton.setForeground(Color.WHITE);
        agreeButton.setFocusPainted(false);
        agreeButton.setFocusable(false);
        agreeButton.setBorder(BorderFactory.createEmptyBorder());
        agreeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        agreeButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { agreeButton.setBackground(COLOR_ACCENT.brighter()); }
            public void mouseExited(java.awt.event.MouseEvent evt) { agreeButton.setBackground(COLOR_ACCENT); }
        });
        agreeButton.addActionListener(e -> {
            result[0] = 0;
            latch.countDown();
            frame.dispose();
        });
        buttonPanel.add(agreeButton);
        
        JButton declineButton = new JButton(declineText);
        declineButton.setFont(new Font(Font.DIALOG, Font.PLAIN, (int)baseSize));
        declineButton.setPreferredSize(new Dimension((int)(120 * scaleFactor), (int)(38 * scaleFactor)));
        declineButton.setBackground(new Color(245, 245, 245));
        declineButton.setForeground(new Color(100, 100, 100));
        declineButton.setFocusPainted(false);
        declineButton.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        declineButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        declineButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { declineButton.setBackground(new Color(235, 235, 235)); }
            public void mouseExited(java.awt.event.MouseEvent evt) { declineButton.setBackground(new Color(245, 245, 245)); }
        });
        declineButton.addActionListener(e -> {
            result[0] = 1;
            latch.countDown();
            frame.dispose();
        });
        buttonPanel.add(declineButton);
        
        // Handle window close button (X) - treat as decline
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                latch.countDown();
            }
        });
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        frame.add(mainPanel);
        frame.setVisible(true);
        
        try {
            latch.await(); // Wait for user interaction
        } catch (InterruptedException ignored) {}
        
        return result[0] == 0;
    }
    
    /**
     * Static helper version of getPreferredFont for use before ShutterSoundGUI instance is created
     */
    private static Font getPreferredFontStatic(float baseSize) {
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (!osName.contains("win")) {
            return null;
        }

        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        
        String[] preferredFontNames;
        
        if (language.equals("ko")) {
            preferredFontNames = new String[]{"맑은 고딕", "Malgun Gothic", "Segoe UI Symbol", "Segoe UI Emoji", "Noto Sans CJK KR", "굴림", "Gulim"};
        } else if (language.equals("ja")) {
            preferredFontNames = new String[]{"Meiryo", "Meiryo UI", "Yu Gothic", "Segoe UI Symbol", "Noto Sans CJK", "Noto Sans"};
        } else if (language.equals("zh")) {
            preferredFontNames = new String[]{"Microsoft YaHei", "SimHei", "Segoe UI Symbol", "Noto Sans CJK SC", "Noto Sans CJK"};
        } else {
            preferredFontNames = new String[]{"Segoe UI", "Arial", "Segoe UI Symbol", "Noto Sans", "Liberation Sans", "DejaVu Sans", "Consolas"};
        }
        
        String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        
        for (String preferredName : preferredFontNames) {
            for (String availableName : availableFonts) {
                if (availableName.equalsIgnoreCase(preferredName)) {
                    return new Font(preferredName, Font.PLAIN, (int) baseSize);
                }
            }
        }
        return null;
    }

    /**
     * Custom ResourceBundle.Control to support UTF-8 encoded property files (for Java 8 compatibility)
     */
    public static class UTF8Control extends ResourceBundle.Control {
        public ResourceBundle newBundle(String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            ResourceBundle bundle = null;
            java.io.InputStream stream = null;
            if (reload) {
                java.net.URL url = loader.getResource(resourceName);
                if (url != null) {
                    java.net.URLConnection connection = url.openConnection();
                    if (connection != null) {
                        connection.setUseCaches(false);
                        stream = connection.getInputStream();
                    }
                }
            } else {
                stream = loader.getResourceAsStream(resourceName);
            }
            if (stream != null) {
                try {
                    bundle = new PropertyResourceBundle(new java.io.InputStreamReader(stream, "UTF-8"));
                } finally {
                    stream.close();
                }
            }
            return bundle;
        }
    }

    public ShutterSoundGUI() {
        // Default language setting (system language, otherwise Korean)
        bundle = loadBundle(null);

        // Use System Look and Feel for a more native appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Calculate Scale Factor based on system font size (base 12)
        Font systemFont = UIManager.getFont("Label.font");
        float baseSize = (systemFont != null) ? systemFont.getSize2D() : 12.0f;
        this.scaleFactor = baseSize / 12.0f;
        
        // Override with preferred fonts using the static helper method
        Font preferredFont = getPreferredFontStatic(baseSize);
        if (preferredFont != null) {
            systemFont = preferredFont;
            // Apply preferred font to all UIManager components for Korean text support
            setUIFont(preferredFont);
        }

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
        headerLabel.setForeground(COLOR_TEXT_MAIN);
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, scale(10), 0);
        panel.add(headerLabel, gbc);

        // Status Label (Current Step)
        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(systemFont != null ? systemFont.deriveFont(baseSize * 1.15f) : new Font(Font.DIALOG, Font.PLAIN, scale(14)));
        statusLabel.setForeground(COLOR_TEXT_SUB);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, scale(15), 0);
        panel.add(statusLabel, gbc);

        // Progress Bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(progressBar.getPreferredSize().width, scale(8)));
        progressBar.setForeground(COLOR_ACCENT); 
        progressBar.setBackground(new Color(230, 230, 230));
        progressBar.setBorderPainted(false);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, scale(15), 0);
        panel.add(progressBar, gbc);

        // Log Area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, scale(12)));
        logArea.setBackground(COLOR_BG_LOG);
        logArea.setBorder(BorderFactory.createEmptyBorder(scale(5), scale(5), scale(5), scale(5)));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(scale(400), scale(150)));
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
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
        langButton.setPreferredSize(new Dimension(scale(80), scale(30)));
        langButton.setBackground((new Color(245, 245, 245)));
        langButton.setForeground(new Color(100, 100, 100));
        langButton.setFocusPainted(false);
        langButton.setFocusable(false);
        langButton.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
        langButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        langButton.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { langButton.setBackground(new Color(235, 235, 235)); }
            public void mouseExited(java.awt.event.MouseEvent evt) { langButton.setBackground(new Color(245, 245, 245)); }
        });
        langButton.addActionListener(e -> toggleLanguage());
        bottomPanel.add(langButton);

        // Donate Button
        donateButton = new JButton("");
        donateButton.setFont(systemFont != null ? systemFont.deriveFont(baseSize) : new Font(Font.DIALOG, Font.PLAIN, scale(12)));
        donateButton.setForeground(COLOR_ACCENT);
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
        bundle = loadBundle(bundle.getLocale().getLanguage().equals("ko") ? Locale.ENGLISH : Locale.KOREAN);
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
            // Convert fileName (e.g., "donate_npay.png") to base64 file (e.g., "donate_npay.b64")
            String base64FileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".b64";
            
            // Load Base64 encoded file from resources/folder
            InputStream is = getClass().getResourceAsStream("/resources/" + base64FileName);
            if (is == null) is = getClass().getResourceAsStream("resources/" + base64FileName);
            
            if (is != null) {
                // Read Base64 string from file
                String base64String = new String(is.readAllBytes()).trim();
                
                // Decode Base64 to byte array
                byte[] imageBytes = java.util.Base64.getDecoder().decode(base64String);
                
                // Create Image from byte array
                Image img = new ImageIcon(imageBytes).getImage();
                
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
        private static final String OS_IDENTIFIER_WINDOWS = "win";
        private static final String OS_IDENTIFIER_LINUX = "linux";
        private static final String RESOURCE_FOLDER_WINDOWS = "/adb-windows/";
        private static final String RESOURCE_FOLDER_LINUX = "/adb-linux/";
        private static final String ADB_EXECUTABLE_WINDOWS = "adb.exe";
        private static final String ADB_EXECUTABLE_LINUX = "adb";

        // ===== Constants: Device Detection (USB Scanning) =====
        private static final String STEP_1_MESSAGE_SCANNING_LINUX = "[STEP 1] Scanning USB ports for Samsung devices (lsusb)...";
        private static final String STEP_1_MESSAGE_SCANNING_WINDOWS = "[STEP 1] Scanning USB ports for Samsung devices (PowerShell)...";
        private static final String LSUSB_SAMSUNG_PATTERN = "ID ([0-9a-fA-F]{4}:[0-9a-fA-F]{4}) Samsung";
        private static final String ISERIAL_PATTERN = "iSerial\\s+\\d+\\s+(\\S+)";
        private static final String SAMSUNG_VENDOR_ID = "VID_04E8";
        private static final String DEVICE_DETECTED_MESSAGE = "> Detected Samsung physical device serial: ";
        private static final String NO_SAMSUNG_DEVICES_MESSAGE = "> No Samsung USB devices detected via lsusb.";
        private static final String FOUND_SAMSUNG_DEVICES_MESSAGE = "> Found ";
        private static final String SAMSUNG_DEVICES_ON_USB_MESSAGE = " Samsung device(s) on USB bus. Extracting serials...";

        // ===== Constants: ADB Communication =====
        private static final String STEP_2_MESSAGE_ADB_CHECK = "[STEP 2] Checking ADB connectivity and USB debugging status...";
        private static final String ADB_COMMAND_DEVICES = "devices";
        private static final String ADB_DEVICES_EMPTY_RESPONSE = "List of devices attached";
        private static final String ADB_DEVICES_OUTPUT_HEADER = "adb devices output:\n";
        private static final String ADB_AUTHORIZED_DEVICE_PATTERN = "(?m)^(\\S+)\\tdevice$";
        private static final String DEVICE_STATE_AUTHORIZED = "\tdevice";
        private static final String DEVICE_STATE_UNAUTHORIZED = "\tunauthorized";
        private static final String DEVICE_PREFIX_MESSAGE = "Device ";
        private static final String DEVICE_AUTHORIZED_SUFFIX = " is authorized and ready.";
        private static final String DEVICE_UNAUTHORIZED_SUFFIX = " found but UNAUTHORIZED. Check phone screen.";
        private static final String DEVICE_UNEXPECTED_STATE_SUFFIX = " found in unexpected state.";
        private static final String AUTHORIZED_DEVICE_FOUND_MESSAGE = "Authorized device found: ";
        private static final String RETRY_MESSAGE = "Retrying in 5 seconds... (";
        private static final int DEVICE_CHECK_MAX_RETRIES = 12;
        private static final long RETRY_INTERVAL_MS = 5000;

        // ===== Constants: Shutter Sound Settings =====
        private static final String SHUTTER_SOUND_SETTING_KEY = "csc_pref_camera_forced_shutter_sound_key";
        private static final String SHUTTER_SOUND_VALUE_ENABLED = "1";
        private static final String SHUTTER_SOUND_VALUE_DISABLED = "0";
        private static final String CHECKING_SHUTTER_SOUND_MESSAGE = "Checking shutter sound setting for ";
        private static final String DISABLING_SHUTTER_SOUND_MESSAGE = "Disabling shutter sound on ";
        private static final String SUCCESS_SUFFIX = " -> SUCCESS: Shutter sound disabled.";
        private static final String ERROR_SUFFIX = " -> ERROR: Failed to update (Value: ";
        private static final String ALREADY_DISABLED_SUFFIX = " -> DONE: Already disabled.";
        private static final String UNKNOWN_STATUS_SUFFIX = " -> FAILED: Could not read setting (";

        // ===== Constants: Result Messages =====
        private static final String STEP_FINISH_PREFIX = "[FINISH] ";
        private static final String RESULT_FINISHED_HEADER = "Finished: \n";
        private static final String RESULT_TIMEOUT_WITH_DEVICE = "Timeout: Samsung device is connected via USB, but USB Debugging is not enabled or authorized.";
        private static final String RESULT_TIMEOUT_NO_DEVICE = "Timeout: No authorized device found.";
        private static final String UNSUPPORTED_OS_MESSAGE = "Unsupported Operating System: ";
        private static final String UNPACKING_ADB_MESSAGE = "Unpacking ADB tools...";

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
            boolean isLinux = os.contains(OS_IDENTIFIER_LINUX);
            boolean isWindows = os.contains(OS_IDENTIFIER_WINDOWS);

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
            if (os.contains(OS_IDENTIFIER_LINUX)) {
                return getLinuxSamsungSerials();
            } else if (os.contains(OS_IDENTIFIER_WINDOWS)) {
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
                CommandResult adbDevicesResult = executeCommand(adbExecutable.toString(), ADB_COMMAND_DEVICES);
                String adbDevicesOutput = adbDevicesResult.stdout.trim();

                // Log the raw adb devices output
                if (adbDevicesOutput.equals(ADB_DEVICES_EMPTY_RESPONSE)) {
                    publish("adb devices: No device found via ADB.");
                } else {
                    publish(ADB_DEVICES_OUTPUT_HEADER + adbDevicesOutput);
                }

                if (!physicalSerials.isEmpty()) {
                    // Precise check: Match physical Samsung devices with authorized ones in ADB
                    for (String serial : physicalSerials) {
                        if (adbDevicesOutput.contains(serial + DEVICE_STATE_AUTHORIZED)) {
                            if (!authorizedSerials.contains(serial)) {
                                authorizedSerials.add(serial);
                                publish(DEVICE_PREFIX_MESSAGE + serial + DEVICE_AUTHORIZED_SUFFIX);
                            }
                            deviceAuthorized = true;
                        } else if (adbDevicesOutput.contains(serial + DEVICE_STATE_UNAUTHORIZED)) {
                            publish(DEVICE_PREFIX_MESSAGE + serial + DEVICE_UNAUTHORIZED_SUFFIX);
                        } else if (adbDevicesOutput.contains(serial)) {
                            publish(DEVICE_PREFIX_MESSAGE + serial + DEVICE_UNEXPECTED_STATE_SUFFIX);
                        } else {
                            // connected physically but not appearing in adb yet
                        }
                    }
                } else {
                    // Fallback: Just look for any authorized device
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(ADB_AUTHORIZED_DEVICE_PATTERN).matcher(adbDevicesOutput);
                    while (m.find()) {
                        String serial = m.group(1);
                        if (!authorizedSerials.contains(serial)) {
                            authorizedSerials.add(serial);
                            publish(AUTHORIZED_DEVICE_FOUND_MESSAGE + serial);
                        }
                        deviceAuthorized = true;
                    }
                }

                if (deviceAuthorized) break;

                if (i < DEVICE_CHECK_MAX_RETRIES - 1) {
                    publish(RETRY_MESSAGE + (i + 1) + "/" + DEVICE_CHECK_MAX_RETRIES + ")");
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
                // Get device information
                String deviceInfo = getDeviceInfo(targetSerial);
                publish("Device: " + targetSerial + " [" + deviceInfo + "]");
                publish(CHECKING_SHUTTER_SOUND_MESSAGE + targetSerial + "...");

                CommandResult getSoundResult = executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "get", "system", SHUTTER_SOUND_SETTING_KEY);
                String currentSetting = getSoundResult.stdout.trim();

                // Build the device prefix for the final result
                String devicePrefix = String.format("[%s] (%s)", targetSerial, deviceInfo);

                if (SHUTTER_SOUND_VALUE_ENABLED.equals(currentSetting)) {
                    publish(DISABLING_SHUTTER_SOUND_MESSAGE + targetSerial + "...");
                    executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "put", "system", SHUTTER_SOUND_SETTING_KEY, SHUTTER_SOUND_VALUE_DISABLED);
                    CommandResult verifyResult = executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "get", "system", SHUTTER_SOUND_SETTING_KEY);
                    if (SHUTTER_SOUND_VALUE_DISABLED.equals(verifyResult.stdout.trim())) {
                        finalResult.append(devicePrefix).append(SUCCESS_SUFFIX).append("\n");
                    } else {
                        finalResult.append(devicePrefix).append(ERROR_SUFFIX).append(verifyResult.stdout.trim()).append(")\n");
                    }
                } else if (SHUTTER_SOUND_VALUE_DISABLED.equals(currentSetting)) {
                    finalResult.append(devicePrefix).append(ALREADY_DISABLED_SUFFIX).append("\n");
                } else {
                    // Filter out ADB error messages from the setting value
                    String cleanStatus = currentSetting.length() > 50 ? currentSetting.substring(0, 47) + "..." : currentSetting;
                    if (cleanStatus.contains("not found") || cleanStatus.contains("error")) {
                        cleanStatus = "Connection error";
                    }
                    finalResult.append(devicePrefix).append(UNKNOWN_STATUS_SUFFIX).append(cleanStatus).append(")\n");
                }
            }

            return finalResult.toString().trim();
        }

        /**
         * Get device model and brand information
         * @param serial Device serial number
         * @return Device information string
         */
        private String getDeviceInfo(String serial) {
            try {
                CommandResult modelResult = executeCommand(adbExecutable.toString(), "-s", serial, "shell", "getprop", "ro.product.model");
                CommandResult brandResult = executeCommand(adbExecutable.toString(), "-s", serial, "shell", "getprop", "ro.product.brand");
                
                String model = modelResult.stdout.trim();
                String brand = brandResult.stdout.trim();
                
                // If ADB returns an error message instead of a property, ignore it
                if (model.toLowerCase().contains("error") || model.toLowerCase().contains("not found") || model.isEmpty()) {
                    return "Unknown Device";
                }
                
                if (!model.isEmpty() && !brand.isEmpty()) {
                    return brand + " " + model;
                } else {
                    return model.isEmpty() ? "Unknown Device" : model;
                }
            } catch (Exception e) {
                return "Unknown Device";
            }
        }

        private List<String> getLinuxSamsungSerials() {
            List<String> serials = new java.util.ArrayList<>();
            try {
                publish(STEP_1_MESSAGE_SCANNING_LINUX);
                // 1. Find Samsung device IDs
                CommandResult lsusbResult = executeCommand("lsusb");
                java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile(LSUSB_SAMSUNG_PATTERN);
                java.util.regex.Matcher idMatcher = idPattern.matcher(lsusbResult.stdout);
                
                java.util.List<String> ids = new java.util.ArrayList<>();
                while (idMatcher.find()) {
                    ids.add(idMatcher.group(1));
                }

                if (ids.isEmpty()) {
                    publish(NO_SAMSUNG_DEVICES_MESSAGE);
                    return serials;
                }

                publish(FOUND_SAMSUNG_DEVICES_MESSAGE + ids.size() + SAMSUNG_DEVICES_ON_USB_MESSAGE);

                // 2. Get iSerial for each Samsung ID
                for (String id : ids) {
                    CommandResult vResult = executeCommand("lsusb", "-v", "-d", id);
                    java.util.regex.Pattern serialPattern = java.util.regex.Pattern.compile(ISERIAL_PATTERN);
                    java.util.regex.Matcher serialMatcher = serialPattern.matcher(vResult.stdout);
                    if (serialMatcher.find()) {
                        String serial = serialMatcher.group(1);
                        if (!serials.contains(serial)) {
                            publish(DEVICE_DETECTED_MESSAGE + serial);
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
                publish(STEP_1_MESSAGE_SCANNING_WINDOWS);
                // Get InstanceId of present devices with Samsung's Vendor ID (04E8)
                CommandResult psResult = executeCommand("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", 
                    "Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -match '" + SAMSUNG_VENDOR_ID + "' } | Select-Object -ExpandProperty InstanceId");
                
                String[] lines = psResult.stdout.split("\\r?\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // Diesel-level candidate logs for debugging, not user-facing UI messages
                    System.out.println("[DEBUG] Samsung candidate instance id: " + line);

                    int lastBackslash = line.lastIndexOf('\\');
                    if (lastBackslash != -1 && lastBackslash < line.length() - 1) {
                        String serial = line.substring(lastBackslash + 1);
                        // Clean up composite device suffix if present
                        if (serial.contains("&")) {
                            serial = serial.split("&")[0];
                        }
                        // Ignore numeric-only entries like '7', which are not serials
                        if (serial.matches("[0-9]+")) continue;
                        // Ignore too-short strings unlikely to be real device serials
                        if (serial.length() < 6) continue;

                        if (!serials.contains(serial)) {
                            publish(DEVICE_DETECTED_MESSAGE + serial);
                            serials.add(serial);
                        }
                    }
                }
                // If no serials are extracted, note it (user-facing) and keep debug detail for candidates
                if (serials.isEmpty()) {
                    publish("> Warning: No valid Samsung device serials extracted from candidates.");
                }
            } catch (Exception e) {
                publish("Warning: Could not check physical USB devices (PowerShell error). " + e.getMessage());
            }
            return serials;
        }

        @Override
        protected String doInBackground() throws Exception {
            // Step 1: Initialize ADB environment based on OS
            AdbEnvironment env = initializeAdbEnvironment();
            if (env == null) {
                String os = System.getProperty("os.name").toLowerCase();
                return UNSUPPORTED_OS_MESSAGE + os;
            }

            // Step 2: Extract ADB tools
            System.out.println("[DEBUG]"+ UNPACKING_ADB_MESSAGE);
            Path tempDir = unpackAdb(env.resourceFolder, env.fileList);
            adbExecutable = tempDir.resolve(env.executableName);

            // Step 3: Detect physically connected Samsung devices
            List<String> physicalSerials = detectPhysicalDevices();
            if (physicalSerials.isEmpty()) {
                return "No Samsung devices found.";
            }

            // Step 4: Wait for ADB to authorize devices
            publish(STEP_2_MESSAGE_ADB_CHECK);
            List<String> authorizedSerials = waitForAdbAuthorization(physicalSerials);

            // Step 5: Disable shutter sound on authorized devices and report results
            if (!authorizedSerials.isEmpty()) {
                String resultDetails = disableShutterSoundOnDevices(authorizedSerials);
                return RESULT_FINISHED_HEADER + resultDetails;
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
                statusLabel.setText(message.contains("\n") ? message.split("\n")[0] + " ..." : message);
                appendLog("> " + message, getTimestamp());
            }
        }

        @Override
        protected void done() {
            // Task is complete
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            
            try {
                String finalStatus = get();
                statusLabel.setText(finalStatus);
                appendLog(STEP_FINISH_PREFIX + finalStatus, getTimestamp());
            } catch (InterruptedException | ExecutionException e) {
                String errorMsg = "Error: " + e.getCause().getMessage();
                statusLabel.setText(errorMsg);
                appendLog("[ERROR] " + errorMsg, getTimestamp());
            }
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

    /**
     * Checks if the current process has administrator privileges (Windows only).
     */
    private static boolean isAdmin() {
        try {
            // 'net session' command requires admin privileges to succeed
            ProcessBuilder pb = new ProcessBuilder("net", "session");
            Process process = pb.start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Relaunches the current JAR file with administrator privileges using PowerShell.
     */
    private static void runAsAdmin() throws Exception {
        String jarPath = ShutterSoundGUI.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
        // Clean up path for Windows (remove leading slash if present)
        if (jarPath.startsWith("/") && jarPath.contains(":")) {
            jarPath = jarPath.substring(1);
        }
        
        // Use ProcessBuilder to request elevation via PowerShell
        ProcessBuilder pb = new ProcessBuilder(
            "powershell.exe", 
            "-NoProfile", 
            "-Command", 
            "Start-Process javaw -ArgumentList '-jar \"\"" + jarPath + "\"\"' -Verb RunAs"
        );
        pb.start();
    }

    public static void main(String[] args) {
        // Load ResourceBundle early for localized error messages
        ResourceBundle bundle;
        try {
            bundle = ResourceBundle.getBundle("messages", new UTF8Control());
        } catch (Exception e) {
            bundle = ResourceBundle.getBundle("messages", Locale.KOREAN, new UTF8Control());
        }

        // Windows privilege check and elevation attempt
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win") && !isAdmin()) {
            try {
                String path = ShutterSoundGUI.class.getProtectionDomain().getCodeSource().getLocation().getPath();
                if (path.toLowerCase().endsWith(".jar")) {
                    // Only attempt auto-elevation if running from a JAR file
                    runAsAdmin();
                    System.exit(0);
                } else {
                    System.out.println("Running from class files. Skipping auto-elevation.");
                }
            } catch (Exception e) {
                System.err.println("Notice: Could not elevate to administrator. " + e.getMessage());
            }
        }

        // Show disclaimer first (bundle already loaded above)
        if (!showDisclaimerDialog(bundle)) {            System.out.println("User declined the disclaimer. Exiting.");
            System.exit(0);
        }
        
        // Run the GUI on the Event Dispatch Thread
        SwingUtilities.invokeLater(() -> {
            ShutterSoundGUI gui = new ShutterSoundGUI();
            gui.setVisible(true);
            gui.startProcess();
        });
    }
}