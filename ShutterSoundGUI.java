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
import javax.swing.plaf.basic.BasicScrollBarUI;

public class ShutterSoundGUI extends JFrame {

    // ===== UI Constants: Dynamic Colors (Updated via detectDarkMode) =====
    private static Color COLOR_BG_PANEL = Color.WHITE;
    private static Color COLOR_TEXT_MAIN = new Color(33, 33, 33);
    private static Color COLOR_TEXT_SUB = new Color(66, 66, 66);
    private static final Color COLOR_ACCENT = new Color(0, 120, 215);
    private static Color COLOR_BG_LOG = new Color(245, 245, 245);
    private static Color COLOR_BORDER = new Color(220, 220, 220);
    private static boolean isDarkMode = false;

    // ===== Environment Helpers =====
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea logArea;
    private JLabel headerLabel;
    private JButton donateButton;
    private JButton langButton;
    private ResourceBundle bundle;
    private float scaleFactor;

    /**
     * Detects if Windows is in Dark Mode and updates colors
     */
    private static void detectDarkMode() {
        if (!IS_WINDOWS) return;
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", 
                "Get-ItemProperty -Path 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize' -Name AppsUseLightTheme | Select-Object -ExpandProperty AppsUseLightTheme");
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String result = r.readLine();
            if (result != null && result.trim().equals("0")) {
                isDarkMode = true;
                COLOR_BG_PANEL = new Color(32, 32, 32);
                COLOR_TEXT_MAIN = new Color(240, 240, 240);
                COLOR_TEXT_SUB = new Color(180, 180, 180);
                COLOR_BG_LOG = new Color(45, 45, 45);
                COLOR_BORDER = new Color(60, 60, 60);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Unified button factory for consistent styling
     */
    private static JButton createStyledButton(String text, Dimension size, boolean isPrimary, float scale) {
        JButton btn = new JButton(text);
        btn.setPreferredSize(size);
        btn.setFocusPainted(false);
        btn.setFocusable(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Color bg = isPrimary ? COLOR_ACCENT : (isDarkMode ? new Color(55, 55, 55) : new Color(245, 245, 245));
        Color fg = isPrimary ? Color.WHITE : COLOR_TEXT_MAIN;
        Color border = isPrimary ? COLOR_ACCENT : COLOR_BORDER;

        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setBorder(isPrimary ? BorderFactory.createEmptyBorder() : BorderFactory.createLineBorder(border));

        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) { btn.setBackground(isPrimary ? COLOR_ACCENT.brighter() : (isDarkMode ? bg.brighter() : new Color(235, 235, 235))); }
            public void mouseExited(java.awt.event.MouseEvent evt) { btn.setBackground(bg); }
        });
        return btn;
    }

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
        
        message = message.replace("\\n", "\n");
        
        Font systemFont = UIManager.getFont("Label.font");
        float baseSize = (systemFont != null) ? systemFont.getSize2D() : 12.0f;
        float scaleFactor = baseSize / 12.0f;
        
        Font preferredFont = getPreferredFontStatic(baseSize);
        if (preferredFont != null) systemFont = preferredFont;
        
        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        final int[] result = {1}; 
        
        JFrame frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize((int)(550 * scaleFactor), (int)(400 * scaleFactor));
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);

        // Handle native close button (X) - exit process
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                System.exit(0);
            }
        });
        
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(COLOR_BG_PANEL);
        mainPanel.setBorder(BorderFactory.createEmptyBorder((int)(15 * scaleFactor), (int)(20 * scaleFactor), 
                                                              (int)(15 * scaleFactor), (int)(20 * scaleFactor)));
        
        JTextArea messageArea = new JTextArea(message);
        messageArea.setEditable(false);
        messageArea.setFocusable(false);
        messageArea.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setFont(new Font(Font.DIALOG, Font.PLAIN, (int)baseSize));
        messageArea.setForeground(COLOR_TEXT_MAIN);
        messageArea.setBackground(COLOR_BG_PANEL);
        messageArea.setBorder(BorderFactory.createEmptyBorder((int)(10 * scaleFactor), 0, (int)(10 * scaleFactor), 0));
        
        JScrollPane scrollPane = new JScrollPane(messageArea);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, (int)(10 * scaleFactor), 0));
        buttonPanel.setOpaque(false);
        
        JButton agreeButton = createStyledButton(agreeText, new Dimension((int)(120 * scaleFactor), (int)(38 * scaleFactor)), true, scaleFactor);
        agreeButton.setFont(new Font(Font.DIALOG, Font.PLAIN, (int)baseSize));
        agreeButton.addActionListener(e -> {
            result[0] = 0;
            latch.countDown();
            frame.dispose();
        });
        buttonPanel.add(agreeButton);
        
        JButton declineButton = createStyledButton(declineText, new Dimension((int)(120 * scaleFactor), (int)(38 * scaleFactor)), false, scaleFactor);
        declineButton.setFont(new Font(Font.DIALOG, Font.PLAIN, (int)baseSize));
        declineButton.addActionListener(e -> {
            result[0] = 1;
            latch.countDown();
            frame.dispose();
        });
        buttonPanel.add(declineButton);
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        frame.add(mainPanel);
        frame.setVisible(true);
        
        try {
            latch.await(); 
        } catch (InterruptedException ignored) {}
        
        return result[0] == 0;
    }
    
    private static Font getPreferredFontStatic(float baseSize) {
        String osName = System.getProperty("os.name").toLowerCase();
        if (!osName.contains("win")) return null;

        Locale locale = Locale.getDefault();
        String language = locale.getLanguage();
        String[] preferredFontNames;
        
        if (language.equals("ko")) preferredFontNames = new String[]{"맑은 고딕", "Malgun Gothic", "Segoe UI Symbol", "Segoe UI Emoji", "Noto Sans CJK KR", "굴림", "Gulim"};
        else if (language.equals("ja")) preferredFontNames = new String[]{"Meiryo", "Meiryo UI", "Yu Gothic", "Segoe UI Symbol", "Noto Sans CJK", "Noto Sans"};
        else if (language.equals("zh")) preferredFontNames = new String[]{"Microsoft YaHei", "SimHei", "Segoe UI Symbol", "Noto Sans CJK SC", "Noto Sans CJK"};
        else preferredFontNames = new String[]{"Segoe UI", "Arial", "Segoe UI Symbol", "Noto Sans", "Liberation Sans", "DejaVu Sans", "Consolas"};
        
        String[] availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String preferredName : preferredFontNames) {
            for (String availableName : availableFonts) {
                if (availableName.equalsIgnoreCase(preferredName)) return new Font(preferredName, Font.PLAIN, (int) baseSize);
            }
        }
        return null;
    }

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
        bundle = loadBundle(null);
        setTitle(bundle.getString("window.title"));
        Font systemFont = UIManager.getFont("Label.font");
        float baseSize = (systemFont != null) ? systemFont.getSize2D() : 12.0f;
        this.scaleFactor = baseSize / 12.0f;
        
        Font preferredFont = getPreferredFontStatic(baseSize);
        if (preferredFont != null) {
            systemFont = preferredFont;
            setUIFont(preferredFont);
        }

        setSize(scale(700), scale(500)); 
        setMinimumSize(new Dimension(scale(650), scale(480)));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(COLOR_BG_PANEL);
        panel.setBorder(BorderFactory.createEmptyBorder(scale(20), scale(30), scale(20), scale(30)));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0;
        gbc.weightx = 1.0;

        headerLabel = new JLabel("", SwingConstants.CENTER);
        headerLabel.setFont(systemFont != null ? systemFont.deriveFont(Font.BOLD, baseSize * 1.5f) : new Font(Font.DIALOG, Font.BOLD, scale(18)));
        headerLabel.setForeground(COLOR_TEXT_MAIN);
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, scale(10), 0);
        panel.add(headerLabel, gbc);

        statusLabel = new JLabel("", SwingConstants.CENTER);
        statusLabel.setFont(systemFont != null ? systemFont.deriveFont(baseSize * 1.15f) : new Font(Font.DIALOG, Font.PLAIN, scale(14)));
        statusLabel.setForeground(COLOR_TEXT_SUB);
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, scale(15), 0);
        panel.add(statusLabel, gbc);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(progressBar.getPreferredSize().width, scale(8)));
        progressBar.setForeground(COLOR_ACCENT); 
        progressBar.setBackground(isDarkMode ? new Color(60, 60, 60) : new Color(230, 230, 230));
        progressBar.setBorderPainted(false);
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, scale(15), 0);
        panel.add(progressBar, gbc);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, scale(12)));
        logArea.setBackground(COLOR_BG_LOG);
        logArea.setForeground(COLOR_TEXT_MAIN);
        logArea.setBorder(BorderFactory.createEmptyBorder(scale(5), scale(5), scale(5), scale(5)));
        
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(scale(400), scale(150)));
        scrollPane.setBorder(BorderFactory.createLineBorder(COLOR_BORDER));
        scrollPane.getViewport().setBackground(COLOR_BG_LOG);

        class CustomScrollBarUI extends BasicScrollBarUI {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = isDarkMode ? new Color(70, 70, 70) : new Color(200, 200, 200);
                this.trackColor = COLOR_BG_LOG;
            }
            @Override protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }
            @Override protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }
            private JButton createZeroButton() {
                JButton jb = new JButton();
                jb.setPreferredSize(new Dimension(0, 0));
                return jb;
            }
            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(thumbColor);
                g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2, thumbBounds.width - 4, thumbBounds.height - 4, 5, 5);
                g2.dispose();
            }
        }

        scrollPane.getVerticalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getVerticalScrollBar().setPreferredSize(new Dimension(scale(10), 0));
        scrollPane.getHorizontalScrollBar().setUI(new CustomScrollBarUI());
        scrollPane.getHorizontalScrollBar().setPreferredSize(new Dimension(0, scale(10)));
        
        gbc.gridy = 3;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(scrollPane, gbc);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, scale(10), 0));
        bottomPanel.setBackground(COLOR_BG_PANEL);

        langButton = createStyledButton("KO | EN", new Dimension(scale(80), scale(30)), false, scaleFactor);
        langButton.setFont(systemFont != null ? systemFont.deriveFont(baseSize) : new Font(Font.DIALOG, Font.PLAIN, scale(12)));
        langButton.addActionListener(e -> toggleLanguage());
        bottomPanel.add(langButton);

        donateButton = createStyledButton("", new Dimension(scale(100), scale(30)), false, scaleFactor);
        donateButton.setFont(systemFont != null ? systemFont.deriveFont(baseSize) : new Font(Font.DIALOG, Font.PLAIN, scale(12)));
        donateButton.setForeground(COLOR_ACCENT);
        donateButton.addActionListener(e -> showDonateDialog());
        bottomPanel.add(donateButton);
        
        gbc.gridy = 4;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(scale(10), 0, 0, 0);
        panel.add(bottomPanel, gbc);

        add(panel);
        updateTexts();
    }

    private void updateTexts() {
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
        dialog.setLocationRelativeTo(this);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(COLOR_BG_PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(COLOR_BG_PANEL);
        tabbedPane.setForeground(COLOR_TEXT_MAIN);
        
        tabbedPane.addTab(bundle.getString("ui.tab.naver"), createImageLabel("donate_npay.png"));
        tabbedPane.addTab(bundle.getString("ui.tab.toss"), createImageLabel("donate_toss.png"));

        root.add(tabbedPane, BorderLayout.CENTER);
        
        JButton closeBtn = createStyledButton("Close", new Dimension(scale(100), scale(35)), true, scaleFactor);
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel bp = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bp.setBackground(COLOR_BG_PANEL);
        bp.add(closeBtn);
        root.add(bp, BorderLayout.SOUTH);

        dialog.add(root);
        dialog.setVisible(true);
    }

    private JComponent createImageLabel(String fileName) {
        try {
            String base64FileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".b64";
            InputStream is = getClass().getResourceAsStream("/resources/" + base64FileName);
            if (is == null) is = getClass().getResourceAsStream("resources/" + base64FileName);
            if (is != null) {
                String base64String = new String(is.readAllBytes()).trim();
                byte[] imageBytes = java.util.Base64.getDecoder().decode(base64String);
                Image img = new ImageIcon(imageBytes).getImage();
                JPanel panel = new JPanel() {
                    @Override
                    protected void paintComponent(Graphics g) {
                        super.paintComponent(g);
                        if (img == null) return;
                        double ratio = Math.min((double) getWidth() / img.getWidth(this), (double) getHeight() / img.getHeight(this));
                        int nw = (int) (img.getWidth(this) * ratio);
                        int nh = (int) (img.getHeight(this) * ratio);
                        g.drawImage(img, (getWidth() - nw) / 2, (getHeight() - nh) / 2, nw, nh, this);
                    }
                };
                panel.setBackground(COLOR_BG_PANEL);
                return panel;
            }
        } catch (Exception ignored) {}
        return new JLabel(bundle.getString("ui.error.image_load") + fileName, SwingConstants.CENTER);
    }

    public void startProcess() {
        AdbWorker worker = new AdbWorker();
        worker.execute();
    }

    private class AdbWorker extends SwingWorker<String, String> {
        private final String STEP_1_MESSAGE_SCANNING = IS_WINDOWS 
            ? "[STEP 1] Scanning USB ports for Samsung devices (PowerShell)..."
            : "[STEP 1] Scanning USB ports for Samsung devices (sysfs)...";
        private static final String SAMSUNG_VENDOR_ID = "04E8";
        private static final String DEVICE_DETECTED_MESSAGE = "> Detected Samsung physical device serial: ";
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
        private static final String AUTHORIZED_DEVICE_FOUND_MESSAGE = "Authorized device found: ";
        private static final String RETRY_MESSAGE = "Retrying in 5 seconds... (";
        private static final int DEVICE_CHECK_MAX_RETRIES = 12;
        private static final long RETRY_INTERVAL_MS = 5000;
        private static final String SHUTTER_SOUND_SETTING_KEY = "csc_pref_camera_forced_shuttersound_key";
        private static final String SHUTTER_SOUND_VALUE_ENABLED = "1";
        private static final String SHUTTER_SOUND_VALUE_DISABLED = "0";
        private static final String CHECKING_SHUTTER_SOUND_MESSAGE = "Checking shutter sound setting for ";
        private static final String DISABLING_SHUTTER_SOUND_MESSAGE = "Disabling shutter sound on ";
        private static final String SUCCESS_SUFFIX = " -> SUCCESS: Shutter sound disabled.";
        private static final String ERROR_SUFFIX = " -> ERROR: Failed to update (Value: ";
        private static final String ALREADY_DISABLED_SUFFIX = " -> DONE: Already disabled.";
        private static final String UNKNOWN_STATUS_SUFFIX = " -> FAILED: Could not read setting (";
        private static final String STEP_FINISH_PREFIX = "[FINISH] ";
        private static final String RESULT_FINISHED_HEADER = "Finished: \n";
        private static final String RESULT_TIMEOUT_WITH_DEVICE = "Timeout: Samsung device is connected via USB, but USB Debugging is not enabled or authorized.";
        private static final String RESULT_TIMEOUT_NO_DEVICE = "Timeout: No authorized device found.";

        private final List<String> ADB_FILES_WINDOWS = Arrays.asList("adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "etc1tool.exe", "fastboot.exe", "hprof-conv.exe", "libwinpthread-1.dll", "make_f2fs_casefold.exe", "make_f2fs.exe", "mke2fs.conf", "mke2fs.exe", "NOTICE.txt", "source.properties", "sqlite3.exe");
        private final List<String> ADB_FILES_LINUX = Arrays.asList("adb", "etc1tool", "fastboot", "hprof-conv", "make_f2fs", "make_f2fs_casefold", "mke2fs", "mke2fs.conf", "NOTICE.txt", "source.properties", "sqlite3", "lib64/libc++.so");
        private Path adbExecutable;

        private List<String> detectPhysicalDevices() {
            List<String> serials = new java.util.ArrayList<>();
            try {
                publish(STEP_1_MESSAGE_SCANNING);
                if (IS_WINDOWS) {
                    CommandResult psResult = executeCommand("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", "Get-PnpDevice -PresentOnly | Where-Object { $_.InstanceId -match 'VID_" + SAMSUNG_VENDOR_ID + "' } | Select-Object -ExpandProperty InstanceId");
                    String[] lines = psResult.stdout.split("\\r?\\n");
                    for (String line : lines) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        int lastBackslash = line.lastIndexOf('\\');
                        if (lastBackslash != -1 && lastBackslash < line.length() - 1) {
                            String serial = line.substring(lastBackslash + 1);
                            if (serial.contains("&")) serial = serial.split("&")[0];
                            if (serial.matches("[0-9]+")) continue;
                            if (serial.length() < 6) continue;
                            if (!serials.contains(serial)) {
                                publish(DEVICE_DETECTED_MESSAGE + serial);
                                serials.add(serial);
                            }
                        }
                    }
                } else {
                    // Linux: Direct sysfs scan for Samsung devices (VID 04e8)
                    java.io.File usbDevices = new java.io.File("/sys/bus/usb/devices");
                    if (usbDevices.exists() && usbDevices.isDirectory()) {
                        java.io.File[] devices = usbDevices.listFiles();
                        if (devices != null) {
                            for (java.io.File dev : devices) {
                                try {
                                    java.io.File vendorIdFile = new java.io.File(dev, "idVendor");
                                    if (vendorIdFile.exists()) {
                                        String vendorId = Files.readString(vendorIdFile.toPath()).trim();
                                        if (vendorId.equalsIgnoreCase(SAMSUNG_VENDOR_ID)) {
                                            java.io.File serialFile = new java.io.File(dev, "serial");
                                            if (serialFile.exists()) {
                                                String serial = Files.readString(serialFile.toPath()).trim();
                                                if (!serial.isEmpty() && !serials.contains(serial)) {
                                                    publish(DEVICE_DETECTED_MESSAGE + serial);
                                                    serials.add(serial);
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
            return serials;
        }

        private List<String> waitForAdbAuthorization(List<String> physicalSerials) throws InterruptedException, IOException {
            List<String> authorizedSerials = new java.util.ArrayList<>();
            boolean deviceAuthorized = false;
            for (int i = 0; i < DEVICE_CHECK_MAX_RETRIES; i++) {
                CommandResult adbDevicesResult = executeCommand(adbExecutable.toString(), ADB_COMMAND_DEVICES);
                String adbDevicesOutput = adbDevicesResult.stdout.trim();
                if (adbDevicesOutput.equals(ADB_DEVICES_EMPTY_RESPONSE)) publish("adb devices: No device found.");
                else publish(ADB_DEVICES_OUTPUT_HEADER + adbDevicesOutput);

                if (!physicalSerials.isEmpty()) {
                    for (String serial : physicalSerials) {
                        if (adbDevicesOutput.contains(serial + DEVICE_STATE_AUTHORIZED)) {
                            if (!authorizedSerials.contains(serial)) {
                                authorizedSerials.add(serial);
                                publish(DEVICE_PREFIX_MESSAGE + serial + DEVICE_AUTHORIZED_SUFFIX);
                            }
                            deviceAuthorized = true;
                        } else if (adbDevicesOutput.contains(serial + DEVICE_STATE_UNAUTHORIZED)) publish(DEVICE_PREFIX_MESSAGE + serial + DEVICE_UNAUTHORIZED_SUFFIX);
                    }
                } else {
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

        private String disableShutterSoundOnDevices(List<String> authorizedSerials) throws IOException, InterruptedException {
            StringBuilder finalResult = new StringBuilder();
            for (String targetSerial : authorizedSerials) {
                String deviceInfo = getDeviceInfo(targetSerial);
                publish("Device: " + targetSerial + " [" + deviceInfo + "]");
                publish(CHECKING_SHUTTER_SOUND_MESSAGE + targetSerial + "...");
                CommandResult getSoundResult = executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "get", "system", SHUTTER_SOUND_SETTING_KEY);
                String currentSetting = getSoundResult.stdout.trim();
                String devicePrefix = String.format("[%s] (%s)", targetSerial, deviceInfo);
                if (SHUTTER_SOUND_VALUE_ENABLED.equals(currentSetting)) {
                    publish(DISABLING_SHUTTER_SOUND_MESSAGE + targetSerial + "...");
                    executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "put", "system", SHUTTER_SOUND_SETTING_KEY, SHUTTER_SOUND_VALUE_DISABLED);
                    CommandResult verifyResult = executeCommand(adbExecutable.toString(), "-s", targetSerial, "shell", "settings", "get", "system", SHUTTER_SOUND_SETTING_KEY);
                    if (SHUTTER_SOUND_VALUE_DISABLED.equals(verifyResult.stdout.trim())) finalResult.append(devicePrefix).append(SUCCESS_SUFFIX).append("\n");
                    else finalResult.append(devicePrefix).append(ERROR_SUFFIX).append(verifyResult.stdout.trim()).append(")\n");
                } else if (SHUTTER_SOUND_VALUE_DISABLED.equals(currentSetting)) finalResult.append(devicePrefix).append(ALREADY_DISABLED_SUFFIX).append("\n");
                else finalResult.append(devicePrefix).append(UNKNOWN_STATUS_SUFFIX).append(currentSetting).append(")\n");
            }
            return finalResult.toString().trim();
        }

        private String getDeviceInfo(String serial) {
            try {
                String model = executeCommand(adbExecutable.toString(), "-s", serial, "shell", "getprop", "ro.product.model").stdout.trim();
                String brand = executeCommand(adbExecutable.toString(), "-s", serial, "shell", "getprop", "ro.product.brand").stdout.trim();
                return brand + " " + model;
            } catch (Exception e) { return "Unknown Device"; }
        }

        @Override
        protected String doInBackground() throws Exception {
            detectDarkMode();
            Path tempDir = Files.createTempDirectory("adb-gui-temp-");
            String adbFileName = IS_WINDOWS ? "adb.exe" : "adb";
            adbExecutable = tempDir.resolve(adbFileName);
            
            List<String> adbFiles = IS_WINDOWS ? ADB_FILES_WINDOWS : ADB_FILES_LINUX;
            String adbResourcePrefix = IS_WINDOWS ? "/adb-windows/" : "/adb-linux/";

            for (String fileName : adbFiles) {
                try (InputStream is = ShutterSoundGUI.class.getResourceAsStream(adbResourcePrefix + fileName)) {
                    if (is != null) {
                        Path targetPath = tempDir.resolve(fileName);
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        if (!IS_WINDOWS && (fileName.equals("adb") || fileName.contains("mke2fs") || fileName.contains("fastboot"))) {
                            targetPath.toFile().setExecutable(true);
                        }
                    }
                }
            }
            
            List<String> physicalSerials = detectPhysicalDevices();
            if (physicalSerials.isEmpty()) return "No Samsung devices found.";
            publish(STEP_2_MESSAGE_ADB_CHECK);
            List<String> authorizedSerials = waitForAdbAuthorization(physicalSerials);
            if (!authorizedSerials.isEmpty()) return RESULT_FINISHED_HEADER + disableShutterSoundOnDevices(authorizedSerials);
            return physicalSerials.isEmpty() ? RESULT_TIMEOUT_NO_DEVICE : RESULT_TIMEOUT_WITH_DEVICE;
        }

        @Override
        protected void process(List<String> chunks) {
            for (String message : chunks) {
                statusLabel.setText(message.contains("\n") ? message.split("\n")[0] + " ..." : message);
                appendLog("> " + message, getTimestamp());
            }
        }

        @Override
        protected void done() {
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            try {
                String finalStatus = get();
                statusLabel.setText(finalStatus);
                appendLog(STEP_FINISH_PREFIX + finalStatus, getTimestamp());
            } catch (Exception e) {
                statusLabel.setText("Error: " + e.getMessage());
            }
        }

        private String getTimestamp() { return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }

        private CommandResult executeCommand(String... command) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder stdout = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) stdout.append(line).append(System.lineSeparator());
            }
            process.waitFor();
            return new CommandResult(stdout.toString());
        }
        private class CommandResult { final String stdout; CommandResult(String stdout) { this.stdout = stdout; } }
    }

    private static boolean isAdmin() {
        if (!IS_WINDOWS) return true; // Only Windows requires admin check for certain PnP operations
        try {
            Process p = new ProcessBuilder("net", "session").start();
            return p.waitFor() == 0;
        } catch (Exception e) { return false; }
    }

    private static void runAsAdmin() throws Exception {
        java.net.URL location = ShutterSoundGUI.class.getProtectionDomain().getCodeSource().getLocation();
        java.io.File jarFile = new java.io.File(location.toURI());
        String jarPath = jarFile.getAbsolutePath();
        
        // Use javaw.exe (Windowed version) to hide the console window
        String javaExe = System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "javaw.exe";
        
        // PowerShell handles single-quoted strings very well for paths with spaces.
        // We escape any single quotes in the path by doubling them (PowerShell rule).
        String escapedJavaExe = javaExe.replace("'", "''");
        String escapedJarPath = jarPath.replace("'", "''");

        String psCommand = String.format("Start-Process '%s' -ArgumentList '-jar', '%s' -Verb RunAs", escapedJavaExe, escapedJarPath);
        new ProcessBuilder("powershell.exe", "-NoProfile", "-Command", psCommand).start();
    }

    public static void main(String[] args) {
        detectDarkMode();
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        
        ResourceBundle bundle;
        try { bundle = ResourceBundle.getBundle("messages", new UTF8Control()); } 
        catch (Exception e) { bundle = ResourceBundle.getBundle("messages", Locale.KOREAN, new UTF8Control()); }

        // Self-elevation: Only for Windows and only when running as a JAR
        if (IS_WINDOWS && !isAdmin()) {
            try {
                java.net.URL location = ShutterSoundGUI.class.getProtectionDomain().getCodeSource().getLocation();
                java.io.File file = new java.io.File(location.toURI());
                if (file.getName().toLowerCase().endsWith(".jar")) {
                    runAsAdmin();
                    System.exit(0);
                }
            } catch (Exception ignored) {}
        }

        if (!showDisclaimerDialog(bundle)) System.exit(0);
        SwingUtilities.invokeLater(() -> {
            ShutterSoundGUI gui = new ShutterSoundGUI();
            gui.setVisible(true);
            gui.startProcess();
        });
    }
}