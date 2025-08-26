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
import java.util.concurrent.ExecutionException;

public class ShutterSoundGUI extends JFrame {

    private final JLabel statusLabel;
    private final JProgressBar progressBar;

    public ShutterSoundGUI() {
        setTitle("Camera Shutter Sound Disabler");
                setSize(600, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Main panel
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Status Label
        statusLabel = new JLabel("Initializing...", SwingConstants.CENTER);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        panel.add(statusLabel, BorderLayout.CENTER);

        // Progress Bar
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        panel.add(progressBar, BorderLayout.SOUTH);

        add(panel);
    }

    public void startProcess() {
        // Use SwingWorker to perform long task in background
        AdbWorker worker = new AdbWorker();
        worker.execute();
    }

    // SwingWorker to handle ADB logic off the Event Dispatch Thread (EDT)
    private class AdbWorker extends SwingWorker<String, String> {

        private final List<String> ADB_FILES = Arrays.asList(
            "adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "etc1tool.exe", "fastboot.exe",
            "hprof-conv.exe", "libwinpthread-1.dll", "make_f2fs_casefold.exe", "make_f2fs.exe",
            "mke2fs.conf", "mke2fs.exe", "NOTICE.txt", "source.properties", "sqlite3.exe"
        );

        private Path adbExecutable;

        @Override
        protected String doInBackground() throws Exception {
            publish("Unpacking ADB tools...");
            Path tempDir = unpackAdb();
            adbExecutable = tempDir.resolve("adb.exe");

            publish("Checking for ADB devices...");
            boolean deviceAuthorized = false;

            for (int i = 0; i < 12; i++) {
                CommandResult adbDevicesResult = executeCommand(adbExecutable.toString(), "devices");
                String adbDevicesOutput = adbDevicesResult.stdout;

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
                CommandResult getSoundResult = executeCommand(adbExecutable.toString(), "shell", "settings", "get", "system", "csc_pref_camera_forced_shuttersound_key");
                String currentSetting = getSoundResult.stdout.trim();

                if ("1".equals(currentSetting)) {
                    publish("Disabling shutter sound...");
                    executeCommand(adbExecutable.toString(), "shell", "settings", "put", "system", "csc_pref_camera_forced_shuttersound_key", "0");
                    CommandResult verifyResult = executeCommand(adbExecutable.toString(), "shell", "settings", "get", "system", "csc_pref_camera_forced_shuttersound_key");
                    if ("0".equals(verifyResult.stdout.trim())) {
                        return "Success! Camera shutter sound disabled.";
                    } else {
                        return "Error: Failed to disable shutter sound.";
                    }
                } else if ("0".equals(currentSetting)) {
                    return "Already disabled. No action needed.";
                } else {
                    return "Could not determine shutter sound status.";
                }
            } else {
                return "Timeout: No authorized device found.";
            }
        }

        @Override
        protected void process(List<String> chunks) {
            // Update GUI with messages from doInBackground
            String latestStatus = chunks.get(chunks.size() - 1);
            statusLabel.setText(latestStatus);
        }

        @Override
        protected void done() {
            // Task is complete
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            try {
                String finalStatus = get();
                statusLabel.setText(finalStatus);
            } catch (InterruptedException | ExecutionException e) {
                statusLabel.setText("Error: " + e.getCause().getMessage());
            }
        }

        private Path unpackAdb() throws IOException {
            Path tempDir = Files.createTempDirectory("adb-gui-temp-");
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    Files.walk(tempDir).sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                        try { Files.delete(path); } catch (IOException ex) { /* ignore */ }
                    });
                } catch (IOException ex) { /* ignore */ }
            }));
            for (String fileName : ADB_FILES) {
                try (InputStream is = ShutterSoundGUI.class.getResourceAsStream("/adb/" + fileName)) {
                    if (is == null) throw new IOException("Cannot find resource: /adb/" + fileName);
                    Files.copy(is, tempDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            return tempDir;
        }

        private CommandResult executeCommand(String... command) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder(command);
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
