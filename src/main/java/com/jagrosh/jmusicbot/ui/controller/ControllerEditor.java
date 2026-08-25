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
package com.jagrosh.jmusicbot.ui.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.Bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

/**
 * The editor for a guild's controller layout.
 *
 * <p>Discord offers no drag-and-drop, so the editor is built from what it does offer: the
 * layout is shown as text, the buttons it produces are rendered live beneath it, and a modal
 * accepts a replacement. Seeing the real buttons matters more than any amount of description
 * — a layout is judged by how it looks, and a preview that is merely a list of names cannot
 * be judged at all.
 *
 * <p>The preview is rendered by the same code that renders the panel, so what is shown is
 * what will be used. A separate preview renderer would be a second implementation to keep in
 * step, and would quietly diverge.
 *
 * @author adan (xx445469)
 */
public final class ControllerEditor
{
    /** Component id prefix, so the listener can tell these apart from panel buttons. */
    public static final String PREFIX = "ctled_";

    public static final String ACTION_EDIT = PREFIX + "edit";
    public static final String ACTION_RESET = PREFIX + "reset";
    public static final String ACTION_REFRESH = PREFIX + "refresh";
    public static final String MODAL_ID = PREFIX + "modal";
    public static final String INPUT_ID = PREFIX + "json";

    /** Discord's modal inputs cap at 4000 characters, which bounds an editable layout. */
    private static final int MAX_LAYOUT_LENGTH = 4000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ControllerEditor() { }

    /** Builds the editor message. */
    public static MessageCreateData build(Bot bot, Guild guild)
    {
        MessageCreateBuilder mb = new MessageCreateBuilder();
        mb.setEmbeds(buildEmbed(bot, guild));

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                Button.primary(ACTION_EDIT, bot.msg(guild, "controller.editor.button.edit")),
                Button.secondary(ACTION_REFRESH, bot.msg(guild, "controller.editor.button.refresh")),
                Button.danger(ACTION_RESET, bot.msg(guild, "controller.editor.button.reset"))));

        // The live preview. Its ids belong to the real panel, so they are deliberately not
        // wired here — the listener ignores presses from this message.
        rows.addAll(previewRows(bot, guild));

        mb.setComponents(rows);
        return mb.build();
    }

    /** Rebuilds the editor in place, for after an edit. */
    public static MessageEditData rebuild(Bot bot, Guild guild)
    {
        MessageEditBuilder mb = new MessageEditBuilder();
        mb.setEmbeds(buildEmbed(bot, guild));

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                Button.primary(ACTION_EDIT, bot.msg(guild, "controller.editor.button.edit")),
                Button.secondary(ACTION_REFRESH, bot.msg(guild, "controller.editor.button.refresh")),
                Button.danger(ACTION_RESET, bot.msg(guild, "controller.editor.button.reset"))));
        rows.addAll(previewRows(bot, guild));

        mb.setComponents(rows);
        return mb.build();
    }

    private static net.dv8tion.jda.api.entities.MessageEmbed buildEmbed(Bot bot, Guild guild)
    {
        boolean custom = bot.getSettingsManager().getSettings(guild).getControllerLayoutJson() != null;

        return new EmbedBuilder()
                .setTitle(bot.msg(guild, "controller.editor.title"))
                .setDescription(bot.msg(guild, "controller.editor.description"))
                .addField(bot.msg(guild, "controller.editor.field.source"),
                          bot.msg(guild, custom
                                  ? "controller.editor.source.custom"
                                  : "controller.editor.source.default"),
                          false)
                .addField(bot.msg(guild, "controller.editor.field.layout"),
                          "```json\n" + truncate(currentJson(bot, guild)) + "\n```",
                          false)
                .setColor(0x5865F2)
                .build();
    }

    /**
     * The buttons this layout produces, disabled.
     *
     * <p>Disabled because they are a picture of the panel rather than the panel: pressing
     * skip from inside the editor should not skip a track.
     */
    private static List<ActionRow> previewRows(Bot bot, Guild guild)
    {
        ControllerRenderer renderer = new ControllerRenderer(
                "ctlpreview_",
                Map.of("volume", "100", "queue_length", "3", "loop_mode", "Off"),
                (key, args) -> bot.msg(guild, key, args));

        ControllerRenderer.PlaybackState sample =
                new ControllerRenderer.PlaybackState(false, true, true, true, true, false, "off");

        List<ActionRow> preview = new ArrayList<>();
        ControllerLayout layout = bot.getSettingsManager().getSettings(guild).getControllerLayout();

        for (ActionRow row : renderer.render(layout, sample))
        {
            List<Button> disabled = new ArrayList<>();
            row.getComponents().forEach(component ->
            {
                if (component instanceof Button button)
                {
                    disabled.add(button.asDisabled());
                }
            });
            if (!disabled.isEmpty())
            {
                preview.add(ActionRow.of(disabled));
            }
        }

        // Discord allows five rows per message and the controls take one, so a five-row
        // layout would push the preview past the limit and the message would be rejected.
        int room = ControllerLayout.MAX_ROWS - 1;
        return preview.size() > room ? preview.subList(0, room) : preview;
    }

    /** The modal for replacing the layout, pre-filled with what is in use. */
    public static Modal buildModal(Bot bot, Guild guild)
    {
        TextInput input = TextInput.create(INPUT_ID, TextInputStyle.PARAGRAPH)
                .setRequired(false)
                .setValue(truncate(currentJson(bot, guild)))
                .setPlaceholder(bot.msg(guild, "controller.editor.modal.placeholder"))
                .setMaxLength(MAX_LAYOUT_LENGTH)
                .build();

        return Modal.create(MODAL_ID, bot.msg(guild, "controller.editor.modal.title"))
                .addComponents(Label.of(bot.msg(guild, "controller.editor.modal.label"), input))
                .build();
    }

    /** Outcome of applying a submitted layout. */
    public record ApplyResult(boolean accepted, String messageKey, List<String> warnings) { }

    /**
     * Validates and stores a submitted layout.
     *
     * <p>Warnings are surfaced rather than swallowed. The parser is forgiving by design, so
     * without them a typo would be silently dropped and the editor would show a layout that
     * does not match what was typed, with no indication why.
     */
    public static ApplyResult apply(Bot bot, Guild guild, String json)
    {
        if (json == null || json.isBlank())
        {
            bot.getSettingsManager().getSettings(guild).setControllerLayoutJson(null);
            return new ApplyResult(true, "controller.editor.reset", List.of());
        }

        JsonNode parsed;
        try
        {
            parsed = MAPPER.readTree(json);
        }
        catch (Exception ex)
        {
            return new ApplyResult(false, "controller.editor.errors.invalidJson", List.of());
        }

        if (!parsed.isArray())
        {
            return new ApplyResult(false, "controller.editor.errors.notAnArray", List.of());
        }

        List<String> warnings = new ArrayList<>();
        ControllerLayout layout = ControllerLayout.parse(parsed, warnings::add);

        if (layout.getRows().isEmpty())
        {
            return new ApplyResult(false, "controller.editor.errors.noButtons", warnings);
        }

        bot.getSettingsManager().getSettings(guild).setControllerLayoutJson(parsed);
        return new ApplyResult(true, "controller.editor.applied", warnings);
    }

    /** The layout in use, as JSON text — the default when the guild has not customised one. */
    private static String currentJson(Bot bot, Guild guild)
    {
        JsonNode custom = bot.getSettingsManager().getSettings(guild).getControllerLayoutJson();
        try
        {
            return MAPPER.writerWithDefaultPrettyPrinter()
                         .writeValueAsString(custom != null ? custom : defaultAsJson());
        }
        catch (Exception ex)
        {
            return "[]";
        }
    }

    /**
     * The built-in layout, written out as an editable starting point.
     *
     * <p>Bare entries, because that is the form worth teaching: it says which buttons appear
     * and in what order without burying that under appearance fields nobody needs to change.
     */
    private static JsonNode defaultAsJson()
    {
        var array = MAPPER.createArrayNode();
        for (List<ControllerLayout.ButtonSpec> row : ControllerLayout.defaultLayout().getRows())
        {
            var rowNode = MAPPER.createObjectNode();
            for (ControllerLayout.ButtonSpec spec : row)
            {
                rowNode.set(spec.action(), MAPPER.createObjectNode());
            }
            array.add(rowNode);
        }
        return array;
    }

    private static String truncate(String text)
    {
        return text.length() <= MAX_LAYOUT_LENGTH ? text : text.substring(0, MAX_LAYOUT_LENGTH);
    }
}
