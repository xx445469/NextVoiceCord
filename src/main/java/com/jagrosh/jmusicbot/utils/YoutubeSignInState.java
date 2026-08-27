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
package com.jagrosh.jmusicbot.utils;

/**
 * What the "Sign in to YouTube" button (desktop window and web panel alike) should show right
 * now, and why.
 *
 * <p>Kept free of Swing, HTTP and the filesystem on purpose: both surfaces render the same
 * handful of outcomes from the same set of facts, and a plain, dependency-free class is what lets
 * those outcomes be asserted in a test without standing up a window or a server. The facts
 * themselves — is OAuth turned on, does {@code youtubetoken.txt} exist, has a code arrived — are
 * read by the caller ({@code ConfigPanel}, {@code WebData}) and handed to {@link #compute} below.
 *
 * <h2>Phases</h2>
 * <ul>
 *   <li>{@link Phase#OAUTH_DISABLED} — {@code playback.youtube.useOAuth} is off. There is nothing
 *       this button can start until that changes and the bot restarts.</li>
 *   <li>{@link Phase#SIGNED_IN} — {@code youtubetoken.txt} exists, whether because a previous run
 *       finished the flow or because it just did. A fresh sign-in is not needed; signing out is
 *       offered instead.</li>
 *   <li>{@link Phase#WAITING_FOR_CODE} — OAuth is on, nothing is signed in, and youtube-source has
 *       not logged a device code yet. Waiting, not broken.</li>
 *   <li>{@link Phase#CODE_READY} — a code arrived. {@link #url()} already carries it (see
 *       {@link YoutubeDeviceCodeUrl}), and {@link #code()} is shown alongside a copy button
 *       regardless, because the browser may not open and the pre-fill may not survive a
 *       redirect.</li>
 *   <li>{@link Phase#BROWSER_OPENED} — the sign-in button was pressed and a browser was opened,
 *       or, if that failed, the code was put on the clipboard instead ({@link #clipboardFallback()}
 *       says which). Either way the code stays visible until the flow actually finishes.</li>
 *   <li>{@link Phase#FAILED} — the sign-in was attempted and did not complete; {@link #message()}
 *       says why.</li>
 * </ul>
 *
 * @author adan (xx445469)
 */
public final class YoutubeSignInState
{
    public enum Phase
    {
        OAUTH_DISABLED,
        SIGNED_IN,
        WAITING_FOR_CODE,
        CODE_READY,
        BROWSER_OPENED,
        FAILED
    }

    private final Phase phase;
    private final String url;
    private final String code;
    private final boolean clipboardFallback;
    private final String message;

    private YoutubeSignInState(Phase phase, String url, String code, boolean clipboardFallback, String message)
    {
        this.phase = phase;
        this.url = url;
        this.code = code;
        this.clipboardFallback = clipboardFallback;
        this.message = message;
    }

    public Phase phase()
    {
        return phase;
    }

    /** The verification URL with the code pre-filled, non-null only for {@code CODE_READY}/{@code BROWSER_OPENED}. */
    public String url()
    {
        return url;
    }

    /** The raw code, non-null only for {@code CODE_READY}/{@code BROWSER_OPENED}. */
    public String code()
    {
        return code;
    }

    /** Whether the last sign-in attempt fell back to the clipboard rather than opening a browser. Only meaningful for {@code BROWSER_OPENED}. */
    public boolean clipboardFallback()
    {
        return clipboardFallback;
    }

    /** Why {@code FAILED} happened. Null for every other phase. */
    public String message()
    {
        return message;
    }

    // ==================== Computed from the ground truth ====================

    /**
     * The phase implied by the current facts, with no memory of any button that was pressed.
     *
     * <p>{@code tokenExists} is checked before {@code pending}: a code that arrived before a
     * restart but was never used is not worth showing once a token is already on disk — someone
     * who is already signed in should see that, not a stale code.
     */
    public static YoutubeSignInState compute(boolean oauthEnabled, boolean tokenExists, String pendingUrl, String pendingCode)
    {
        if (!oauthEnabled)
        {
            return disabled();
        }
        if (tokenExists)
        {
            return signedIn();
        }
        if (pendingUrl != null && !pendingUrl.isBlank() && pendingCode != null && !pendingCode.isBlank())
        {
            return codeReady(pendingUrl, pendingCode);
        }
        return waitingForCode();
    }

    // ==================== Factories ====================

    public static YoutubeSignInState disabled()
    {
        return new YoutubeSignInState(Phase.OAUTH_DISABLED, null, null, false, null);
    }

    public static YoutubeSignInState signedIn()
    {
        return new YoutubeSignInState(Phase.SIGNED_IN, null, null, false, null);
    }

    public static YoutubeSignInState waitingForCode()
    {
        return new YoutubeSignInState(Phase.WAITING_FOR_CODE, null, null, false, null);
    }

    public static YoutubeSignInState codeReady(String url, String code)
    {
        return new YoutubeSignInState(Phase.CODE_READY, YoutubeDeviceCodeUrl.withCode(url, code), code, false, null);
    }

    /**
     * The device flow is moving on to (or back to) {@code BROWSER_OPENED}.
     *
     * <p>Unlike {@link #codeReady}, {@code url} is taken as-is rather than run through {@link
     * YoutubeDeviceCodeUrl#withCode} again — the caller already has the pre-filled URL from
     * whatever state it is transitioning out of (typically {@link #codeReady}'s own {@link
     * #url()}), and re-appending {@code user_code} a second time would duplicate the parameter.
     */
    public static YoutubeSignInState browserOpened(String url, String code, boolean clipboardFallback)
    {
        return new YoutubeSignInState(Phase.BROWSER_OPENED, url, code, clipboardFallback, null);
    }

    public static YoutubeSignInState failed(String message)
    {
        return new YoutubeSignInState(Phase.FAILED, null, null, false, message);
    }
}
