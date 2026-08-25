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
package com.jagrosh.jmusicbot.listener;

import java.util.OptionalLong;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.admin.SettingsPanelRenderer;
import com.jagrosh.jmusicbot.commands.v2.admin.UserSettingsPanelRenderer;
import com.jagrosh.jmusicbot.i18n.Language;
import com.jagrosh.jmusicbot.settings.Settings;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import org.jetbrains.annotations.NotNull;

/**
 * Drives the personal half of {@code /settings}, and the menu that moves between the two halves.
 *
 * <p>Kept apart from {@link SettingsInteractionListener} on purpose. That one answers to the
 * {@code settings_} prefix and gates every interaction on Manage Server, which is right for
 * settings that affect everyone. These controls change one person's own preference, so they
 * must not inherit that gate — and the surest way to guarantee that is for them never to reach
 * the code that applies it.
 *
 * @author adan (xx445469)
 */
public class UserSettingsInteractionListener extends ListenerAdapter
{
    private final Bot bot;

    public UserSettingsInteractionListener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event)
    {
        String id = event.getComponentId();
        if (!id.startsWith(UserSettingsPanelRenderer.PREFIX) || !isOwner(event.getComponentId(), event))
        {
            return;
        }

        if (id.startsWith(UserSettingsPanelRenderer.PREFIX + "lang_"))
        {
            applyLanguage(event);
        }
        else if (id.startsWith(UserSettingsPanelRenderer.PREFIX + "scope_"))
        {
            switchScope(event);
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event)
    {
        String id = event.getComponentId();
        if (!id.startsWith(UserSettingsPanelRenderer.PREFIX + "close_") || !isOwner(id, event))
        {
            return;
        }
        event.deferEdit().queue(hook -> hook.deleteOriginal().queue(null, error -> { }));
    }

    /**
     * Rejects anyone but the person the panel was opened for.
     *
     * <p>The panel is ephemeral, so this should not come up — but an id is guessable and a
     * component can outlive the message it was attached to, and nothing here should be
     * changeable by a bystander.
     */
    private boolean isOwner(String componentId,
                            net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent event)
    {
        OptionalLong owner = UserSettingsPanelRenderer.userIdOf(componentId);
        if (owner.isEmpty())
        {
            return false;
        }
        if (owner.getAsLong() == event.getUser().getIdLong())
        {
            return true;
        }
        event.reply(bot.msg(event.getGuild(), "common.errors.notPanelOwner")).setEphemeral(true).queue();
        return false;
    }

    private void applyLanguage(StringSelectInteractionEvent event)
    {
        String chosen = event.getValues().isEmpty() ? null : event.getValues().get(0);
        if (chosen == null)
        {
            return;
        }

        long userId = event.getUser().getIdLong();

        if (UserSettingsPanelRenderer.FOLLOW_SERVER.equals(chosen))
        {
            // null is how the store spells "no preference", which is not the same as English.
            bot.getUserLanguages().set(userId, null);
        }
        else
        {
            Language language = Language.fromCode(chosen).orElse(null);
            if (language == null || !bot.getLanguages().getAvailableLanguages().contains(language))
            {
                // Reachable from a stale client holding a menu built by an older version.
                event.reply(bot.getConfig().getError() + " "
                            + bot.msgFor(event.getGuild(), event.getUser(),
                                         "settings.language.invalid", chosen, availableCodes()))
                     .setEphemeral(true).queue();
                return;
            }
            bot.getUserLanguages().set(userId, language);
        }

        // Redrawn after the change, so the panel comes back in the new language. That is the
        // shortest possible proof it took effect.
        rerenderPersonal(event);
    }

    private void switchScope(StringSelectInteractionEvent event)
    {
        String scope = event.getValues().isEmpty() ? null : event.getValues().get(0);

        if (UserSettingsPanelRenderer.SCOPE_PERSONAL.equals(scope))
        {
            rerenderPersonal(event);
            return;
        }

        if (!UserSettingsPanelRenderer.SCOPE_SERVER.equals(scope))
        {
            return;
        }

        if (!canManage(event))
        {
            event.reply(bot.getConfig().getError() + " "
                        + bot.msgFor(event.getGuild(), event.getUser(),
                                     "permissions.errors.needManageServer"))
                 .setEphemeral(true).queue();
            return;
        }

        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());
        String invokerName = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getUser().getName();

        event.editMessage(new MessageEditBuilder()
                .setComponents(SettingsPanelRenderer.buildSettingsMessageComponents(
                        bot, event.getGuild(), settings, bot.getConfig(),
                        event.getUser().getIdLong(), invokerName, event.getUser()))
                .useComponentsV2()
                .build()).queue();
    }

    private void rerenderPersonal(StringSelectInteractionEvent event)
    {
        event.editMessage(new MessageEditBuilder()
                .setComponents(UserSettingsPanelRenderer.buildPersonalMessageComponents(
                        bot, event.getGuild(), event.getUser(), canManage(event)))
                .useComponentsV2()
                .build()).queue();
    }

    private boolean canManage(net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent event)
    {
        return event.getMember() != null && event.getMember().hasPermission(Permission.MANAGE_SERVER);
    }

    private String availableCodes()
    {
        return bot.getLanguages().getAvailableLanguages().stream()
                  .map(Language::name)
                  .collect(java.util.stream.Collectors.joining(", "));
    }
}
