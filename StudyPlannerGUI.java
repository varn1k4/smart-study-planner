import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * StudyPlannerGUI.java
 *
 * Single-file Swing application implementing the Study Planner requested.
 * - Pastel aesthetic (option A)
 * - Signup / Login (users stored in users.txt in the app folder)
 * - Subjects entry (hours, urgency, completion)
 * - Smart timetable generation (urgency-weighted)
 * - Pomodoro timer with customizable lengths
 * - Manual productive timer
 * - Per-user subject persistence (serialized)
 *
 * Compile: javac StudyPlannerGUI.java
 * Run:     java StudyPlannerGUI
 *
 * NOTE: For demo simplicity, passwords are stored plainly in users.txt. Do not use
 * this approach in production.
 */

public class StudyPlannerGUI extends JFrame {
    // Files
    private static final File USER_DB = new File("users.txt");
    private static final String SUBJECTS_SUFFIX = "_subjects.ser"; // per-user file

    // UI
    private CardLayout cards = new CardLayout();
    private JPanel mainPanel = new JPanel(cards);

    // Login/signup
    private JTextField signupEmailField;
    private JTextField signupUsernameField;
    private JPasswordField signupPasswordField;
    private JPasswordField signupConfirmField;
    private JCheckBox autoPasswordBox;

    private JTextField loginUsernameField;
    private JPasswordField loginPasswordField;

    // After login
    private User currentUser;
    private DefaultListModel<Subject> subjectListModel = new DefaultListModel<>();
    private JList<Subject> subjectJList = new JList<>(subjectListModel);

    // Timetable display
    private DefaultListModel<String> timetableModel = new DefaultListModel<>();
    private JList<String> timetableJList = new JList<>(timetableModel);

    // Pomodoro
    private JLabel pomoCountdownLabel = new JLabel("00:00:00");
    private javax.swing.Timer pomoTimer;
    private int pomoSecondsLeft = 0;
    private boolean pomoRunning = false;

    // Manual timer
    private JLabel manualTimerLabel = new JLabel("00:00:00");
    private javax.swing.Timer manualTimer;
    private long manualStartTime = 0;

    // Appearance
    private static final Color PASTEL_BG = new Color(255, 244, 249);
    private static final Color PASTEL_PANEL = new Color(255, 234, 241);
    private static final Color PASTEL_ACCENT = new Color(255, 182, 193);
    private static final Font HEADER_FONT = new Font("SansSerif", Font.BOLD, 18);

    public StudyPlannerGUI() {
        setTitle("🌸 Smart Study Planner — Pastel Edition");
        setSize(900, 640);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        ensureUserDbExists();

        mainPanel.setBackground(PASTEL_BG);
        add(mainPanel);

        mainPanel.add(buildWelcomePanel(), "welcome");
        mainPanel.add(buildSignupLoginPanel(), "auth");
        mainPanel.add(buildAppPanel(), "app");

        cards.show(mainPanel, "welcome");
    }

    // ---------- Panels ----------
    private JPanel buildWelcomePanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PASTEL_BG);
        JLabel title = new JLabel("🌸 Welcome to Smart Study Planner");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        title.setHorizontalAlignment(SwingConstants.CENTER);
        title.setBorder(new EmptyBorder(30, 0, 10, 0));
        p.add(title, BorderLayout.NORTH);

        JTextArea info = new JTextArea("A cute pastel study planner with signup/login, smart scheduling, and Pomodoro support.\n\nChoose to Sign Up or Log In to continue.");
        info.setEditable(false);
        info.setLineWrap(true);
        info.setWrapStyleWord(true);
        info.setBackground(PASTEL_BG);
        info.setFont(new Font("SansSerif", Font.PLAIN, 14));
        info.setBorder(new EmptyBorder(10, 30, 10, 30));
        p.add(info, BorderLayout.CENTER);

        JPanel btns = new JPanel();
        btns.setBackground(PASTEL_BG);
        JButton signup = pastelButton("Sign Up ✨");
        JButton login = pastelButton("Log In 🔐");
        signup.addActionListener(e -> cards.show(mainPanel, "auth"));
        login.addActionListener(e -> cards.show(mainPanel, "auth"));
        btns.add(signup);
        btns.add(login);
        p.add(btns, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildSignupLoginPanel() {
        JPanel p = new JPanel(new GridLayout(1, 2));
        p.setBackground(PASTEL_BG);
        p.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Signup panel
        JPanel s = new JPanel();
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.setBackground(PASTEL_PANEL);
        s.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel sTitle = new JLabel("Create Account");
        sTitle.setFont(HEADER_FONT);
        sTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        s.add(sTitle);
        s.add(Box.createRigidArea(new Dimension(0, 10)));

        s.add(new JLabel("Email:"));
        signupEmailField = new JTextField();
        s.add(signupEmailField);

        s.add(new JLabel("Username (leave empty to auto-generate):"));
        signupUsernameField = new JTextField();
        s.add(signupUsernameField);

        s.add(new JLabel("Password:"));
        signupPasswordField = new JPasswordField();
        s.add(signupPasswordField);

        s.add(new JLabel("Confirm Password:"));
        signupConfirmField = new JPasswordField();
        s.add(signupConfirmField);

        autoPasswordBox = new JCheckBox("Auto-generate password (if checked, fields ignored)");
        autoPasswordBox.setBackground(PASTEL_PANEL);
        s.add(autoPasswordBox);

        JButton doSignup = pastelButton("Sign Up 💖");
        doSignup.setAlignmentX(Component.CENTER_ALIGNMENT);
        doSignup.addActionListener(e -> handleSignup());
        s.add(Box.createRigidArea(new Dimension(0, 8)));
        s.add(doSignup);

        // Login panel
        JPanel l = new JPanel();
        l.setLayout(new BoxLayout(l, BoxLayout.Y_AXIS));
        l.setBackground(PASTEL_PANEL);
        l.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel lTitle = new JLabel("Welcome Back");
        lTitle.setFont(HEADER_FONT);
        lTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        l.add(lTitle);
        l.add(Box.createRigidArea(new Dimension(0, 10)));

        l.add(new JLabel("Username:"));
        loginUsernameField = new JTextField();
        l.add(loginUsernameField);

        l.add(new JLabel("Password:"));
        loginPasswordField = new JPasswordField();
        l.add(loginPasswordField);

        JButton doLogin = pastelButton("Log In ✅");
        doLogin.setAlignmentX(Component.CENTER_ALIGNMENT);
        doLogin.addActionListener(e -> handleLogin());
        l.add(Box.createRigidArea(new Dimension(0, 8)));
        l.add(doLogin);

        JButton back = pastelButton("Back");
        back.addActionListener(ev -> cards.show(mainPanel, "welcome"));
        l.add(Box.createRigidArea(new Dimension(0, 8)));
        l.add(back);

        p.add(s);
        p.add(l);
        return p;
    }

    private JPanel buildAppPanel() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PASTEL_BG);

        // Top header with current user
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PASTEL_BG);
        JLabel appTitle = new JLabel("📚 Smart Study Planner");
        appTitle.setFont(new Font("SansSerif", Font.BOLD, 22));
        appTitle.setBorder(new EmptyBorder(10, 10, 10, 10));
        header.add(appTitle, BorderLayout.WEST);

        JButton logout = pastelButton("Logout");
        logout.addActionListener(e -> logout());
        header.add(logout, BorderLayout.EAST);

        p.add(header, BorderLayout.NORTH);

        // Main split: left sidebar and content
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(300);
        split.setLeftComponent(buildLeftPanel());
        split.setRightComponent(buildRightPanel());
        split.setBackground(PASTEL_BG);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildLeftPanel() {
        JPanel lp = new JPanel();
        lp.setLayout(new BorderLayout());
        lp.setBackground(PASTEL_PANEL);
        lp.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel section = new JLabel("Subjects & Tasks");
        section.setFont(HEADER_FONT);
        lp.add(section, BorderLayout.NORTH);

        subjectJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subjectJList.setCellRenderer(new SubjectCellRenderer());
        JScrollPane scroller = new JScrollPane(subjectJList);
        lp.add(scroller, BorderLayout.CENTER);

        JPanel controls = new JPanel();
        controls.setBackground(PASTEL_PANEL);
        controls.setLayout(new GridLayout(0, 1, 6, 6));

        JButton add = pastelButton("Add Subject ➕");
        add.addActionListener(e -> addSubjectDialog());
        JButton edit = pastelButton("Edit Selected ✏️");
        edit.addActionListener(e -> editSelectedSubject());
        JButton remove = pastelButton("Remove Selected 🗑️");
        remove.addActionListener(e -> removeSelectedSubject());
        JButton save = pastelButton("Save Subjects 💾");
        save.addActionListener(e -> saveSubjectsForUser());

        controls.add(add);
        controls.add(edit);
        controls.add(remove);
        controls.add(save);

        lp.add(controls, BorderLayout.SOUTH);
        return lp;
    }

    private JPanel buildRightPanel() {
        JPanel rp = new JPanel(new BorderLayout());
        rp.setBackground(PASTEL_BG);
        rp.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(PASTEL_BG);
        tabs.addTab("Timetable", buildTimetablePanel());
        tabs.addTab("Pomodoro", buildPomodoroPanel());
        tabs.addTab("Manual Timer", buildManualTimerPanel());
        tabs.addTab("Profile", buildProfilePanel());

        rp.add(tabs, BorderLayout.CENTER);
        return rp;
    }

    private JPanel buildTimetablePanel() {
        JPanel tp = new JPanel(new BorderLayout());
        tp.setBackground(PASTEL_BG);

        JPanel top = new JPanel(new GridLayout(2, 2, 8, 8));
        top.setBackground(PASTEL_BG);
        top.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel hoursLabel = new JLabel("Hours / day:");
        JTextField hoursField = new JTextField("4");
        JLabel modeLabel = new JLabel("Mode:");
        String[] modes = {"Pomodoro (25/5)", "Plain blocks"};
        JComboBox<String> modeCombo = new JComboBox<>(modes);

        top.add(hoursLabel);
        top.add(hoursField);
        top.add(modeLabel);
        top.add(modeCombo);

        tp.add(top, BorderLayout.NORTH);

        timetableJList.setModel(timetableModel);
        JScrollPane tableScroll = new JScrollPane(timetableJList);
        tp.add(tableScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setBackground(PASTEL_BG);
        JButton gen = pastelButton("Generate Timetable ✨");
        gen.addActionListener(e -> generateTimetable(Double.parseDouble(hoursField.getText()), modeCombo.getSelectedIndex() == 0));
        bottom.add(gen);
        tp.add(bottom, BorderLayout.SOUTH);
        return tp;
    }

    private JPanel buildPomodoroPanel() {
        JPanel pp = new JPanel(new BorderLayout());
        pp.setBackground(PASTEL_BG);
        pp.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel center = new JPanel();
        center.setBackground(PASTEL_BG);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel hint = new JLabel("Customize your Pomodoro: work / break / rounds");
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(hint);
        center.add(Box.createRigidArea(new Dimension(0, 8)));

        JPanel settings = new JPanel(new GridLayout(1, 6, 6, 6));
        settings.setBackground(PASTEL_BG);
        JTextField workField = new JTextField("25");
        JTextField breakField = new JTextField("5");
        JTextField roundsField = new JTextField("4");
        settings.add(new JLabel("Work (min):"));
        settings.add(workField);
        settings.add(new JLabel("Break (min):"));
        settings.add(breakField);
        settings.add(new JLabel("Rounds:"));
        settings.add(roundsField);
        center.add(settings);

        center.add(Box.createRigidArea(new Dimension(0, 16)));
        pomoCountdownLabel.setFont(new Font("Monospaced", Font.BOLD, 36));
        pomoCountdownLabel.setHorizontalAlignment(SwingConstants.CENTER);
        center.add(pomoCountdownLabel);

        JPanel controls = new JPanel();
        JButton start = pastelButton("Start Pomodoro ▶️");
        JButton stop = pastelButton("Stop ⏹");
        controls.add(start);
        controls.add(stop);
        center.add(Box.createRigidArea(new Dimension(0, 10)));
        center.add(controls);

        start.addActionListener(e -> startPomodoro(Integer.parseInt(workField.getText()), Integer.parseInt(breakField.getText()), Integer.parseInt(roundsField.getText())));
        stop.addActionListener(e -> stopPomodoro());

        pp.add(center, BorderLayout.CENTER);
        return pp;
    }

    private JPanel buildManualTimerPanel() {
        JPanel mp = new JPanel();
        mp.setBackground(PASTEL_BG);
        mp.setLayout(new BoxLayout(mp, BoxLayout.Y_AXIS));
        mp.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel hint = new JLabel("Manual productive timer — press Start when you begin studying, Stop when you finish.");
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);
        mp.add(hint);

        mp.add(Box.createRigidArea(new Dimension(0, 10)));
        manualTimerLabel.setFont(new Font("Monospaced", Font.BOLD, 28));
        manualTimerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mp.add(manualTimerLabel);

        mp.add(Box.createRigidArea(new Dimension(0, 12)));
        JPanel ctr = new JPanel();
        ctr.setBackground(PASTEL_BG);
        JButton start = pastelButton("Start ⏱");
        JButton stop = pastelButton("Stop ⏹");
        ctr.add(start);
        ctr.add(stop);
        mp.add(ctr);

        start.addActionListener(e -> startManualTimer());
        stop.addActionListener(e -> stopManualTimer());

        return mp;
    }

    private JPanel buildProfilePanel() {
        JPanel pp = new JPanel();
        pp.setBackground(PASTEL_BG);
        pp.setLayout(new BoxLayout(pp, BoxLayout.Y_AXIS));
        pp.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel label = new JLabel("Profile & Settings");
        label.setFont(HEADER_FONT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        pp.add(label);

        pp.add(Box.createRigidArea(new Dimension(0, 10)));
        JLabel userLabel = new JLabel("Logged in as: —");
        userLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        pp.add(userLabel);

        // update when currentUser changes
        pp.add(Box.createRigidArea(new Dimension(0, 10)));
        JButton export = pastelButton("Export Timetable to TXT");
        export.addActionListener(e -> exportTimetable());
        pp.add(export);

        // filler
        pp.add(Box.createVerticalGlue());

        // update label when showing
        pp.addHierarchyListener(e -> {
            if (currentUser != null) userLabel.setText("Logged in as: " + currentUser.username + " (" + currentUser.email + ")");
        });

        return pp;
    }

    // ---------- Actions ----------
    private void handleSignup() {
        String email = signupEmailField.getText().trim();
        String username = signupUsernameField.getText().trim();
        String pass = new String(signupPasswordField.getPassword());
        String confirm = new String(signupConfirmField.getPassword());

        if (!email.contains("@")) {
            JOptionPane.showMessageDialog(this, "Please enter a valid email.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (username.isEmpty()) {
            username = generateUsernameFromEmail(email);
        }
        if (usernameExists(username)) {
            JOptionPane.showMessageDialog(this, "Username already exists. Choose another.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (autoPasswordBox.isSelected()) {
            pass = generateRandomPassword(10);
            JOptionPane.showMessageDialog(this, "Auto-generated password: " + pass + "\nPlease copy it somewhere safe.", "Password Generated", JOptionPane.INFORMATION_MESSAGE);
        } else {
            if (pass.isEmpty() || !pass.equals(confirm)) {
                JOptionPane.showMessageDialog(this, "Passwords are empty or do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        addUserToDb(new User(username, email, pass));
        JOptionPane.showMessageDialog(this, "Signed up! You can now log in with username: " + username, "Success", JOptionPane.INFORMATION_MESSAGE);
        // clear fields
        signupEmailField.setText("");
        signupUsernameField.setText("");
        signupPasswordField.setText("");
        signupConfirmField.setText("");
    }

    private void handleLogin() {
        String username = loginUsernameField.getText().trim();
        String pass = new String(loginPasswordField.getPassword());
        if (username.isEmpty() || pass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter username & password.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        User u = getUserFromDb(username);
        if (u == null) {
            JOptionPane.showMessageDialog(this, "User not found. Please sign up first.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!u.password.equals(pass)) {
            JOptionPane.showMessageDialog(this, "Incorrect password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // success
        currentUser = u;
        loadSubjectsForUser();
        cards.show(mainPanel, "app");
    }

    private void logout() {
        int ok = JOptionPane.showConfirmDialog(this, "Logout?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            currentUser = null;
            subjectListModel.clear();
            timetableModel.clear();
            cards.show(mainPanel, "welcome");
        }
    }

    // ---------- Subject management ----------
    private void addSubjectDialog() {
        SubjectForm form = new SubjectForm(this, null);
        form.setVisible(true);
        if (form.ok) {
            subjectListModel.addElement(form.subject);
        }
    }

    private void editSelectedSubject() {
        Subject sel = subjectJList.getSelectedValue();
        if (sel == null) return;
        SubjectForm form = new SubjectForm(this, sel);
        form.setVisible(true);
        if (form.ok) {
            subjectJList.repaint();
        }
    }

    private void removeSelectedSubject() {
        Subject sel = subjectJList.getSelectedValue();
        if (sel == null) return;
        int ok = JOptionPane.showConfirmDialog(this, "Remove " + sel.name + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) subjectListModel.removeElement(sel);
    }

    private void saveSubjectsForUser() {
        if (currentUser == null) return;
        java.util.List<Subject> list = Collections.list(subjectListModel.elements());
        File f = new File(currentUser.username + SUBJECTS_SUFFIX);
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(f))) {
            oos.writeObject(list);
            JOptionPane.showMessageDialog(this, "Subjects saved for user.", "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to save subjects: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSubjectsForUser() {
        subjectListModel.clear();
        if (currentUser == null) return;
        File f = new File(currentUser.username + SUBJECTS_SUFFIX);
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            java.util.List<?> list = (java.util.List<?>) ois.readObject();
            for (Object o : list) if (o instanceof Subject) subjectListModel.addElement((Subject) o);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ---------- Timetable generation ----------
    private void generateTimetable(double dailyHours, boolean usePomo) {
        timetableModel.clear();
        java.util.List<Subject> subjects = Collections.list(subjectListModel.elements());
        if (subjects.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No subjects to schedule.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // compute remaining hours per subject
        class Task { Subject s; double remaining; double weight; }
        java.util.List<Task> tasks = new ArrayList<>();
        double totalWeight = 0;
        for (Subject s : subjects) {
            double rem = Math.max(0, s.hoursNeeded * (1.0 - s.completionPercent / 100.0));
            if (rem <= 0) continue;
            Task t = new Task();
            t.s = s;
            t.remaining = rem;
            t.weight = rem * (1.0 + s.urgency / 5.0);
            tasks.add(t);
            totalWeight += t.weight;
        }
        if (tasks.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All tasks show no remaining hours.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // scheduling parameters
        int pomoWork = 25;
        int pomoBreak = 5;
        double availableMinutes = Math.max(30, Math.min(dailyHours * 60, 16 * 60));

        // sort by urgency
        tasks.sort((a, b) -> {
            if (a.s.urgency != b.s.urgency) return b.s.urgency - a.s.urgency;
            return Double.compare(b.weight, a.weight);
        });

        // schedule loop
        int idx = 0;
        LocalTime cursor = LocalTime.of(7, 0);
        while (availableMinutes >= 15 && !tasks.isEmpty()) {
            Task chosen = tasks.get(idx % tasks.size());
            if (chosen.remaining <= 0.01) { tasks.remove(idx % tasks.size()); continue; }
            int block = usePomo ? pomoWork : Math.min(60, (int)Math.round(availableMinutes));
            double remMins = chosen.remaining * 60.0;
            if (remMins < block) block = Math.max(15, (int)Math.round(remMins));
            if (block > availableMinutes) block = (int)Math.floor(availableMinutes);

            LocalTime end = cursor.plusMinutes(block);
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("hh:mm a");
            timetableModel.addElement(fmt.format(cursor) + " - " + fmt.format(end) + " | " + chosen.s.name + (usePomo ? " (Pomodoro)" : ""));

            cursor = end;
            availableMinutes -= block;
            chosen.remaining -= block / 60.0;

            if (usePomo && availableMinutes >= pomoBreak) {
                LocalTime bEnd = cursor.plusMinutes(pomoBreak);
                timetableModel.addElement(fmt.format(cursor) + " - " + fmt.format(bEnd) + " | Break");
                cursor = bEnd;
                availableMinutes -= pomoBreak;
            }

            idx++;
            tasks.sort((a,b) -> {
                if (a.s.urgency != b.s.urgency) return b.s.urgency - a.s.urgency;
                return Double.compare(b.weight * b.remaining, a.weight * a.remaining);
            });
        }

        JOptionPane.showMessageDialog(this, "Timetable generated. Total study blocks: " + timetableModel.size(), "Done", JOptionPane.INFORMATION_MESSAGE);
    }

    // ---------- Pomodoro logic ----------
    private void startPomodoro(int workMin, int breakMin, int rounds) {
        if (pomoRunning) return;
        // We'll run sequentially: work, break, work, ... using Swing javax.swing.Timer that ticks each second
        java.util.List<Integer> sequence = new ArrayList<>(); // positive = work minutes, negative = break minutes
        for (int i = 0; i < rounds; i++) {
            sequence.add(workMin);
            if (i < rounds - 1) sequence.add(-breakMin);
        }

        // flatten to seconds per step
        java.util.List<Integer> seqSeconds = new ArrayList<>();
        for (int v : sequence) seqSeconds.add(Math.abs(v * 60));

        // Runner state
        final int[] pos = {0};
        final boolean[] isWork = {sequence.get(0) > 0};
        pomoSecondsLeft = seqSeconds.get(0);
        updatePomoLabel();

        pomoTimer = new javax.swing.Timer(1000, null);
        pomoTimer.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (pomoSecondsLeft <= 0) {
                    pos[0]++;
                    if (pos[0] >= seqSeconds.size()) {
                        stopPomodoro();
                        JOptionPane.showMessageDialog(StudyPlannerGUI.this, "Pomodoro complete! Great job 🌟", "Done", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    pomoSecondsLeft = seqSeconds.get(pos[0]);
                    isWork[0] = sequence.get(pos[0]) > 0;
                    updatePomoLabel();
                }
                pomoSecondsLeft--;
                updatePomoLabel();
            }
        });
        pomoTimer.start();
        pomoRunning = true;
    }

    private void stopPomodoro() {
        if (pomoTimer != null) pomoTimer.stop();
        pomoRunning = false;
        pomoSecondsLeft = 0;
        updatePomoLabel();
    }

    private void updatePomoLabel() {
        long s = Math.max(0, pomoSecondsLeft);
        long hrs = s / 3600;
        long mins = (s % 3600) / 60;
        long secs = s % 60;
        String formatted = (hrs > 0) ? String.format("%02d:%02d:%02d", hrs, mins, secs) : String.format("%02d:%02d", mins, secs);
        pomoCountdownLabel.setText(formatted);
    }

    // ---------- Manual timer ----------
    private void startManualTimer() {
        if (manualTimer != null && manualTimer.isRunning()) return;
        manualStartTime = System.currentTimeMillis();
        manualTimer = new javax.swing.Timer(1000, e -> {
            long elapsed = (System.currentTimeMillis() - manualStartTime) / 1000;
            manualTimerLabel.setText(formatSeconds(elapsed));
        });
        manualTimer.start();
    }

    private void stopManualTimer() {
        if (manualTimer == null) return;
        manualTimer.stop();
        long elapsed = (System.currentTimeMillis() - manualStartTime) / 1000;
        JOptionPane.showMessageDialog(this, "You studied productively for " + formatSeconds(elapsed), "Session Ended", JOptionPane.INFORMATION_MESSAGE);
        manualTimerLabel.setText("00:00:00");
        manualStartTime = 0;
    }

    private String formatSeconds(long s) {
        long hrs = s / 3600;
        long mins = (s % 3600) / 60;
        long secs = s % 60;
        if (hrs > 0) return String.format("%02d:%02d:%02d", hrs, mins, secs);
        return String.format("%02d:%02d:%02d", mins, secs, 0).replaceFirst(":00$", ":00");
    }

    // ---------- Export ----------
    private void exportTimetable() {
        if (currentUser == null) return;
        if (timetableModel.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No timetable to export.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        File out = new File(currentUser.username + "_timetable.txt");
        try (PrintWriter pw = new PrintWriter(out)) {
            pw.println("Timetable for: " + currentUser.username + " — " + java.time.LocalDate.now());
            for (int i = 0; i < timetableModel.size(); i++) pw.println(timetableModel.get(i));
            JOptionPane.showMessageDialog(this, "Exported to " + out.getAbsolutePath(), "Exported", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------- User DB helpers ----------
    private void ensureUserDbExists() {
        try {
            if (!USER_DB.exists()) USER_DB.createNewFile();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private boolean usernameExists(String username) {
        try (BufferedReader br = new BufferedReader(new FileReader(USER_DB))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 3);
                if (parts.length >= 1 && parts[0].equals(username)) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private void addUserToDb(User u) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(USER_DB, true))) {
            bw.write(u.username + ":" + u.email + ":" + u.password);
            bw.newLine();
        } catch (IOException e) { e.printStackTrace(); }
    }

    private User getUserFromDb(String username) {
        try (BufferedReader br = new BufferedReader(new FileReader(USER_DB))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(":", 3);
                if (parts.length == 3 && parts[0].equals(username)) return new User(parts[0], parts[1], parts[2]);
            }
        } catch (IOException ignored) {}
        return null;
    }

    // ---------- Utilities ----------
    private String generateUsernameFromEmail(String email) {
        String base = email.split("@")[0].replaceAll("[^A-Za-z0-9]", "");
        if (base.length() < 3) base = base + "user";
        String candidate = base;
        Random r = new Random();
        while (usernameExists(candidate)) candidate = base + (100 + r.nextInt(900));
        return candidate;
    }

    private String generateRandomPassword(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%";
        Random r = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    // ---------- UI helpers ----------
    private JButton pastelButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(PASTEL_ACCENT);
        b.setForeground(Color.DARK_GRAY);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        return b;
    }

    // ---------- Serialization / Deserialization for subjects ----------

    // ---------- Inner classes ----------
    static class User implements Serializable {
        String username, email, password;
        User(String u, String e, String p) { username = u; email = e; password = p; }
    }

    static class Subject implements Serializable {
        String name;
        double hoursNeeded; // planned hours
        int urgency; // 1-5
        double completionPercent; // 0-100

        Subject(String n, double h, int u, double c) { name = n; hoursNeeded = h; urgency = u; completionPercent = c; }

        @Override
        public String toString() {
            return name + " — " + String.format("%.1fh", hoursNeeded) + " | U:" + urgency + " | " + String.format("%.0f%%", completionPercent);
        }
    }

    private static class SubjectCellRenderer extends JPanel implements ListCellRenderer<Subject> {
        private JLabel title = new JLabel();
        private JLabel meta = new JLabel();
        SubjectCellRenderer() {
            setLayout(new BorderLayout());
            add(title, BorderLayout.CENTER);
            add(meta, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        }
        @Override
        public Component getListCellRendererComponent(JList<? extends Subject> list, Subject value, int index, boolean isSelected, boolean cellHasFocus) {
            title.setText(value.name);
            meta.setText(String.format("%.1fh | U:%d | %.0f%%", value.hoursNeeded, value.urgency, value.completionPercent));
            if (isSelected) setBackground(new Color(255, 222, 235)); else setBackground(new Color(255, 245, 249));
            return this;
        }
    }

    // Subject form dialog
    class SubjectForm extends JDialog {
        boolean ok = false;
        Subject subject;
        JTextField nameF = new JTextField();
        JTextField hoursF = new JTextField();
        JComboBox<Integer> urgBox = new JComboBox<>(new Integer[]{1,2,3,4,5});
        JTextField compF = new JTextField();

        SubjectForm(JFrame owner, Subject existing) {
            super(owner, true);
            setTitle(existing == null ? "Add Subject" : "Edit Subject");
            setSize(400, 280);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout());

            JPanel center = new JPanel(new GridLayout(0,2,6,6));
            center.setBorder(new EmptyBorder(12,12,12,12));
            center.add(new JLabel("Name:")); center.add(nameF);
            center.add(new JLabel("Hours needed:")); center.add(hoursF);
            center.add(new JLabel("Urgency (1-5):")); center.add(urgBox);
            center.add(new JLabel("Completion %:")); center.add(compF);

            if (existing != null) {
                nameF.setText(existing.name);
                hoursF.setText(String.valueOf(existing.hoursNeeded));
                urgBox.setSelectedItem(existing.urgency);
                compF.setText(String.valueOf(existing.completionPercent));
            }

            add(center, BorderLayout.CENTER);
            JPanel bottom = new JPanel();
            JButton okb = pastelButton("OK");
            JButton cancel = pastelButton("Cancel");
            bottom.add(okb); bottom.add(cancel);
            add(bottom, BorderLayout.SOUTH);

            okb.addActionListener(e -> {
                try {
                    String n = nameF.getText().trim();
                    double h = Double.parseDouble(hoursF.getText().trim());
                    int ur = (int)urgBox.getSelectedItem();
                    double cp = Double.parseDouble(compF.getText().trim());
                    if (n.isEmpty()) throw new Exception("Name required");
                    if (existing != null) {
                        existing.name = n; existing.hoursNeeded = h; existing.urgency = ur; existing.completionPercent = cp;
                        subject = existing;
                    } else {
                        subject = new Subject(n, h, ur, cp);
                    }
                    ok = true;
                    setVisible(false);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Invalid input: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
            cancel.addActionListener(e -> setVisible(false));
        }
    }

    // ---------- Main ----------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            StudyPlannerGUI app = new StudyPlannerGUI();
            app.setVisible(true);
        });
    }
}
