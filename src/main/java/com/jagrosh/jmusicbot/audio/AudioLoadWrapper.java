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
package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;

/**
 * Interface for wrapping audio load result handlers to add monitoring/metrics.
 * When GUI is disabled, the NO_OP implementation passes through the delegate directly.
 *
 * @author Arif Banai (arif-banai)
 */
public interface AudioLoadWrapper {
    
    /**
     * Wraps an AudioLoadResultHandler to optionally add monitoring.
     *
     * @param query the search query or URL being loaded
     * @param delegate the original handler to delegate to
     * @return a wrapped handler (or the delegate directly for NO_OP)
     */
    AudioLoadResultHandler wrap(String query, AudioLoadResultHandler delegate);
    
    /**
     * No-operation wrapper that passes through the delegate without monitoring.
     * Used when GUI is disabled to avoid allocating monitoring resources.
     */
    AudioLoadWrapper NO_OP = (query, delegate) -> delegate;
}
