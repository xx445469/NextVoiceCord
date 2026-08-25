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
package com.jagrosh.jmusicbot.commands.v2.admin;

import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.i18n.Language;

import net.dv8tion.jda.api.components.MessageTopLevelComponent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;

/**
 * The half of {@code /settings} that belongs to one person rather than the server.
 *
 * <p>Language used to be its own command. It does not deserve one: nobody goes looking for a
 * verb when what they want is a setting, and a server with two settings commands invites the
 * question of which one has the thing you are after.
 *
 * <p>Which view opens first depends on what the person can actually do. Someone who can manage
 * the server lands on the server's settings, because that is the view only they can reach and
 * the reason they ran the command. Everyone else lands here, because the server view would be
 * a wall of controls that reject them.
 *
 * @author adan (xx445469)
 */
public final class UserSettingsPanelRenderer
{
    /** Distinct from {@code settings_}: that prefix is gated on Manage Server, and this is not. */
    public static final String PREFIX = "usersettings_";

    public static final String SCOPE_SERVER = "server";
    public static final String SCOPE_PERSONAL = "personal";

    /** Chosen in the language menu to go back to following the server. */
    public static final String FOLLOW_SERVER = "__follow__";

    private UserSettingsPanelRenderer() { }

    public static String scopeSelectId(long userId)
    {
        return PREFIX + "scope_" + userId;
    }

    public static String languageSelectId(long userId)
    {
        return PREFIX + "lang_" + userId;
    }

    public static String closeButtonId(long userId)
    {
        return PREFIX + "close_" + userId;
    }

    /** Reads the trailing user id out of any of the ids above. */
    public static java.util.OptionalLong userIdOf(String componentId)
    {
        int at = componentId.lastIndexOf('_');
        if (at < 0 || at + 1 >= componentId.length())
        {
            return java.util.OptionalLong.empty();
        }
        try
        {
            return java.util.OptionalLong.of(Long.parseLong(componentId.substring(at + 1)));
        }
        catch (NumberFormatException ex)
        {
            return java.util.OptionalLong.empty();
        }
    }

    /**
     * The personal view.
     *
     * @param canManage whether to offer a way back to the server view — someone who cannot
     *                  manage the server has nothing to switch to, and a menu whose only other
     *                  entry refuses them is worse than no menu
     */
    public static List<MessageTopLevelComponent> buildPersonalMessageComponents(
            Bot bot, Guild guild, User user, boolean canManage)
    {
        long userId = user.getIdLong();
        Language personal = bot.getUserLanguages().get(userId).orElse(null);
        Language serverLanguage = bot.getSettingsManager().getSettings(guild).getLanguage(bot.getConfig());

        String state = personal == null
                ? bot.msgFor(guild, user, "settings.personal.language.following", serverLanguage.getNativeName())
                : bot.msgFor(guild, user, "settings.personal.language.chosen", personal.getNativeName());

        List<ContainerChildComponent> children = new ArrayList<>(List.of(
                TextDisplay.of("## " + bot.msgFor(guild, user, "settings.personal.title")),
                TextDisplay.of(bot.msgFor(guild, user, "settings.personal.description")),
                Separator.createDivider(Separator.Spacing.SMALL),
                TextDisplay.of(bot.msgFor(guild, user, "settings.personal.language.label") + ": " + state),
                ActionRow.of(languageSelect(bot, guild, user, personal, serverLanguage))
        ));

        if (canManage)
        {
            children.add(Separator.createDivider(Separator.Spacing.SMALL));
            children.add(ActionRow.of(scopeSelect(bot, guild, user, SCOPE_PERSONAL)));
        }

        children.add(ActionRow.of(Button.danger(closeButtonId(userId),
                bot.msgFor(guild, user, "settings.panel.button.close"))));

        return List.of(Container.of(children));
    }

    /**
     * The menu that moves between the two views.
     *
     * <p>Only ever attached for people who can manage the server, so both entries lead
     * somewhere they are allowed to go.
     */
    public static StringSelectMenu scopeSelect(Bot bot, Guild guild, User user, String currentScope)
    {
        return StringSelectMenu.create(scopeSelectId(user.getIdLong()))
                .setPlaceholder(bot.msgFor(guild, user, "settings.scope.placeholder"))
                .addOption(bot.msgFor(guild, user, "settings.scope.server"),
                           SCOPE_SERVER,
                           bot.msgFor(guild, user, "settings.scope.serverHint"))
                .addOption(bot.msgFor(guild, user, "settings.scope.personal"),
                           SCOPE_PERSONAL,
                           bot.msgFor(guild, user, "settings.scope.personalHint"))
                .setDefaultValues(currentScope)
                .build();
    }

    /**
     * Every language that loaded, each labelled in itself.
     *
     * <p>日本語 rather than Japanese: someone escaping a language they cannot read has to
     * recognise their own to get out of it.
     */
    private static StringSelectMenu languageSelect(
            Bot bot, Guild guild, User user, Language personal, Language serverLanguage)
    {
        StringSelectMenu.Builder menu = StringSelectMenu.create(languageSelectId(user.getIdLong()))
                .setPlaceholder(bot.msgFor(guild, user, "settings.personal.language.placeholder"));

        menu.addOption(bot.msgFor(guild, user, "settings.personal.language.followOption"),
                       FOLLOW_SERVER,
                       bot.msgFor(guild, user, "settings.personal.language.followHint",
                                  serverLanguage.getNativeName()));

        // Only languages whose file actually loaded. Offering one that is missing would let
        // someone pick a language that then renders entirely in English.
        for (Language option : bot.getLanguages().getAvailableLanguages())
        {
            menu.addOption(option.getNativeName(), option.name(), option.getEnglishName());
        }

        menu.setDefaultValues(personal == null ? FOLLOW_SERVER : personal.name());
        return menu.build();
    }
}
