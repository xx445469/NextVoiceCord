/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.utils;

import java.io.PrintStream;
import javax.swing.*;

import com.jagrosh.jmusicbot.gui.TextAreaOutputStream;


/**
 * Utility class for redirecting System.out and System.err to a GUI text area.
 * This allows early redirection during startup so that logs appear in the GUI console.
 * 
 * <p>Supports buffer-and-replay: early startup output can be captured in a buffer
 * (via {@link TeeOutputStream}) and replayed into the GUI console when the GUI
 * is initialized.</p>
 *
 * @author Arif Banai (arif-banai)
 */
public class ConsoleUtil {
    private static PrintStream consoleStream;
    private static JTextArea sharedTextArea;
    
    /**
     * Redirects System.out and System.err to a GUI text area.
     * This can be called early in startup (before GUI is fully initialized)
     * to capture logs that occur during configuration loading.
     * 
     * @return The JTextArea that will receive the console output
     */
    public static JTextArea redirectSystemStreams() {
        return redirectSystemStreamsWithReplay(null);
    }
    
    /**
     * Redirects System.out and System.err to a GUI text area, first replaying
     * any buffered early output into the text area.
     * 
     * <p>Use this when early startup logs were captured via {@link TeeOutputStream}
     * before the GUI was initialized. The buffered content is appended to the
     * text area before redirection begins, so all logs appear in order.</p>
     * 
     * @param earlyOutput Buffered output from early startup (may be null or empty)
     * @return The JTextArea that will receive the console output
     */
    public static JTextArea redirectSystemStreamsWithReplay(String earlyOutput) {
        if (sharedTextArea == null) {
            sharedTextArea = new JTextArea();
            sharedTextArea.setLineWrap(true);
            sharedTextArea.setWrapStyleWord(true);
            sharedTextArea.setEditable(false);
            
            // Replay buffered early output before redirecting streams
            if (earlyOutput != null && !earlyOutput.isEmpty()) {
                sharedTextArea.append(earlyOutput);
            }
            
            consoleStream = new PrintStream(new TextAreaOutputStream(sharedTextArea));
            System.setOut(consoleStream);
            System.setErr(consoleStream);
        }
        return sharedTextArea;
    }
    
    /**
     * Gets the shared text area if redirection has been set up, or null otherwise.
     * 
     * @return The shared JTextArea, or null if redirection hasn't been initialized
     */
    public static JTextArea getSharedTextArea() {
        return sharedTextArea;
    }
}
