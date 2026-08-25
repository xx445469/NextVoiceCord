package com.jagrosh.jmusicbot.settings;

import com.jagrosh.jmusicbot.utils.EnumUtil;

public enum NowPlayingLayoutMode
{
    INHERIT("inherit"),
    FULL("full"),
    MINIMAL("minimal");

    private final String userInputValue;

    NowPlayingLayoutMode(String userInputValue)
    {
        this.userInputValue = userInputValue;
    }

    public static NowPlayingLayoutMode valueOfOrDefault(String value, NowPlayingLayoutMode defaultValue)
    {
        return EnumUtil.valueOfOrDefault(NowPlayingLayoutMode.class, value, defaultValue);
    }

    public static NowPlayingLayoutMode fromInput(String input)
    {
        if (input == null)
            return null;
        String normalized = input.trim().toLowerCase();
        for (NowPlayingLayoutMode mode : values())
        {
            if (mode.userInputValue.equals(normalized))
                return mode;
        }
        return null;
    }

    public boolean resolve(boolean globalMinimalMessage)
    {
        return switch (this) {
            case MINIMAL -> true;
            case FULL -> false;
            case INHERIT -> globalMinimalMessage;
        };
    }

    public String getUserInputValue()
    {
        return userInputValue;
    }
}
