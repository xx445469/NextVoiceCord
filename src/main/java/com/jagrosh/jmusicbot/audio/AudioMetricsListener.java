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

/**
 * Listener interface for audio performance metrics events.
 * Used by AudioHandler to report audio events for performance monitoring.
 * 
 * <p>When GUI is enabled, {@link PerformanceMetrics} implements this interface
 * to collect metrics. When GUI is disabled, the {@link #NO_OP} singleton is used
 * to avoid any overhead.
 *
 * @author Arif Banai (arif-banai)
 */
public interface AudioMetricsListener {
    
    /**
     * Called when an audio frame is requested.
     *
     * @param provided true if audio frame was available, false if missed
     * @param latencyNanos time taken to get frame from Lavaplayer in nanoseconds
     */
    void onFrameProvided(boolean provided, long latencyNanos);
    
    /**
     * Called when a track starts playing.
     *
     * @param title the track title (may be null)
     * @param uri the track URI (may be null)
     */
    void onTrackStart(String title, String uri);
    
    /**
     * Called when a track ends.
     *
     * @param title the track title (may be null)
     * @param uri the track URI (may be null)
     */
    void onTrackEnd(String title, String uri);
    
    /**
     * Called when a track throws an exception.
     *
     * @param title the track title (may be null)
     * @param uri the track URI (may be null)
     */
    void onTrackException(String title, String uri);
    
    /**
     * Called when a track gets stuck.
     *
     * @param thresholdMs the threshold (in ms) that was exceeded
     * @param title the track title (may be null)
     * @param uri the track URI (may be null)
     */
    void onTrackStuck(long thresholdMs, String title, String uri);
    
    /**
     * Called when the session should be reset (typically when a new track starts).
     */
    void onSessionReset();
    
    /**
     * No-op implementation for when metrics collection is disabled (no-GUI mode).
     * All methods are empty and SHOULD be inlined by the JIT compiler.
     */
    AudioMetricsListener NO_OP = new AudioMetricsListener() {
        @Override
        public void onFrameProvided(boolean provided, long latencyNanos) {}
        
        @Override
        public void onTrackStart(String title, String uri) {}
        
        @Override
        public void onTrackEnd(String title, String uri) {}
        
        @Override
        public void onTrackException(String title, String uri) {}
        
        @Override
        public void onTrackStuck(long thresholdMs, String title, String uri) {}
        
        @Override
        public void onSessionReset() {}
    };
}
