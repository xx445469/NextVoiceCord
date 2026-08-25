package com.jagrosh.jmusicbot.settings;

import com.jagrosh.jmusicbot.utils.EnumUtil;

public enum NowPlayingButtonsMode
{
    INHERIT("inherit"),
    ON("on"),
    OFF("off");

    private final String userInputValue;

    NowPlayingButtonsMode(String userInputValue)
    {
        this.userInputValue = userInputValue;
    }

    public static NowPlayingButtonsMode valueOfOrDefault(String value, NowPlayingButtonsMode defaultValue)
    {
        return EnumUtil.valueOfOrDefault(NowPlayingButtonsMode.class, value, defaultValue);
    }

    public static NowPlayingButtonsMode fromInput(String input)
    {
        if (input == null)
            return null;
        String normalized = input.trim().toLowerCase();
        for (NowPlayingButtonsMode mode : values())
        {
            if (mode.userInputValue.equals(normalized))
                return mode;
        }
        return null;
    }

    public boolean resolve(boolean globalShowButtons)
    {
        return switch (this) {
            case ON -> true;
            case OFF -> false;
            case INHERIT -> globalShowButtons;
        };
    }

    public String getUserInputValue()
    {
        return userInputValue;
    }
}
