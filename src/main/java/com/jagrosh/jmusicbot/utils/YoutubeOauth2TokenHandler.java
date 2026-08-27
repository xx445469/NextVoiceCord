package com.jagrosh.jmusicbot.utils;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A logback turbo filter, used retrieve the YouTube OAuth2 refresh token that gets logged once authorized with YouTube.
 *
 * <p>Also the single source of truth for the "Sign in to YouTube" button (see {@code ConfigPanel}
 * and the web panel's {@code /api/youtube-oauth}): both learn a code has arrived, that the flow
 * finished, or that it failed by registering a {@link Listener} here rather than polling. {@link
 * #decide} runs on whatever thread logback is invoking the turbo filter from — not the Swing
 * event thread and not an HTTP handler thread — so every listener callback happens there too;
 * callers that need to touch Swing marshal onto the EDT themselves rather than this class doing
 * it for every caller including ones that do not need it.
 *
 * @author Michaili K. <git@michaili.dev>
 */
public class YoutubeOauth2TokenHandler extends TurboFilter {
    public final static org.slf4j.Logger LOGGER = LoggerFactory.getLogger(YoutubeOauth2TokenHandler.class);
    private static final String TOKEN_FILE = "youtubetoken.txt";

    private volatile Data data;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void init()
    {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.addTurboFilter(this);
    }

    public Data getData()
    {
        return data;
    }

    /** Registers a listener for code-ready/signed-in/failed. Never replaces one already added. */
    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public void removeListener(Listener listener)
    {
        listeners.remove(listener);
    }

    /** Whether a refresh token has already been stored, i.e. whether the bot is already signed in. */
    public static boolean tokenFileExists()
    {
        return Files.exists(OtherUtil.getPath(TOKEN_FILE));
    }

    /**
     * Deletes the stored refresh token, so the next boot has to sign in again.
     *
     * @return true if a file was actually deleted; false if there was nothing to delete
     */
    public static boolean deleteStoredToken() throws java.io.IOException
    {
        return Files.deleteIfExists(OtherUtil.getPath(TOKEN_FILE));
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t)
    {
        if (!logger.getName().equals("dev.lavalink.youtube.http.YoutubeOauth2Handler"))
            return FilterReply.NEUTRAL;

        if (format.equals("OAUTH INTEGRATION: To give youtube-source access to your account, go to {} and enter code {}"))
        {
            this.data = new Data((String) params[0], (String) params[1]);
            notifyCodeReady(this.data);
            return FilterReply.NEUTRAL;
        }
        if (format.equals("OAUTH INTEGRATION: Token retrieved successfully. Store your refresh token as this can be reused. ({})"))
        {
            LOGGER.info(
                "Authorization successful & retrieved token! Storing the token in {}",
                OtherUtil.getPath(TOKEN_FILE).toAbsolutePath()
            );

            try
            {
                Files.write(OtherUtil.getPath(TOKEN_FILE), params[0].toString().getBytes());
                // The code that got us here is spent either way — successfully exchanged for a
                // token or not, it will not work a second time, so there is nothing left for the
                // button to show once this happens.
                this.data = null;
                notifySignedIn();
            }
            catch (Exception e)
            {
                LOGGER.error(
                    "Failed to write the YouTube OAuth2 refresh token to storage! You will need to authorize again on the next reboot",
                    e
                );
                notifyFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            return FilterReply.DENY;
        }

        return FilterReply.NEUTRAL;
    }

    private void notifyCodeReady(Data data)
    {
        for (Listener listener : listeners)
        {
            safely(() -> listener.onCodeReady(data));
        }
    }

    private void notifySignedIn()
    {
        for (Listener listener : listeners)
        {
            safely(listener::onSignedIn);
        }
    }

    private void notifyFailed(String message)
    {
        for (Listener listener : listeners)
        {
            safely(() -> listener.onFailed(message));
        }
    }

    /** A listener throwing must never break log processing for every other turbo filter. */
    private void safely(Runnable action)
    {
        try
        {
            action.run();
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("A YouTube sign-in listener threw while handling an update", e);
        }
    }

    /** Told about a code arriving, the flow finishing, or the flow failing — never polled for. */
    public interface Listener
    {
        default void onCodeReady(Data data) { }

        default void onSignedIn() { }

        default void onFailed(String message) { }
    }

    public static class Data
    {
        private final String authorisationUrl;
        private final String code;

        private Data(String authorisationUrl, String code)
        {
            this.authorisationUrl = authorisationUrl;
            this.code = code;
        }

        public String getCode()
        {
            return code;
        }

        public String getAuthorisationUrl()
        {
            return authorisationUrl;
        }
    }

}
