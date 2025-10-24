package com.example.sqlmap;

import burp.api.montoya.http.message.requests.HttpRequest;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class SqlmapPanel extends JPanel {
    private final JTextField tfSqlmap = new JTextField("sqlmap");

    private final JRadioButton rbRaw = new JRadioButton("Raw -r", true);
    private final JRadioButton rbUrl = new JRadioButton("URL -u");
    private final JTextArea taRawPreview = new JTextArea(12, 80); // editable
    private HttpRequest captured;
    private final JTextField tfUrl = new JTextField();

    private final JSpinner spLevel = new JSpinner(new SpinnerNumberModel(2, 1, 5, 1));
    private final JSpinner spRisk  = new JSpinner(new SpinnerNumberModel(2, 0, 3, 1));
    private final JTextField tfTech = new JTextField("");
    private final JTextField tfDbms = new JTextField("");
    private final JTextField tfTamper = new JTextField("");
    private final JTextField tfParamInclude = new JTextField(""); // -p
    private final JTextField tfParamSkip    = new JTextField(""); // --skip

    private final JCheckBox cbBatch = new JCheckBox("--batch", true);
    private final JCheckBox cbSmart = new JCheckBox("--smart", true);
    private final JCheckBox cbSSL = new JCheckBox("--force-ssl");
    private final JCheckBox cbRA = new JCheckBox("--random-agent");

    private final JSpinner spThreads = new JSpinner(new SpinnerNumberModel(1, 1, 10, 1));
    private final JSpinner spCrawl   = new JSpinner(new SpinnerNumberModel(0, 0, 10, 1));
    private final JSpinner spTimeout = new JSpinner(new SpinnerNumberModel(30, 1, 600, 5));
    private final JSpinner spDelay   = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 10.0, 0.1));
    private final JTextField tfProxy = new JTextField("");
    private final JCheckBox cbFlush  = new JCheckBox("--flush-session");
    private final JCheckBox cbDump   = new JCheckBox("--dump");
    private final JCheckBox cbShell  = new JCheckBox("--os-shell");

    private final JTextField tfOutDir = new JTextField("");
    private final JTextField tfExtTerm = new JTextField("");

    private final JTextArea console = new JTextArea(12, 100);
    private final JButton btnRun = smallButton("Run");
    private final JButton btnStopAll = smallButton("Stop all");
    private final JButton btnFixLen = smallButton("Recalc Content-Length");
    private final JButton btnExtract = smallButton("Extract params");
    private final JProgressBar progress = new JProgressBar();

    private final JTextArea taCmdPreview = new JTextArea(2, 100);

    private final List<Process> running = new CopyOnWriteArrayList<>();
    private final Runner runHandler;
    private final Stopper stopHandler;

    interface Runner { void run(SqlmapSettings s); }
    interface Stopper { void stop(); }

    SqlmapPanel(Runner runner, Stopper stopper) {
        super(new BorderLayout(6,6));
        this.runHandler = runner;
        this.stopHandler = stopper;

        // Top controls
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints g = gbc();
        addLine(top, g, new JLabel("sqlmap path"), tfSqlmap);

        ButtonGroup bg = new ButtonGroup();
        bg.add(rbRaw); bg.add(rbUrl);
        JPanel rawBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        rawBtns.add(btn("Paste raw from context", e -> pasteRaw()));
        rawBtns.add(btnExtract);
        rawBtns.add(btnFixLen);
        addLine(top, g, rbRaw, rawBtns);
        addLine(top, g, rbUrl, tfUrl);

        // Tooltips
        spLevel.setToolTipText("--level (1..5)");
        spRisk.setToolTipText("--risk (0..3)");
        tfTech.setToolTipText("--technique, e.g. BEUSTQ");
        tfDbms.setToolTipText("--dbms");
        tfTamper.setToolTipText("--tamper, comma-separated");
        tfParamInclude.setToolTipText("-p param1,param2   (only test these)");
        tfParamSkip.setToolTipText("--skip=param1,param2   (exclude these)");
        spThreads.setToolTipText("--threads 1..10");
        spCrawl.setToolTipText("--crawl");
        spTimeout.setToolTipText("--timeout seconds");
        spDelay.setToolTipText("--delay seconds");
        tfProxy.setToolTipText("--proxy http://127.0.0.1:8081");
        tfOutDir.setToolTipText("--output-dir");
        tfExtTerm.setToolTipText("External terminal; {cmd} substituted");

        for (JSpinner sp : List.of(spLevel, spRisk, spThreads, spCrawl, spTimeout)) sp.setPreferredSize(new Dimension(70, 24));
        spDelay.setPreferredSize(new Dimension(80, 24));

        // Raw preview (editable)
        taRawPreview.setEditable(true);
        taRawPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        taRawPreview.setBorder(new TitledBorder("Raw request (editable)"));

        // Options
        JPanel opts1 = form("Core",
                row("level", spLevel),
                row("risk",  spRisk),
                row("technique", tfTech),
                row("dbms", tfDbms),
                row("tamper", tfTamper),
                row("params -p", tfParamInclude),
                row("skip", tfParamSkip),
                row(cbBatch, cbSmart),
                row(cbSSL, cbRA)
        );

        JPanel opts2 = form("Runtime",
                row("threads",    spThreads),
                row("crawl depth",spCrawl),
                row("timeout s",  spTimeout),
                row("delay s",    spDelay),
                row("proxy",      tfProxy),
                row(cbFlush, cbDump),
                row(cbShell, new JLabel())
        );

        JPanel outp = form("Output",
                row("output dir", tfOutDir),
                row("external terminal cmd", tfExtTerm)
        );

        JPanel mid = new JPanel(new GridLayout(1,3,8,8));
        mid.add(opts1); mid.add(opts2); mid.add(outp);

        JSplitPane splitTop = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(taRawPreview), mid);
        splitTop.setResizeWeight(0.55);
        splitTop.setDividerSize(6);

        // Toolbar
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.add(btnRun);
        bar.add(btnStopAll);
        progress.setIndeterminate(false);
        progress.setPreferredSize(new Dimension(120, 14));
        bar.addSeparator();
        bar.add(progress);

        // Bottom
        taCmdPreview.setEditable(false);
        taCmdPreview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        taCmdPreview.setBorder(new TitledBorder("Command preview"));
        console.setEditable(false);
        console.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JSplitPane bottomSplit = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(taCmdPreview),
                new JScrollPane(console));
        bottomSplit.setResizeWeight(0.35);
        bottomSplit.setDividerSize(6);
        bottomSplit.setPreferredSize(new Dimension(10, 220));
        bottomSplit.setMinimumSize(new Dimension(10, 160));

        JPanel southStack = new JPanel(new BorderLayout(6,6));
        southStack.add(bar, BorderLayout.NORTH);
        southStack.add(bottomSplit, BorderLayout.CENTER);
        southStack.setMinimumSize(new Dimension(10, 180));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitTop, southStack);
        mainSplit.setResizeWeight(0.85);
        mainSplit.setDividerSize(6);

        add(top, BorderLayout.NORTH);
        add(mainSplit, BorderLayout.CENTER);

        // Listeners
        btnFixLen.addActionListener(e -> recalcContentLength());
        btnExtract.addActionListener(e -> tfParamInclude.setText(String.join(",", extractParamsFromRaw())));

        for (var c : List.of(tfSqlmap, tfUrl, tfTech, tfDbms, tfTamper, tfParamInclude, tfParamSkip, tfProxy, tfOutDir, tfExtTerm))
            c.getDocument().addDocumentListener(SimpleDocListener.onChange(this::refreshPreview));
        for (var c : List.of(rbRaw, rbUrl, cbBatch, cbSmart, cbSSL, cbRA, cbFlush, cbDump, cbShell))
            c.addChangeListener(e -> refreshPreview());
        for (var c : List.of(spLevel, spRisk, spThreads, spCrawl, spTimeout, spDelay))
            c.addChangeListener(e -> refreshPreview());

        btnRun.addActionListener(e -> run());
        btnStopAll.addActionListener(e -> stopAll());

        detectDefaultTerminal();
        SwingUtilities.invokeLater(() -> mainSplit.setDividerLocation(0.85));
        refreshPreview();
    }

    void fillFromRequest(HttpRequest req) {
        this.captured = req;
        rbRaw.setSelected(true);
        taRawPreview.setText(req.toByteArray().toString());
        tfParamInclude.setText(String.join(",", extractParamsFromRaw()));
        consoleAppend("[info] request captured; you can edit it. Use Extract params to update -p.");
        refreshPreview();
    }

    void runProcess(ProcessBuilder pb, String prefix) {
        try {
            Process p = pb.start();
            running.add(p);
            progress.setIndeterminate(true);
            Thread t = new Thread(() -> {
                try (var r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) consoleAppend(prefix + " " + line);
                } catch (Exception ex) {
                    consoleAppend("[error] " + ex.getMessage());
                } finally {
                    try { int code = p.waitFor(); consoleAppend(prefix + " exit " + code); }
                    catch (InterruptedException ignored) {}
                    running.remove(p);
                    if (running.isEmpty()) SwingUtilities.invokeLater(() -> progress.setIndeterminate(false));
                }
            });
            t.setDaemon(true);
            t.start();
        } catch (Exception ex) {
            consoleAppend("[error] " + ex.getMessage());
        }
    }

    void stopAll() {
        for (Process p : running) p.destroyForcibly();
        running.clear();
        progress.setIndeterminate(false);
        if (stopHandler != null) stopHandler.stop();
        consoleAppend("[info] stopped all");
    }

    private void run() {
        autoFixContentLengthIfMismatch();
        SqlmapSettings s = collect();
        if (s.mode == SqlmapSettings.Mode.RAW && (s.rawEditedText == null || s.rawEditedText.isBlank())) {
            consoleAppend("[warn] RAW selected but the request text is empty.");
            return;
        }
        if (!s.paramsInclude.isBlank()) consoleAppend("[info] -p " + s.paramsInclude);
        runHandler.run(s);
    }

    private SqlmapSettings collect() {
        SqlmapSettings s = new SqlmapSettings();
        s.sqlmapPath = tfSqlmap.getText().trim();
        s.mode = rbUrl.isSelected() ? SqlmapSettings.Mode.URL : SqlmapSettings.Mode.RAW;
        s.rawRequest = captured;
        s.rawEditedText = taRawPreview.getText();
        s.url = tfUrl.getText().trim();

        s.level = (Integer) spLevel.getValue();
        s.risk  = (Integer) spRisk.getValue();
        s.techniques = tfTech.getText().trim();
        s.dbms = tfDbms.getText().trim();
        s.tamper = tfTamper.getText().trim();
        s.paramsInclude = tfParamInclude.getText().trim();
        s.paramsSkip    = tfParamSkip.getText().trim();

        s.batch = cbBatch.isSelected();
        s.smart = cbSmart.isSelected();
        s.forceSSL = cbSSL.isSelected();
        s.randomAgent = cbRA.isSelected();

        s.threads = (Integer) spThreads.getValue();
        s.crawlDepth = (Integer) spCrawl.getValue();
        s.timeoutSec = (Integer) spTimeout.getValue();
        s.delaySec = ((Double) spDelay.getValue()).floatValue();
        s.proxy = tfProxy.getText().trim();
        s.flushSession = cbFlush.isSelected();
        s.dump = cbDump.isSelected();
        s.osShell = cbShell.isSelected();

        s.outputDir = tfOutDir.getText().trim();
        s.externalTerminalCmd = tfExtTerm.getText().trim();
        return s;
    }

    private void refreshPreview() {
        SqlmapSettings s = collect();
        StringBuilder cmd = new StringBuilder();
        cmd.append(escape(s.sqlmapPath)).append(" ");
        if (s.mode == SqlmapSettings.Mode.RAW) {
            cmd.append("-r <temp_request.txt> ");
        } else if (!s.url.isBlank()) {
            cmd.append("-u ").append(escape(s.url)).append(" ");
        }
        cmd.append("--level=").append(s.level).append(" ");
        cmd.append("--risk=").append(s.risk).append(" ");
        if (!s.paramsInclude.isBlank()) cmd.append("-p ").append(escape(s.paramsInclude)).append(" ");
        if (!s.paramsSkip.isBlank())    cmd.append("--skip=").append(escape(s.paramsSkip)).append(" ");
        if (!s.techniques.isBlank())    cmd.append("--technique=").append(s.techniques).append(" ");
        if (!s.dbms.isBlank())          cmd.append("--dbms=").append(s.dbms).append(" ");
        if (!s.tamper.isBlank())        cmd.append("--tamper=").append(s.tamper).append(" ");
        if (s.batch)                    cmd.append("--batch ");
        if (s.smart)                    cmd.append("--smart ");
        if (s.forceSSL)                 cmd.append("--force-ssl ");
        if (s.randomAgent)              cmd.append("--random-agent ");
        if (s.threads > 1)              cmd.append("--threads=").append(s.threads).append(" ");
        if (s.crawlDepth > 0)           cmd.append("--crawl=").append(s.crawlDepth).append(" ");
        if (s.timeoutSec > 0)           cmd.append("--timeout=").append(s.timeoutSec).append(" ");
        if (s.delaySec > 0)             cmd.append("--delay=").append(s.delaySec).append(" ");
        if (!s.proxy.isBlank())         cmd.append("--proxy=").append(s.proxy).append(" ");
        if (s.flushSession)             cmd.append("--flush-session ");
        if (s.dump)                     cmd.append("--dump ");
        if (s.osShell)                  cmd.append("--os-shell ");
        if (!s.outputDir.isBlank())     cmd.append("--output-dir=").append(escape(s.outputDir)).append(" ");
        cmd.append("--disable-coloring");
        taCmdPreview.setText(cmd.toString().trim());
    }

    private void recalcContentLength() {
        String raw = taRawPreview.getText();
        if (raw == null) return;

        // Normalize separator to exactly one blank line
        String s = raw.replace("\r\n", "\n");
        int idx = s.indexOf("\n\n");
        if (idx < 0) { consoleAppend("[warn] cannot find header/body separator"); return; }
        String headers = s.substring(0, idx);
        String body = s.substring(idx + 2).replaceFirst("^[\\n]+", "");

        int len = body.getBytes(StandardCharsets.ISO_8859_1).length;
        String crlf = "\r\n";
        String[] lines = headers.split("\n", -1);
        StringBuilder newHeaders = new StringBuilder();
        boolean hadCL = false;
        for (String line : lines) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                if (!hadCL) { newHeaders.append("Content-Length: ").append(len).append(crlf); hadCL = true; }
            } else {
                newHeaders.append(line).append(crlf);
            }
        }
        if (!hadCL) newHeaders.append("Content-Length: ").append(len).append(crlf);
        taRawPreview.setText(newHeaders.toString() + crlf + body);
        consoleAppend("[info] Content-Length recalculated to " + len);
    }

    private void autoFixContentLengthIfMismatch() {
        String[] hb = splitHeadersBody(taRawPreview.getText());
        if (hb == null) return;
        String headers = hb[0], body = hb[1];
        String crlf = headers.contains("\r\n") ? "\r\n" : "\n";
        int actual = body.getBytes(StandardCharsets.ISO_8859_1).length;
        Integer stated = findContentLength(headers);
        if (stated == null || stated != actual) recalcContentLength();
    }

    private Integer findContentLength(String headers) {
        String crlf = headers.contains("\r\n") ? "\r\n" : "\n";
        for (String line : headers.split(crlf)) {
            if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                String v = line.substring(line.indexOf(':')+1).trim();
                try { return Integer.parseInt(v); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private String[] splitHeadersBody(String raw) {
        if (raw == null) return null;
        int pos = raw.indexOf("\r\n\r\n");
        int alt = raw.indexOf("\n\n");
        int idx = pos >= 0 ? pos : alt;
        if (idx < 0) return null;
        String sep = (pos >= 0) ? "\r\n\r\n" : "\n\n";
        return new String[]{ raw.substring(0, idx), raw.substring(idx + sep.length()) };
    }

    private Set<String> extractParamsFromRaw() {
        String raw = taRawPreview.getText();
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) return out;

        String[] hb = splitHeadersBody(raw);
        String headers = hb != null ? hb[0] : raw;
        String body = hb != null ? hb[1] : "";

        String crlf = headers.contains("\r\n") ? "\r\n" : "\n";
        String start = headers.split(crlf, 2)[0];

        // GET query
        int sp1 = start.indexOf(' ');
        int sp2 = start.indexOf(' ', sp1 + 1);
        if (sp1 > 0 && sp2 > sp1) {
            String path = start.substring(sp1 + 1, sp2);
            int q = path.indexOf('?');
            if (q >= 0 && q < path.length() - 1) addPairs(path.substring(q + 1), out);
        }
        // Cookie header
        for (String line : headers.split(crlf)) {
            if (line.toLowerCase(Locale.ROOT).startsWith("cookie:")) {
                String cookie = line.substring(line.indexOf(':') + 1).trim();
                for (String kv : cookie.split(";")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) out.add(kv.substring(0, eq).trim());
                }
            }
        }
        // POST body (x-www-form-urlencoded)
        String ct = headerValue(headers, "content-type");
        if (ct != null && ct.toLowerCase(Locale.ROOT).contains("application/x-www-form-urlencoded")) {
            addPairs(body, out);
        }
        return out;
    }

    private static void addPairs(String data, Set<String> out) {
        for (String kv : data.split("&")) {
            int eq = kv.indexOf('=');
            String name = eq >= 0 ? kv.substring(0, eq) : kv;
            try { name = URLDecoder.decode(name, StandardCharsets.UTF_8); } catch (Exception ignored) {}
            name = name.trim();
            if (!name.isEmpty()) out.add(name);
        }
    }

    private static String headerValue(String headers, String key) {
        String crlf = headers.contains("\r\n") ? "\r\n" : "\n";
        String k = key.toLowerCase(Locale.ROOT) + ":";
        for (String line : headers.split(crlf)) {
            if (line.toLowerCase(Locale.ROOT).startsWith(k)) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return null;
    }

    private void detectDefaultTerminal() {
        if (tfExtTerm.getText().isBlank()) {
            tfExtTerm.setText("x-terminal-emulator -e bash -lc \"{cmd}; echo; read -n1 -p 'done'\"");
        }
    }

    private static JButton smallButton(String text) {
        JButton b = new JButton(text);
        b.setMargin(new Insets(2,8,2,8));
        b.setFont(b.getFont().deriveFont(12f));
        return b;
    }
    private static JButton btn(String label, java.awt.event.ActionListener l) {
        JButton b = smallButton(label); b.addActionListener(l); return b;
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx=0; g.gridy=0; g.weightx=1; g.weighty=0;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(4,4,0,4);
        return g;
    }
    private static void addLine(JPanel p, GridBagConstraints g, JComponent left, JComponent right) {
        GridBagConstraints g1 = (GridBagConstraints) g.clone();
        g1.gridx = 0; g1.weightx = 0.25;
        p.add(left, g1);
        GridBagConstraints g2 = (GridBagConstraints) g.clone();
        g2.gridx = 1; g2.weightx = 0.75;
        p.add(right, g2);
        g.gridy++;
    }

    private static JPanel form(String title, JComponent[]... rows) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new TitledBorder(title));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(2,2,2,2);
        g.fill = GridBagConstraints.HORIZONTAL;
        int y = 0;
        for (JComponent[] r : rows) {
            g.gridx = 0; g.gridy = y; g.weightx = 0.35; panel.add(r[0], g);
            g.gridx = 1; g.gridy = y; g.weightx = 0.65; panel.add(r[1], g);
            y++;
        }
        return panel;
    }
    private static JComponent[] row(Object left, Object right) {
        return new JComponent[] { asComp(left), asComp(right) };
    }
    private static JComponent asComp(Object o) { return (o instanceof JComponent) ? (JComponent)o : new JLabel(String.valueOf(o)); }

    private void pasteRaw() {
        if (captured == null) {
            consoleAppend("[warn] no captured request. Use context menu on a request.");
        } else {
            taRawPreview.setText(captured.toByteArray().toString());
            tfParamInclude.setText(String.join(",", extractParamsFromRaw()));
            refreshPreview();
        }
    }

    void append(String s) { consoleAppend(s); }
    private void consoleAppend(String s) {
        SwingUtilities.invokeLater(() -> {
            console.append(s);
            console.append("\n");
            console.setCaretPosition(console.getDocument().getLength());
        });
    }

    private String escape(String s) {
        if (s == null) return "";
        if (s.matches("^[A-Za-z0-9_./,:-]+$")) return s;
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private static class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable r;
        private SimpleDocListener(Runnable r){ this.r = r; }
        static javax.swing.event.DocumentListener onChange(Runnable r){ return new SimpleDocListener(r); }
        public void insertUpdate(javax.swing.event.DocumentEvent e){ r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e){ r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e){ r.run(); }
    }
}
