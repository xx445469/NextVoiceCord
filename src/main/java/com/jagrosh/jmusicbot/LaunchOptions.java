/*
 * Copyright 2026 adan (xx445469) - NextVoiceCord
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
package com.jagrosh.jmusicbot;

import java.util.Locale;
import java.util.Optional;

/**
 * Command-line options.
 *
 * <p>Before this existed, the only way to disable the window was
 * {@code java -Dnogui=true -jar ...} — a JVM system property, which has to appear before
 * {@code -jar} and reads like JVM tuning rather than an application setting. {@code --nogui}
 * is what people try first, and it silently did nothing.
 *
 * <p>Parsing still writes the system properties the rest of the codebase reads, so
 * {@code -Dnogui=true} keeps working for anyone whose scripts already use it.
 *
 * @param noGui          run headless, with no Swing window
 * @param webPort        port for the web panel, or empty to leave it off
 * @param generateConfig write a default config file and exit
 * @param showHelp       print usage and exit
 *
 * @author adan (xx445469)
 */
public record LaunchOptions(boolean noGui,
                            Optional<Integer> webPort,
                            boolean generateConfig,
                            boolean showHelp)
{
    /** Lowest port that does not require elevated privileges on Unix. */
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    /**
     * Parses {@code args}, tolerating the spellings people actually type.
     *
     * @throws IllegalArgumentException if an option is malformed, so startup stops with an
     *         explanation rather than silently ignoring what was asked for
     */
    public static LaunchOptions parse(String[] args)
    {
        boolean noGui = isPropertyEnabled("nogui");
        Optional<Integer> webPort = Optional.empty();
        boolean generateConfig = false;
        boolean showHelp = false;

        for (int i = 0; i < args.length; i++)
        {
            String arg = args[i].toLowerCase(Locale.ROOT);

            switch (normalise(arg))
            {
                case "nogui", "headless" -> noGui = true;

                case "web" ->
                {
                    // Read as a separate argument rather than --web=PORT only, because
                    // "--web 8080" is the form people write.
                    if (i + 1 >= args.length)
                    {
                        throw new IllegalArgumentException(
                                "--web needs a port number, for example: --web 8080");
                    }
                    webPort = Optional.of(parsePort(args[++i]));
                }

                case "help", "h", "?" -> showHelp = true;
                case "generate-config" -> generateConfig = true;

                default ->
                {
                    // "--web=8080" and "--web:8080" are common enough to accept.
                    if (arg.startsWith("--web=") || arg.startsWith("--web:"))
                    {
                        webPort = Optional.of(parsePort(args[i].substring(6)));
                    }
                    else
                    {
                        throw new IllegalArgumentException(
                                "Unknown option: " + args[i] + "\nRun with --help to see what is available.");
                    }
                }
            }
        }

        // Written back so Prompt and everything else reading the property see the CLI form
        // too, keeping one source of truth instead of two ways to be headless.
        if (noGui)
        {
            System.setProperty("nogui", "true");
        }

        return new LaunchOptions(noGui, webPort, generateConfig, showHelp);
    }

    /** Strips leading dashes so {@code --nogui}, {@code -nogui} and {@code nogui} all match. */
    private static String normalise(String arg)
    {
        return arg.replaceFirst("^--?", "");
    }

    private static int parsePort(String text)
    {
        int port;
        try
        {
            port = Integer.parseInt(text.trim());
        }
        catch (NumberFormatException ex)
        {
            throw new IllegalArgumentException("'" + text + "' is not a port number.");
        }

        if (port < MIN_PORT || port > MAX_PORT)
        {
            // Ports below 1024 need root on Unix. Failing here with the reason beats binding
            // as root or dying later with an opaque permission error.
            throw new IllegalArgumentException(
                    "Port " + port + " is out of range. Use " + MIN_PORT + "-" + MAX_PORT
                    + "; ports below " + MIN_PORT + " require elevated privileges.");
        }
        return port;
    }

    private static boolean isPropertyEnabled(String name)
    {
        String value = System.getProperty(name);
        return value != null && !"false".equalsIgnoreCase(value);
    }

    /** Usage text, printed for {@code --help} and for a malformed option. */
    public static String usage()
    {
        return """
               NextVoiceCord — a self-hosted Discord music bot

               Usage: java -jar NextVoiceCord.jar [options]

               Options:
                 --nogui               Run headless, without the desktop window.
                                       Use this on a server, or over SSH.
                 --web <port>          Serve the web panel on <port>, reachable in a
                                       browser. Works with or without --nogui.
                 --help                Show this message.
                 generate-config       Write a default config file and exit.

               Examples:
                 java -jar NextVoiceCord.jar
                 java -jar NextVoiceCord.jar --nogui
                 java -jar NextVoiceCord.jar --nogui --web 8080

               The older -Dnogui=true form still works and means the same thing.
               """;
    }
}
