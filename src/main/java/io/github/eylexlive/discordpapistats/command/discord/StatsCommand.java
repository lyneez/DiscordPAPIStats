package io.github.eylexlive.discordpapistats.command.discord;

import io.github.eylexlive.discordpapistats.DiscordPAPIStats;
import io.github.eylexlive.discordpapistats.stats.StatsManager;
import io.github.eylexlive.discordpapistats.util.config.ConfigUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class StatsCommand extends ListenerAdapter {

    private final DiscordPAPIStats plugin;

    public StatsCommand(DiscordPAPIStats plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        final User user = event.getAuthor();
        if (user.isBot())
            return;

        final MessageChannel channel = event.getChannel();

        final List<String> commands = ConfigUtil.getStringList(
                "stats-command-aliases"
        );

        commands.add(
                ConfigUtil.getString(
                        "stats-command"
                )
        );

        final String[] parts = event.getMessage().getContentRaw().split(" ");

        if (commands.stream().anyMatch(parts[0]::equalsIgnoreCase)) {
            if (parts.length == 1) {
                if (plugin.getDiscordSRV() == null) {
                    channel.sendMessage(
                            ConfigUtil.getString(
                                    "discord-messages.correct-usage",
                                    "nl:" + "\n",
                                    "command:" + parts[0]
                            )
                    ).queue();

                } else {
                    final String name = matchDiscordSRVPlayer(user.getId());
                    if (name != null) {
                        channel.sendMessage(
                                getEmbed(
                                        user, name
                                ).build()
                        ).queue();

                    } else {
                        channel.sendMessage(
                                ConfigUtil.getString(
                                        "discord-messages.account-unlinked",
                                        "nl:" + "\n",
                                        "command:" + parts[0]
                                )
                        ).queue();
                    }
                }
            }

            else if (parts.length == 2) {
                final List<Member> mentionedMembers = event.getMessage().getMentionedMembers();
                if (plugin.getDiscordSRV() == null  || mentionedMembers.size() == 0) {
                    channel.sendMessage(
                            getEmbed(
                                    user, parts[1]
                            ).build()
                    ).queue();

                } else {
                    final Member target = mentionedMembers.get(0);

                    final String name = matchDiscordSRVPlayer(target.getId());
                    if (name != null) {
                        channel.sendMessage(
                                getEmbed(
                                        user, name
                                ).build()
                        ).queue();

                    } else {
                        channel.sendMessage(
                                ConfigUtil.getString(
                                        "discord-messages.account-unlinked-target",
                                        "nl:" + "\n",
                                        "target:" + target.getUser().getName()
                                )
                        ).queue();
                    }
                }
            }
        }
    }

    private String matchDiscordSRVPlayer(String discordID) {
        final UUID uuid = plugin.getDiscordSRV()
                .getAccountLinkManager()
                .getUuid(
                        discordID
                );

        return (uuid != null ?
                plugin.getServer().getOfflinePlayer(uuid).getName()
                :
                null
        );
    }

    private EmbedBuilder getEmbed(User user, String name) {
        final EmbedBuilder embed = new EmbedBuilder();

        embed.setDescription(
                ConfigUtil.getString(
                        "stats-embed.description",
                        "player:" + name
                )
        );

        final Player player = plugin.getServer().getPlayerExact(name);
        final boolean online = player != null;

        final List<String> list = Arrays.asList(
                ConfigUtil.getString(
                        "online-status.online"
                ),
                ConfigUtil.getString(
                        "online-status.online-image"
                ),
                ConfigUtil.getString(
                        "online-status.offline"
                ),
                ConfigUtil.getString(
                        "online-status.offline-image"
                )
        );

        embed.setAuthor(
                ConfigUtil.getString(
                        "stats-embed.author",
                        "online_status:" + (online ? list.get(0) : list.get(2))
                ),
                null,
                online ? list.get(1) : list.get(3)
        );

        Color color;
        try {
            final Field field = Color.class.getField(
                    "stats-embed.color"
            );
            color = (Color) field.get(null);
        } catch (Exception e) {
            color = Color.gray;
        }

        embed.setColor(color);

        try {
            embed.setThumbnail(
                    ConfigUtil.getString(
                            "avatar-api",
                            "player:" + name
                    )
            );
        } catch (IllegalArgumentException e) {
            embed.setThumbnail(
                    ConfigUtil.getString(
                            "avatar-api",
                            "player:Steve"
                    )
            );
        }

        embed.setFooter(
                user.getName() + "#" + user.getDiscriminator(),
                user.getAvatarUrl()
        );

        embed.setTimestamp(Instant.now());

        final StatsManager statsManager = plugin.getStatsManager();
        if (!ConfigUtil.getBoolean("stats-embed.custom-fields.enabled")) {
            statsManager.getStatsList().forEach(stats ->  {
                final String statsValue = (
                        online ? statsManager.getStats(stats, player) : statsManager.getStats(stats, name)
                );

                final String fieldFormat = ConfigUtil.getString(
                        "stats-embed.field-format",
                        "stats_name:" + stats.getName(),
                        "stats_value:" + statsValue,
                        "player_name:" + name
                );

                final String[] fieldParts = fieldFormat.split("%VALUE");

                embed.addField(
                        fieldParts[0],
                        fieldParts[1],
                        true
                );
            });

        } else {
            final List<String> customFieldList = ConfigUtil.getStringList(
                    "stats-embed.custom-fields.fields"
            );

            customFieldList.replaceAll(s ->
                    s.replace(
                            "{player_name}", name
                    )
            );

            statsManager.getStatsList().forEach(stats ->  {
                final String statsValue = (
                        online ? statsManager.getStats(stats, player) : statsManager.getStats(stats, name)
                );

                customFieldList.replaceAll(s ->
                        s.replace(
                                "{stats_" + stats.getName() + "}", stats.getName()
                        )
                );
                customFieldList.replaceAll(s ->
                        s.replace(
                                "{stats_" + stats.getName() + "_value}", statsValue
                        )
                );
            });

            customFieldList.forEach(field -> {
                final String[] fieldParts = field.split("%VALUE");
                embed.addField(
                        fieldParts[0],
                        fieldParts[1],
                        true
                );
            });
        }

        return embed;
    }
}
