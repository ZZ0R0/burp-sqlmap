package com.example.sqlmap;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.ui.contextmenu.ContextMenuEvent;
import burp.api.montoya.ui.contextmenu.ContextMenuItemsProvider;

import javax.swing.*;
import java.awt.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;

public class SqlmapExtension implements BurpExtension, ContextMenuItemsProvider {
    private MontoyaApi api;
    private Logging log;
    private SqlmapPanel panel;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;
        this.log = api.logging();
        panel = new SqlmapPanel(this::runSqlmap, this::stopAll);
        api.userInterface().registerSuiteTab("sqlmap", panel);
        api.userInterface().registerContextMenuItemsProvider(this);
        api.extension().setName("Send to sqlmap");
        log.raiseInfoEvent("sqlmap GUI ready");
    }

    @Override
    public List<Component> provideMenuItems(ContextMenuEvent e) {
        List<HttpRequestResponse> sel = e.selectedRequestResponses();
        if (sel == null || sel.isEmpty()) return List.of();
        JMenuItem send = new JMenuItem("Send to sqlmap (GUI)");
        send.addActionListener(ae -> panel.fillFromRequest(sel.get(0).request()));
        return List.of(send);
    }

    private Path writeRawRequestFromText(String rawText) throws IOException {
        String norm = normalizeRawHttp(rawText);
        Path dir = Files.createTempDirectory("burp-sqlmap-");
        Path f = dir.resolve("request.txt");
        Files.write(f, norm.getBytes(StandardCharsets.ISO_8859_1),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return f;
    }

    private void runSqlmap(SqlmapSettings s) {
        try {
            Path reqFile = null;
            if (s.mode == SqlmapSettings.Mode.RAW) {
                String raw = (s.rawEditedText != null && !s.rawEditedText.isBlank())
                        ? s.rawEditedText
                        : (s.rawRequest != null ? s.rawRequest.toByteArray().toString() : "");
                if (raw.isBlank()) {
                    panel.append("[warn] RAW mode selected but no request text available.");
                    return;
                }
                reqFile = writeRawRequestFromText(raw);

                // Debug preview of the exact request that will be used by sqlmap
                panel.append("[debug] temp request: " + reqFile);
                panel.append("[debug] --- begin request preview ---");
                try {
                    var lines = Files.readAllLines(reqFile, StandardCharsets.ISO_8859_1);
                    for (int i = 0; i < Math.min(lines.size(), 40); i++) panel.append(lines.get(i));
                } catch (Exception ignored) {}
                panel.append("[debug] --- end request preview ---");
            }

            StringBuilder cmd = new StringBuilder();
            cmd.append(escape(s.sqlmapPath)).append(" ");

            if (s.mode == SqlmapSettings.Mode.RAW && reqFile != null) {
                cmd.append("-r ").append(escape(reqFile.toString())).append(" ");
            } else if (s.mode == SqlmapSettings.Mode.URL && s.url != null && !s.url.isBlank()) {
                cmd.append("-u ").append(escape(s.url)).append(" ");
            }

            cmd.append("--level=").append(s.level).append(" ");
            cmd.append("--risk=").append(s.risk).append(" ");
            if (!s.paramsInclude.isBlank()) cmd.append("-p ").append(escape(s.paramsInclude)).append(" ");
            if (!s.paramsSkip.isBlank())    cmd.append("--skip=").append(escape(s.paramsSkip)).append(" ");
            if (!s.techniques.isBlank())    cmd.append("--technique=").append(s.techniques).append(" ");
            if (!s.dbms.isBlank())          cmd.append("--dbms=").append(s.dbms).append(" ");
            if (!s.tamper.isBlank())        cmd.append("--tamper=").append(s.tamper).append(" ");

            if (s.batch)         cmd.append("--batch ");
            if (s.smart)         cmd.append("--smart ");
            if (s.forceSSL)      cmd.append("--force-ssl ");
            if (s.randomAgent)   cmd.append("--random-agent ");
            if (s.threads > 1)   cmd.append("--threads=").append(s.threads).append(" ");
            if (s.crawlDepth > 0)cmd.append("--crawl=").append(s.crawlDepth).append(" ");
            if (s.timeoutSec > 0)cmd.append("--timeout=").append(s.timeoutSec).append(" ");
            if (s.delaySec > 0)  cmd.append("--delay=").append(s.delaySec).append(" ");
            if (!s.proxy.isBlank()) cmd.append("--proxy=").append(s.proxy).append(" ");
            if (s.flushSession)  cmd.append("--flush-session ");
            if (s.dump)          cmd.append("--dump ");
            if (s.osShell)       cmd.append("--os-shell ");
            if (!s.outputDir.isBlank()) cmd.append("--output-dir=").append(escape(s.outputDir)).append(" ");
            cmd.append("--disable-coloring");

            String commandStr = cmd.toString().trim();

            if (s.externalTerminalEnabled()) {
                ProcessBuilder pb = new ProcessBuilder("bash", "-lc",
                        s.externalTerminalCmd.replace("{cmd}", commandStr));
                panel.runProcess(pb, "[ext]");
            } else {
                ProcessBuilder pb = new ProcessBuilder("bash", "-lc", commandStr);
                panel.runProcess(pb, "[sqlmap]");
            }
        } catch (Exception ex) {
            log.logToError(ex);
            panel.append("[error] " + ex.getMessage());
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        if (s.matches("^[A-Za-z0-9_./,:-]+$")) return s;
        return "'" + s.replace("'", "'\"'\"'") + "'";
    }

    private void stopAll() { panel.stopAll(); }

    // Ensure exactly one CRLF-CRLF separator and strip extra blank lines before body
    private static String normalizeRawHttp(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\r\n", "\n");
        int idx = s.indexOf("\n\n");
        if (idx >= 0) {
            String headers = s.substring(0, idx);
            String body = s.substring(idx + 2).replaceFirst("^[\\n]+", "");
            s = headers + "\n\n" + body;
        }
        return s.replace("\n", "\r\n");
    }
}
