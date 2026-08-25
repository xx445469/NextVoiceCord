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
package com.jagrosh.jmusicbot.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Slows down anyone guessing the panel's token.
 *
 * <p>The token is 32 random bytes, so guessing it is not a realistic attack on its own. This
 * exists because the panel now writes {@code config.txt}: the cost of a successful guess went
 * from reading status to editing the bot's configuration, and a control that is cheap to add
 * belongs on the expensive side of that change rather than the cheap one.
 *
 * <p>Counted per client address. A shared address means one clumsy person can lock out another
 * behind the same NAT, which is the right trade — the alternative is a single global counter
 * that anyone can trip for everyone.
 *
 * <p>Not persisted. A restart issues a new token anyway, so carrying a lockout across one would
 * punish an address for guessing a token that no longer exists.
 *
 * @author adan (xx445469)
 */
final class WebRateLimit
{
    /** Generous: a person retyping a token from a console gets several attempts. */
    private static final int MAX_FAILURES = 8;

    private static final Duration LOCKOUT = Duration.ofMinutes(15);

    /** Bounded so a flood of spoofed source addresses cannot grow this without limit. */
    private static final int MAX_TRACKED = 4096;

    private final Map<String, Attempts> byAddress = new ConcurrentHashMap<>();

    /** Whether this address is currently locked out. */
    boolean isLockedOut(String address)
    {
        Attempts attempts = byAddress.get(address);
        if (attempts == null)
        {
            return false;
        }
        if (Duration.between(attempts.lastFailure, Instant.now()).compareTo(LOCKOUT) > 0)
        {
            byAddress.remove(address);
            return false;
        }
        return attempts.count.get() >= MAX_FAILURES;
    }

    /** Records a rejected token. Returns true if that just tripped the lockout. */
    boolean recordFailure(String address)
    {
        if (byAddress.size() >= MAX_TRACKED)
        {
            // Drop everything rather than evicting one entry: entries expire on their own, and
            // the only way to reach this size is an attack that a partial clear would not slow.
            byAddress.clear();
        }
        Attempts attempts = byAddress.computeIfAbsent(address, key -> new Attempts());
        attempts.lastFailure = Instant.now();
        return attempts.count.incrementAndGet() == MAX_FAILURES;
    }

    /** Clears the count after a request authenticates. */
    void recordSuccess(String address)
    {
        byAddress.remove(address);
    }

    private static final class Attempts
    {
        private final AtomicInteger count = new AtomicInteger();
        private volatile Instant lastFailure = Instant.now();
    }
}
