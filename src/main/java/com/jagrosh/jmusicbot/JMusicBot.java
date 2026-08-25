/*
 * Copyright 2016 John Grosh (jagrosh).
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

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.audio.VoiceConnectionMonitor;
import com.jagrosh.jmusicbot.commands.v1.CommandFactory;
import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.web.WebPanel;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.gui.MainFrame;
import com.jagrosh.jmusicbot.gui.theme.ThemeManager;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.ConsoleUtil;
import com.jagrosh.jmusicbot.utils.InstanceLock;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.TeeOutputStream;

import java.io.PrintStream;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author John Grosh (jagrosh)
 */
public class JMusicBot 
{
    public final static Logger LOG = LoggerFactory.getLogger(JMusicBot.class);
    public final static Permission[] RECOMMENDED_PERMS = {
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_EMBED_LINKS,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_MANAGE,
            Permission.MESSAGE_EXT_EMOJI,
            Permission.VOICE_CONNECT,
            Permission.VOICE_SPEAK,
            Permission.NICKNAME_CHANGE
    };
    public final static GatewayIntent[] INTENTS = {
            GatewayIntent.DIRECT_MESSAGES,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MESSAGE_REACTIONS,
            GatewayIntent.GUILD_VOICE_STATES,
            GatewayIntent.MESSAGE_CONTENT
    };
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        LaunchOptions options;
        try
        {
            options = LaunchOptions.parse(args);
        }
        catch (IllegalArgumentException ex)
        {
            // Printed rather than thrown: a typo in a flag should produce an explanation,
            // not a stack trace that buries it.
            System.err.println(ex.getMessage());
            System.err.println();
            System.err.println(LaunchOptions.usage());
            System.exit(2);
            return;
        }

        if (options.showHelp())
        {
            System.out.println(LaunchOptions.usage());
            return;
        }

        if (options.generateConfig())
        {
            // Headless prompt for config generation (nogui=true, noprompt=true)
            UserInteraction userInteraction = new Prompt(null, null, true, true);
            BotConfig.writeDefaultConfig(userInteraction);
            return;
        }

        startBot(options);
    }
    
    private static void startBot(LaunchOptions options)
    {
        // Create user interaction handler for startup
        UserInteraction userInteraction = new Prompt("NextVoiceCord");
        
        // Buffer early output when GUI might be used.
        // We don't know if config has gui.enabled=true yet, so buffer when -Dnogui is not set.
        // If GUI ends up disabled by config, we restore original streams; otherwise we replay buffer into GUI.
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        TeeOutputStream teeOut = null;
        TeeOutputStream teeErr = null;
        
        if (!userInteraction.isNoGUI())
        {
            teeOut = new TeeOutputStream(originalOut);
            teeErr = new TeeOutputStream(originalErr);
            System.setOut(new PrintStream(teeOut, true));
            System.setErr(new PrintStream(teeErr, true));
        }
        
        // Check for another running instance
        if (!InstanceLock.tryAcquire()) {
            userInteraction.alert(UserInteraction.Level.ERROR, "NextVoiceCord",
                    "Another instance of NextVoiceCord is already running.\n" +
                    "Running multiple instances with the same configuration causes duplicate responses to commands.\n" +
                    "Please close the other instance first.");
            System.exit(1);
        }
        
        // Startup checks
        OtherUtil.checkVersion(userInteraction);
        OtherUtil.checkJavaVersion(userInteraction);
        
        // Load config
        BotConfig config = new BotConfig(userInteraction);
        config.load();
        if (!config.isValid())
        {
            // Restore original streams before exiting
            if (teeOut != null)
            {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
            return;
        }
        LOG.info("Loaded config from {}", config.getConfigLocation());

        // Set log level from config
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(
                ch.qos.logback.classic.Level.toLevel(config.getLogLevel(), ch.qos.logback.classic.Level.INFO));
        
        // Single source of truth for GUI: combines -Dnogui and config gui.enabled.
        // This matches the logic in Bot.isNoGUI().
        boolean guiEnabled = !userInteraction.isNoGUI() && config.getGuiEnabled();
        
        // Get buffered early output before we change streams again
        String earlyOutput = null;
        if (teeOut != null)
        {
            earlyOutput = teeOut.getBufferedContent();
            teeOut.clearBuffer();
            if (teeErr != null)
            {
                teeErr.clearBuffer();
            }
        }
        
        if (guiEnabled)
        {
            // Initialize theme before creating any Swing components
            try
            {
                ThemeManager.initialize(config.getGuiTheme(), config.getGuiFontSize());
            }
            catch (Exception e)
            {
                LOG.warn("Could not apply theme from config: {}", e.getMessage());
            }
            
            // Redirect streams to GUI console, replaying buffered early output
            try
            {
                ConsoleUtil.redirectSystemStreamsWithReplay(earlyOutput);
            }
            catch (Exception e)
            {
                LOG.warn("Could not redirect console streams to GUI. Logs may not appear in GUI console.");
                // Restore original streams on failure
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
        else
        {
            // GUI disabled: restore original streams (discard buffer)
            if (teeOut != null)
            {
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
        
        // Set up the listener
        EventWaiter waiter = new EventWaiter();
        SettingsManager settings = new SettingsManager();
        Bot bot = new Bot(waiter, config, settings, userInteraction);

        // TrackLoadingMonitor is configured via Bot.getAudioLoadWrapper() (DI pattern).
        VoiceConnectionMonitor.setEnabled(guiEnabled);

        if (guiEnabled)
        {
            try
            {
                MainFrame mainFrame = new MainFrame(bot);
                bot.setGUI(mainFrame);
                mainFrame.init();
            }
            catch (Exception e)
            {
                LOG.error("Could not start GUI. Use -Dnogui=true for server environments.", e);
                userInteraction.alert(UserInteraction.Level.ERROR, "NextVoiceCord",
                        "Could not start GUI.\nUse -Dnogui=true for server environments.");
            }
        }
        
        CommandClient client = CommandFactory.createCommandClient(config, settings, bot);
        bot.setCommandClient(client);

        // Now that GUI/Logging is ready, initialize the player manager
        bot.getPlayerManager().init();

        // attempt to log in and start
        try
        {
            JDA jda = DiscordService.createJDA(config, bot, waiter, client, userInteraction);
            bot.setJDA(jda);

            // Started after the connection, not before: a panel that came up first would
            // spend its first seconds reporting a bot that is in no servers and playing
            // nothing, which reads as broken rather than as still starting.
            options.webPort().ifPresent(port -> {
                WebPanel panel = new WebPanel(bot, port);
                panel.start();
                bot.setWebPanel(panel);
                Runtime.getRuntime().addShutdownHook(new Thread(panel::stop, "web-panel-shutdown"));
            });
        }
        catch(IllegalArgumentException ex)
        {
            userInteraction.alert(UserInteraction.Level.ERROR, "NextVoiceCord",
                    "Invalid configuration. Check your token.\nConfig Location: " + config.getConfigLocation());
            System.exit(1);
        }
        catch(ErrorResponseException ex)
        {
            userInteraction.alert(UserInteraction.Level.ERROR, "NextVoiceCord", "Invalid response from Discord. Check your internet connection.");
            System.exit(1);
        }
        catch(Exception ex)
        {
            LOG.error("An unexpected error occurred during startup", ex);
            System.exit(1);
        }
    }
}
