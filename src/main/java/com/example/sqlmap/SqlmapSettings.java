package com.example.sqlmap;

import burp.api.montoya.http.message.requests.HttpRequest;

public class SqlmapSettings {
    enum Mode { RAW, URL }
    Mode mode = Mode.RAW;

    String sqlmapPath = "sqlmap";

    // RAW mode
    HttpRequest rawRequest;        // captured (optional)
    String rawEditedText = "";     // edited text from GUI

    // URL mode
    String url = "";

    // Core
    int level = 2;   // 1..5
    int risk  = 2;   // 0..3
    String techniques = ""; // e.g. BEUSTQ
    String dbms = "";
    String tamper = "";
    String paramsInclude = ""; // -p
    String paramsSkip    = ""; // --skip

    boolean batch = true;
    boolean smart = true;
    boolean forceSSL = false;
    boolean randomAgent = false;

    // Runtime
    int threads = 1;
    int crawlDepth = 0;
    int timeoutSec = 30;
    float delaySec = 0f;
    String proxy = "";
    boolean flushSession = false;
    boolean dump = false;
    boolean osShell = false;

    // Output / terminal
    String outputDir = "";
    String externalTerminalCmd = ""; // e.g. x-terminal-emulator -e bash -lc "{cmd}; read -n1"
    boolean externalTerminalEnabled() { return externalTerminalCmd != null && !externalTerminalCmd.isBlank(); }
}
