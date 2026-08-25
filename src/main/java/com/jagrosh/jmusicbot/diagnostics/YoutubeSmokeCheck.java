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
package com.jagrosh.jmusicbot.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.jagrosh.jmusicbot.audio.AudioSource;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.skeleton.Client;

/**
 * Verifies that YouTube playback actually works, end to end.
 *
 * <p>This exists because a {@code youtube-source} upgrade can compile cleanly, pass every
 * unit test, and still be completely broken at runtime — Google changes the player script
 * or the InnerTube contract, and extraction fails only when a real video is fetched.
 * Automated dependency bumps are therefore unsafe without a check at this depth.
 *
 * <p>The check drives the full chain: metadata load &rarr; format selection &rarr; signature
 * cipher &rarr; stream download &rarr; decoded audio frames. Nothing short of decoded frames
 * proves the chain is intact.
 *
 * <h2>Exit codes</h2>
 * <table>
 *   <tr><td>{@code 0}</td><td>PASS — audio frames were decoded.</td></tr>
 *   <tr><td>{@code 1}</td><td>FAIL — extraction is broken. A version bump that produces
 *       this is a genuine regression and must not ship.</td></tr>
 *   <tr><td>{@code 2}</td><td>INCONCLUSIVE — YouTube refused this host (bot check, login
 *       wall, rate limit). Says nothing about the code. Expected on datacenter IPs such as
 *       CI runners, so CI must not read this as a failure.</td></tr>
 * </table>
 *
 * <p>Distinguishing 1 from 2 is the entire point. Treating an IP block as a broken build
 * would make the pipeline cry wolf daily and quickly get ignored.
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -cp NextVoiceCord-All.jar com.jagrosh.jmusicbot.diagnostics.YoutubeSmokeCheck
 *   java -cp NextVoiceCord-All.jar com.jagrosh.jmusicbot.diagnostics.YoutubeSmokeCheck dQw4w9WgXcQ
 * </pre>
 *
 * @author adan (xx445469)
 */
public final class YoutubeSmokeCheck
{
    /** Exit code: the full extraction chain works. */
    public static final int EXIT_PASS = 0;
    /** Exit code: extraction is broken; a bump producing this must not ship. */
    public static final int EXIT_FAIL = 1;
    /** Exit code: YouTube refused this host; the code is not implicated. */
    public static final int EXIT_INCONCLUSIVE = 2;

    /**
     * Long-lived, widely-mirrored videos. Several are used because a single video can fail
     * for reasons of its own — removal, regional restriction, an age gate added later —
     * which would otherwise look identical to broken extraction.
     *
     * <p>Crucially, the set must span both format paths YouTube serves. Some videos hand
     * back a direct stream URL; others return a ciphered URL that must be decoded by
     * executing a function lifted out of YouTube's player script. Signature extraction is
     * the single most fragile part of youtube-source and the part that breaks most often,
     * yet a video on the direct path never touches it. Testing only such a video yields a
     * confident PASS on a build whose cipher handling is entirely broken.
     *
     * <p>This was observed in practice: with youtube-source 1.18.1, {@code dQw4w9WgXcQ}
     * played perfectly while other videos failed with "must find sig function" against
     * player script {@code e937390a}.
     */
    private static final String[] DEFAULT_VIDEO_IDS = {
        "dQw4w9WgXcQ",
        "jNQXAC9IVRw",
        "9bZkp7q19f0",
        "kJQP7kiw5Fk"
    };

    /** Enables remote signature decoding, the same path {@code sources.youtube.useOAuth} takes. */
    public static final String REMOTE_CIPHER_FLAG = "--remote-cipher";

    /** Third-party cipher service the bot uses when OAuth is enabled. */
    private static final String REMOTE_CIPHER_URL = "https://cipher.kikkia.dev/";

    private static final long LOAD_TIMEOUT_SECONDS = 45;
    private static final long PLAYBACK_TIMEOUT_SECONDS = 60;

    /**
     * Error signatures meaning "YouTube refused this host". These track the wording used by
     * YouTube and youtube-source, so they may need updating; an unrecognised error is
     * deliberately treated as a real failure rather than silently excused.
     */
    private static final String[] HOST_REFUSAL_SIGNATURES = {
        "sign in to confirm",
        "not a bot",
        "requires login",
        "login required",
        "429",
        "too many requests",
        "rate limit",
        "consent",
        "age-restricted",
        "age restricted"
    };

    /** Error signatures meaning extraction itself is broken — the regressions worth catching. */
    private static final String[] EXTRACTION_BROKEN_SIGNATURES = {
        "must find sig function",
        "cannot find",
        "could not find",
        "no supported audio streams",
        "failed to load player script",
        "problematic youtube player script",
        "unable to extract",
        "nosuchmethod",
        "noclassdeffound",
        "nosuchfield"
    };

    private YoutubeSmokeCheck() { }

    public static void main(String[] args)
    {
        // --remote-cipher mirrors what enabling OAuth does at runtime: signature decoding is
        // offloaded to a remote service instead of parsing YouTube's player script locally.
        // Having it as a switch is what makes "is local extraction broken, or is the whole
        // chain broken?" answerable in one run instead of by guesswork.
        boolean remoteCipher = List.of(args).contains(REMOTE_CIPHER_FLAG);
        List<String> videoIds = List.of(args).stream()
                                    .filter(arg -> !arg.startsWith("--"))
                                    .toList();
        if (videoIds.isEmpty())
        {
            videoIds = List.of(DEFAULT_VIDEO_IDS);
        }

        System.out.println("=".repeat(72));
        System.out.println("NextVoiceCord — YouTube extraction smoke check");
        System.out.println("youtube-source: " + detectYoutubeSourceVersion());
        System.out.println("cipher:         " + (remoteCipher ? "remote (" + REMOTE_CIPHER_URL + ")" : "local"));
        System.out.println("videos:         " + String.join(", ", videoIds));
        System.out.println("=".repeat(72));

        List<Result> results = new ArrayList<>();
        for (String videoId : videoIds)
        {
            Result result = check(videoId, remoteCipher);
            results.add(result);
            System.out.printf("%n[%s] %s — %s%n", result.status, videoId, result.detail);

            // Every video is checked, even after one succeeds. Stopping early would mean a
            // video on the direct-URL path could report PASS before anything exercised the
            // signature cipher, hiding exactly the breakage this check exists to find.
        }

        System.out.println();
        System.out.println("=".repeat(72));
        int exitCode = summarise(results);
        System.out.println("=".repeat(72));
        System.exit(exitCode);
    }

    /**
     * Reduces per-video results to one verdict.
     *
     * <p>FAIL outranks PASS, which is the opposite of the intuitive reading. A build where
     * some videos play and others die on signature extraction is broken, not healthy: the
     * ones that play simply never needed the code path that is broken. Letting a PASS
     * outvote a FAIL would ship precisely that build.
     */
    private static int summarise(List<Result> results)
    {
        long passed = results.stream().filter(r -> r.status == Status.PASS).count();
        long failed = results.stream().filter(r -> r.status == Status.FAIL).count();
        long refused = results.stream().filter(r -> r.status == Status.INCONCLUSIVE).count();

        System.out.printf("videos: %d passed, %d failed, %d refused%n%n", passed, failed, refused);

        if (failed > 0)
        {
            System.out.println("RESULT: FAIL — extraction is broken.");
            System.out.println();
            if (passed > 0)
            {
                System.out.println("Some videos did play. That is not reassuring: those took the");
                System.out.println("direct-URL path and never exercised the broken code. Videos");
                System.out.println("needing signature decoding fail, so real users hit this.");
                System.out.println();
            }
            System.out.println("Parse/cipher/format errors below are code-level regressions,");
            System.out.println("not access refusals:");
            results.stream()
                   .filter(r -> r.status == Status.FAIL)
                   .forEach(r -> System.out.println("  - " + r.detail));
            return EXIT_FAIL;
        }

        if (passed > 0)
        {
            System.out.println("RESULT: PASS — YouTube extraction is working end to end.");
            if (refused > 0)
            {
                System.out.printf("(%d video(s) were refused by YouTube; the rest played.)%n", refused);
            }
            return EXIT_PASS;
        }

        System.out.println("RESULT: INCONCLUSIVE — YouTube refused this host.");
        System.out.println();
        System.out.println("Every attempt hit a bot check, login wall, or rate limit. That is a");
        System.out.println("property of this IP address, not of the code, and is normal on cloud");
        System.out.println("and CI runners. Re-run from a residential connection to get a verdict.");
        return EXIT_INCONCLUSIVE;
    }

    private static Result check(String videoId, boolean remoteCipher)
    {
        System.out.printf("%n--- checking %s ---%n", videoId);

        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        // PCM output skips the Opus *encoder* native, one less thing to go wrong on a CI
        // runner. Source decoding is unaffected, so extraction is still exercised fully.
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.COMMON_PCM_S16_LE);

        try
        {
            YoutubeSourceOptions options = new YoutubeSourceOptions()
                    .setAllowSearch(true)
                    .setAllowDirectVideoIds(true)
                    .setAllowDirectPlaylistIds(true);

            if (remoteCipher)
            {
                options.setRemoteCipher(REMOTE_CIPHER_URL, null, "nextvoicecord");
            }

            // Deliberately the bot's own client list, not a copy: a duplicated list could
            // drift and let this check pass while real playback fails.
            Client[] clients = AudioSource.buildYoutubeClients(false);
            System.out.println("clients: " + describeClients(clients));

            manager.registerSourceManager(new YoutubeAudioSourceManager(options, clients));

            AudioTrack track = loadTrack(manager, videoId);
            if (track == null)
            {
                return new Result(Status.FAIL, videoId + ": no track returned from load");
            }
            System.out.println("loaded:  " + track.getInfo().title);

            return playbackCheck(manager, track, videoId);
        }
        catch (Exception ex)
        {
            return classify(videoId, "unexpected " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
        finally
        {
            manager.shutdown();
        }
    }

    private static AudioTrack loadTrack(DefaultAudioPlayerManager manager, String videoId)
    {
        AtomicReference<AudioTrack> loaded = new AtomicReference<>();
        AtomicReference<String> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        manager.loadItem(videoId, new AudioLoadResultHandler()
        {
            @Override
            public void trackLoaded(AudioTrack track)
            {
                loaded.set(track);
                latch.countDown();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist)
            {
                if (!playlist.getTracks().isEmpty())
                {
                    loaded.set(playlist.getTracks().get(0));
                }
                latch.countDown();
            }

            @Override
            public void noMatches()
            {
                failure.set("no matches");
                latch.countDown();
            }

            @Override
            public void loadFailed(FriendlyException ex)
            {
                failure.set(rootMessage(ex));
                latch.countDown();
            }
        });

        try
        {
            if (!latch.await(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            {
                System.out.println("load:    timed out after " + LOAD_TIMEOUT_SECONDS + "s");
                return null;
            }
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            return null;
        }

        if (failure.get() != null)
        {
            System.out.println("load:    FAILED — " + failure.get());
        }
        return loaded.get();
    }

    /**
     * Plays the track until real audio frames arrive. Loading a track only proves metadata
     * was readable; the signature cipher and format resolution do not run until playback
     * starts, and those are exactly what upstream changes tend to break.
     */
    private static Result playbackCheck(DefaultAudioPlayerManager manager, AudioTrack track, String videoId)
    {
        AudioPlayer player = manager.createPlayer();
        AtomicReference<String> trackError = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);

        player.addListener(new AudioEventAdapter()
        {
            @Override
            public void onTrackException(AudioPlayer p, AudioTrack t, FriendlyException ex)
            {
                trackError.set(rootMessage(ex));
                finished.countDown();
            }

            @Override
            public void onTrackEnd(AudioPlayer p, AudioTrack t, AudioTrackEndReason reason)
            {
                if (reason != AudioTrackEndReason.STOPPED)
                {
                    finished.countDown();
                }
            }
        });

        try
        {
            player.playTrack(track);

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(PLAYBACK_TIMEOUT_SECONDS);
            int frames = 0;

            while (System.nanoTime() < deadline)
            {
                if (trackError.get() != null)
                {
                    System.out.println("play:    FAILED — " + trackError.get());
                    return classify(videoId, trackError.get());
                }

                if (player.provide() != null && ++frames >= 5)
                {
                    // Five frames is ~100ms of audio: enough to prove the stream is genuinely
                    // decoding, not just that a connection opened.
                    System.out.println("play:    OK — decoded " + frames + " audio frames");
                    return new Result(Status.PASS, videoId + ": decoded audio successfully");
                }

                if (finished.await(50, TimeUnit.MILLISECONDS) && frames == 0)
                {
                    break;
                }
            }

            String reason = trackError.get() != null
                    ? trackError.get()
                    : "no audio frames within " + PLAYBACK_TIMEOUT_SECONDS + "s";
            System.out.println("play:    FAILED — " + reason);
            return classify(videoId, reason);
        }
        catch (InterruptedException ie)
        {
            Thread.currentThread().interrupt();
            return new Result(Status.FAIL, videoId + ": interrupted");
        }
        finally
        {
            player.destroy();
        }
    }

    /**
     * Decides whether an error means "broken code" or "refused host".
     *
     * <p>Extraction signatures are checked <em>first</em>, and the ordering is load-bearing.
     * youtube-source reports a failure as a single {@code AllClientsFailedException} whose
     * message concatenates what every client said, so one message routinely carries both
     * kinds of wording at once — a real case looked like:
     *
     * <pre>
     *   Client [ANDROID_VR]     failed: This video requires login.
     *   Client [MWEB]           failed: Must find sig function from script: ...
     *   Client [WEB]            failed: Must find sig function from script: ...
     *   Client [TVHTML5_SIMPLY] failed: Sign in to confirm you're not a bot
     * </pre>
     *
     * <p>Matching refusal wording first would label that INCONCLUSIVE and wave a genuinely
     * broken build through. Individual clients being refused is routine; a parse or cipher
     * error is never routine, so its presence anywhere in the bundle is the real signal.
     *
     * <p>An unrecognised error falls through to {@link Status#FAIL}, so novel breakage is
     * surfaced rather than quietly excused.
     */
    private static Result classify(String videoId, String message)
    {
        String haystack = message == null ? "" : message.toLowerCase(Locale.ROOT);

        for (String signature : EXTRACTION_BROKEN_SIGNATURES)
        {
            if (haystack.contains(signature))
            {
                return new Result(Status.FAIL, videoId + ": extraction broken (" + signature + ")");
            }
        }

        for (String signature : HOST_REFUSAL_SIGNATURES)
        {
            if (haystack.contains(signature))
            {
                return new Result(Status.INCONCLUSIVE, videoId + ": host refused (" + signature + ")");
            }
        }

        return new Result(Status.FAIL, videoId + ": unrecognised failure — " + message);
    }

    private static String rootMessage(Throwable ex)
    {
        StringBuilder sb = new StringBuilder();
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth++ < 8)
        {
            if (current.getMessage() != null)
            {
                sb.append(current.getMessage()).append(' ');
            }
            current = current.getCause();
        }
        return sb.toString().trim();
    }

    private static String describeClients(Client[] clients)
    {
        List<String> names = new ArrayList<>();
        for (Client client : clients)
        {
            names.add(client.getIdentifier());
        }
        return String.join(", ", names);
    }

    /**
     * Best-effort version read; purely informational, so failure to detect is not fatal.
     *
     * <p>youtube-source ships its version as a {@code yts-version.txt} resource. The package
     * manifest is not usable here: after shading, {@code getImplementationVersion()} returns
     * the enclosing bot's version, which reads plausibly and is simply wrong.
     */
    private static String detectYoutubeSourceVersion()
    {
        try (java.io.InputStream in =
                     YoutubeSmokeCheck.class.getClassLoader().getResourceAsStream("yts-version.txt"))
        {
            if (in != null)
            {
                String version = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!version.isEmpty())
                {
                    return version;
                }
            }
        }
        catch (Exception ignored)
        {
            // fall through
        }
        return "unknown";
    }

    private enum Status { PASS, FAIL, INCONCLUSIVE }

    private record Result(Status status, String detail) { }
}
