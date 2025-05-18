package com.atharv.CodeEditor;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

public class ByteSmith extends JFrame implements ActionListener {

    private RSyntaxTextArea textArea;
    private JTextArea consoleArea;   // Single console for output + input
    private RTextScrollPane scrollPane;
    private JFileChooser fileChooser;

    private String currentFilePath = null;
    private String currentLanguage = "Java";

    private Process runningProcess;
    private BufferedWriter processInputWriter;

    private int promptPosition = 0;  // Marks where user input starts in consoleArea

    private JLabel statusBar;

    public ByteSmith() {
        setTitle("ByteSmith");
        setSize(900, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        // Editor area
        textArea = new RSyntaxTextArea(20, 70);
        setLanguageSyntax(currentLanguage);
        textArea.setCodeFoldingEnabled(true);
        textArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        scrollPane = new RTextScrollPane(textArea);
        scrollPane.setLineNumbersEnabled(true);

        // Console area for output + input
        consoleArea = new JTextArea(12, 70);
        consoleArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        consoleArea.setLineWrap(true);
        consoleArea.setWrapStyleWord(true);
        consoleArea.setBackground(new Color(30, 30, 30));
        consoleArea.setForeground(new Color(200, 200, 200));
        consoleArea.setCaretColor(Color.WHITE);

        // Make consoleArea behave like terminal
        consoleArea.setEditable(true);
        DefaultCaret caret = (DefaultCaret) consoleArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane consoleScrollPane = new JScrollPane(consoleArea);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, consoleScrollPane);
        splitPane.setDividerLocation(420);

        // Top panel with Run, Stop, Clear buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton runButton = new JButton("Run (Ctrl+R)");
        runButton.addActionListener(e -> runCode());
        JButton stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> stopProcess());
        JButton clearButton = new JButton("Clear Console");
        clearButton.addActionListener(e -> clearConsole());
        topPanel.add(runButton);
        topPanel.add(stopButton);
        topPanel.add(clearButton);

        // Menu bar
        createMenuBar();

        // Status bar at bottom
        statusBar = new JLabel(" No file loaded | Ready");
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        statusBar.setFont(new Font("Consolas", Font.PLAIN, 12));

        // Add components
        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(statusBar, BorderLayout.SOUTH);

        fileChooser = new JFileChooser();

        // Console input handling
        consoleArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (consoleArea.getCaretPosition() < promptPosition) {
                    consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
                }
                if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    if (consoleArea.getCaretPosition() <= promptPosition) {
                        e.consume();
                    }
                }
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    e.consume();
                    handleUserInput();
                }
            }
        });

        // Keyboard shortcut Ctrl+R for run
        textArea.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK), "runCode");
        textArea.getActionMap().put("runCode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runCode();
            }
        });

        setVisible(true);
    }

    private void createMenuBar() {
        JMenuBar menuBar = new JMenuBar();

        JMenu fileMenu = new JMenu("File");

        JMenuItem newFile = new JMenuItem("New");
        newFile.addActionListener(this);
        JMenuItem openFile = new JMenuItem("Open");
        openFile.addActionListener(this);
        JMenuItem saveFile = new JMenuItem("Save");
        saveFile.addActionListener(this);
        JMenuItem exitApp = new JMenuItem("Exit");
        exitApp.addActionListener(this);

        fileMenu.add(newFile);
        fileMenu.add(openFile);
        fileMenu.add(saveFile);
        fileMenu.addSeparator();
        fileMenu.add(exitApp);

        menuBar.add(fileMenu);

        JMenu langMenu = new JMenu("Language");
        String[] languages = {"Java", "Python", "C++", "JavaScript", "Plain Text"};
        for (String lang : languages) {
            JMenuItem langItem = new JMenuItem(lang);
            langItem.addActionListener(e -> {
                currentLanguage = lang;
                setLanguageSyntax(lang);
                updateStatusBar();
            });
            langMenu.add(langItem);
        }
        menuBar.add(langMenu);

        setJMenuBar(menuBar);
    }

    private void setLanguageSyntax(String lang) {
        switch (lang) {
            case "Java":
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
                break;
            case "Python":
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
                break;
            case "C++":
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
                break;
            case "JavaScript":
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
                break;
            default:
                textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();

        switch (cmd) {
            case "New":
                textArea.setText("");
                currentFilePath = null;
                setTitle("ByteSmith");
                consoleArea.setText("");
                promptPosition = 0;
                updateStatusBar();
                break;

            case "Open":
                openFile();
                break;

            case "Save":
                saveFile();
                break;

            case "Exit":
                stopProcess();
                System.exit(0);
                break;
        }
    }

    private void openFile() {
        int option = fileChooser.showOpenDialog(this);
        if (option == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                textArea.read(br, null);
                currentFilePath = file.getAbsolutePath();
                setTitle("ByteSmith - " + file.getName());
                setSyntaxByFileName(file.getName());
                consoleArea.setText("");
                promptPosition = 0;
                updateStatusBar();
                autoSave();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to open file: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setSyntaxByFileName(String filename) {
        if (filename.endsWith(".java")) {
            currentLanguage = "Java";
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVA);
        } else if (filename.endsWith(".py")) {
            currentLanguage = "Python";
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PYTHON);
        } else if (filename.endsWith(".cpp") || filename.endsWith(".h") || filename.endsWith(".c")) {
            currentLanguage = "C++";
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS);
        } else if (filename.endsWith(".js")) {
            currentLanguage = "JavaScript";
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        } else {
            currentLanguage = "Plain Text";
            textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
        }
        updateStatusBar();
    }

    private void saveFile() {
        if (currentFilePath == null) {
            int option = fileChooser.showSaveDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();
                currentFilePath = file.getAbsolutePath();
            } else {
                return; // cancel save
            }
        }

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(currentFilePath))) {
            textArea.write(bw);
            setTitle("ByteSmith - " + new File(currentFilePath).getName());
            updateStatusBar();
            autoSave();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Failed to save file: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void autoSave() {
        // You can add auto-save logic here (for demo, saving after each open/save)
        // For now, just print to console
        System.out.println("Auto-saved: " + currentFilePath);
    }

    private void runCode() {
        if (runningProcess != null) {
            appendConsole("\nAlready running a process! Stop it first.\n");
            return;
        }

        if (currentFilePath == null) {
            JOptionPane.showMessageDialog(this, "Save your file before running code.",
                    "Save Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        saveFile();  // Save before running

        appendConsole("\nRunning code...\n");
        statusBar.setText(" Running...");

        String cmd = buildRunCommand(currentFilePath, currentLanguage);
        if (cmd == null) {
            appendConsole("Run command not supported for language: " + currentLanguage + "\n");
            statusBar.setText(" Ready");
            return;
        }

        try {
            runningProcess = Runtime.getRuntime().exec(cmd);

            // Input stream to send user input to the process
            processInputWriter = new BufferedWriter(new OutputStreamWriter(runningProcess.getOutputStream()));

            // Read output asynchronously
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(runningProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        appendConsole(line + "\n");
                    }
                } catch (IOException ignored) {}
            }).start();

            // Read error stream asynchronously
            new Thread(() -> {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(runningProcess.getErrorStream()))) {
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        appendConsole(line + "\n");
                    }
                } catch (IOException ignored) {}
            }).start();

            // Wait for process to finish
            new Thread(() -> {
                try {
                    int exitCode = runningProcess.waitFor();
                    appendConsole("\nProcess finished with exit code: " + exitCode + "\n");
                } catch (InterruptedException ignored) {}
                runningProcess = null;
                processInputWriter = null;
                statusBar.setText(" Ready");
                addPrompt();
            }).start();

            addPrompt();

        } catch (IOException ex) {
            appendConsole("Failed to run process: " + ex.getMessage() + "\n");
            runningProcess = null;
            statusBar.setText(" Ready");
        }
    }

    private String buildRunCommand(String filePath, String lang) {
        String fileName = new File(filePath).getName();
        String dir = new File(filePath).getParent();

        switch (lang) {
            case "Java":
                // Compile first
                try {
                    Process compileProcess = Runtime.getRuntime().exec("javac \"" + filePath + "\"");
                    compileProcess.waitFor();
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
                    StringBuilder errors = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errors.append(line).append("\n");
                    }
                    if (errors.length() > 0) {
                        appendConsole("Compilation errors:\n" + errors);
                        return null;
                    }
                } catch (Exception e) {
                    appendConsole("Compilation failed: " + e.getMessage() + "\n");
                    return null;
                }
                String className = fileName.replace(".java", "");
                return "java -cp \"" + dir + "\" " + className;

            case "Python":
                return "python \"" + filePath + "\"";

            case "C++":
                // Compile and run binary
                String exeName = "a.exe";
                try {
                    Process compileProcess = Runtime.getRuntime().exec("g++ \"" + filePath + "\" -o " + exeName);
                    compileProcess.waitFor();
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(compileProcess.getErrorStream()));
                    StringBuilder errors = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errors.append(line).append("\n");
                    }
                    if (errors.length() > 0) {
                        appendConsole("Compilation errors:\n" + errors);
                        return null;
                    }
                } catch (Exception e) {
                    appendConsole("Compilation failed: " + e.getMessage() + "\n");
                    return null;
                }
                return "./" + exeName;

            case "JavaScript":
                return "node \"" + filePath + "\"";

            default:
                return null;
        }
    }

    private void appendConsole(String text) {
        SwingUtilities.invokeLater(() -> {
            consoleArea.append(text);
            promptPosition = consoleArea.getDocument().getLength();
            consoleArea.setCaretPosition(promptPosition);
        });
    }

    private void addPrompt() {
        SwingUtilities.invokeLater(() -> {
            String prompt = "> ";
            consoleArea.append(prompt);
            promptPosition = consoleArea.getDocument().getLength();
            consoleArea.setCaretPosition(promptPosition);
        });
    }

    private void handleUserInput() {
        try {
            int docLength = consoleArea.getDocument().getLength();
            String userInput = consoleArea.getText(promptPosition, docLength - promptPosition).trim();

            appendConsole("\n"); // move to next line

            if (runningProcess != null && processInputWriter != null) {
                // Send input to running process
                processInputWriter.write(userInput + "\n");
                processInputWriter.flush();
            } else {
                appendConsole("No running process to send input.\n");
            }
            addPrompt();
        } catch (BadLocationException | IOException e) {
            appendConsole("Failed to handle input: " + e.getMessage() + "\n");
        }
    }

    private void stopProcess() {
        if (runningProcess != null) {
            runningProcess.destroy();
            runningProcess = null;
            processInputWriter = null;
            appendConsole("\nProcess stopped by user.\n");
            statusBar.setText(" Ready");
            addPrompt();
        } else {
            appendConsole("No running process to stop.\n");
        }
    }

    private void clearConsole() {
        consoleArea.setText("");
        promptPosition = 0;
        addPrompt();
    }

    private void updateStatusBar() {
        String fileName = (currentFilePath == null) ? "No file loaded" : new File(currentFilePath).getName();
        String runningStatus = (runningProcess == null) ? "Ready" : "Running";
        statusBar.setText(" " + fileName + " | " + currentLanguage + " | " + runningStatus);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ByteSmith::new);
    }
}
