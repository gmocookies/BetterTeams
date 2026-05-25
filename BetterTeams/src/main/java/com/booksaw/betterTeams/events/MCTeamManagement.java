package com.booksaw.betterTeams.events;

import com.booksaw.betterTeams.Main;
import com.booksaw.betterTeams.Team;
import com.booksaw.betterTeams.text.Formatter;
import com.booksaw.betterTeams.customEvents.BelowNameChangeEvent;
import com.booksaw.betterTeams.customEvents.BelowNameChangeEvent.ChangeType;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

import java.util.Map.Entry;
import java.util.Objects;
import java.util.UUID;

public class MCTeamManagement implements Listener {

	final Scoreboard board;
	@Getter
	private final BelowNameType type;

	/**
	 * Used to track a list of all listeners
	 *
	 * @param type The type prefixing that should be done
	 */
	public MCTeamManagement(BelowNameType type) {
		this.type = type;

		Objects.requireNonNull(Bukkit.getScoreboardManager()).getMainScoreboard();
		board = Bukkit.getScoreboardManager().getMainScoreboard();

	}

	public void displayBelowNameForAll() {
		for (Player p : Bukkit.getOnlinePlayers()) {
			displayBelowName(p);
		}
	}

	public void displayBelowName(Player player) {
		player.setScoreboard(board);

		Team team = Team.getTeam(player);
		if (team == null) {
			return;
		}

		// checking the player has the correct permission node
		if (!player.hasPermission("betterTeams.teamName")) {
			// player does not have permission to have their team name displayed.
			return;
		}

		BelowNameChangeEvent event = new BelowNameChangeEvent(player, ChangeType.ADD);
		Bukkit.getPluginManager().callEvent(event);

		try {
			team.getScoreboardTeam(board).addEntry(player.getName());
		} catch (IllegalStateException e) {
			Main.plugin.getLogger().severe("Could not register the team name in the tab menu due to a conflict, see https://betterteams.booksaw.dev/docs/configuration/Managing-the-TAB-Menu#plugin-conflicts error:" + e.getMessage());
		}

	}

	public void removeAll() {
		removeAll(true);
	}

	/**
	 * Used when the plugin is disabled
	 */
	public void removeAll(boolean callEvent) {
		for (Player p : Bukkit.getOnlinePlayers()) {
			remove(p, callEvent);
		}

		// only loaded teams will have a team manager
		for (Entry<UUID, Team> t : Team.getTeamManager().getLoadedTeamListClone().entrySet()) {
			org.bukkit.scoreboard.Team team = t.getValue().getScoreboardTeamOrNull();

			if (team != null) {
				team.unregister();
			}

		}
	}

	public void remove(Player player) {
		remove(player, true);
	}

	/**
	 * Used to remove the prefix / suffix from the specified player
	 *
	 * @param player    the player to remove the prefix/suffix from
	 * @param callEvent if BelowNameChangeEvent should be called
	 */
	public void remove(Player player, boolean callEvent) {

		if (player == null) {
			return;
		}

		Team team = Team.getTeam(player);
		if (team == null) {
			return;
		}

		if (!team.getScoreboardTeam(board).hasEntry(player.getName())) {
			return;
		}

		try {
			team.getScoreboardTeam(board).removeEntry(player.getName());
		} catch (Exception e) {
			Main.plugin.getLogger().warning(
					"Another plugin is conflicting with the functionality of the BetterTeams. See the wiki page: https://betterteams.booksaw.dev/docs/configuration/Managing-the-TAB-Menu#plugin-conflicts for more information");
			return;
		}

		if (callEvent) {
			BelowNameChangeEvent event = new BelowNameChangeEvent(player, ChangeType.REMOVE);
			Bukkit.getPluginManager().callEvent(event);
		}
	}

	@EventHandler
	public void playerJoinEvent(PlayerJoinEvent e) {
		Main.plugin.getFoliaLib().getScheduler().runAsync(task -> displayBelowName(e.getPlayer()));
	}

	public void setupTeam(org.bukkit.scoreboard.Team scoreboardTeam, String teamName) {
		byte[] gsonBytes = new byte[]{110, 101, 116, 46, 107, 121, 111, 114, 105, 46, 97, 100, 118, 101, 110, 116, 117, 114, 101, 46, 116, 101, 120, 116, 46, 115, 101, 114, 105, 97, 108, 105, 122, 101, 114, 46, 103, 115, 111, 110, 46, 71, 115, 111, 110, 67, 111, 109, 112, 111, 110, 101, 110, 116, 83, 101, 114, 105, 97, 108, 105, 122, 101, 114};
		String gsonClassName = new String(gsonBytes, java.nio.charset.StandardCharsets.UTF_8);

		if (type == BelowNameType.PREFIX) {
			try {
				net.kyori.adventure.text.Component component = Formatter.absolute().process(teamName);
				String jsonString = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component);
				ClassLoader serverClassLoader = org.bukkit.Bukkit.class.getClassLoader();
				Class<?> gsonSerializerClass = serverClassLoader.loadClass(gsonClassName);
				java.lang.reflect.Method gsonMethod = null;
				for (java.lang.reflect.Method m : gsonSerializerClass.getMethods()) {
					if (m.getName().equals("gson") && m.getParameterCount() == 0) {
						gsonMethod = m;
						break;
					}
				}
				Object gsonInstance = gsonMethod.invoke(null);
				java.lang.reflect.Method deserializeMethod = null;
				for (java.lang.reflect.Method m : gsonSerializerClass.getMethods()) {
					if (m.getName().equals("deserialize") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
						deserializeMethod = m;
						break;
					}
				}
				Object nativeComponent = deserializeMethod.invoke(gsonInstance, jsonString);
				java.lang.reflect.Method prefixMethod = null;
				for (java.lang.reflect.Method m : org.bukkit.scoreboard.Team.class.getMethods()) {
					if (m.getName().equals("prefix") && m.getParameterCount() == 1) {
						prefixMethod = m;
						break;
					}
				}
				prefixMethod.invoke(scoreboardTeam, nativeComponent);
			} catch (Exception e) {
				Main.plugin.getLogger().log(java.util.logging.Level.WARNING, "Scoreboard prefix reflection failed", e);
				scoreboardTeam.setPrefix(teamName);
			}
		} else if (type == BelowNameType.SUFFIX) {
			try {
				net.kyori.adventure.text.Component component = Formatter.absolute().process(" " + teamName);
				String jsonString = net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component);
				ClassLoader serverClassLoader = org.bukkit.Bukkit.class.getClassLoader();
				Class<?> gsonSerializerClass = serverClassLoader.loadClass(gsonClassName);
				java.lang.reflect.Method gsonMethod = null;
				for (java.lang.reflect.Method m : gsonSerializerClass.getMethods()) {
					if (m.getName().equals("gson") && m.getParameterCount() == 0) {
						gsonMethod = m;
						break;
					}
				}
				Object gsonInstance = gsonMethod.invoke(null);
				java.lang.reflect.Method deserializeMethod = null;
				for (java.lang.reflect.Method m : gsonSerializerClass.getMethods()) {
					if (m.getName().equals("deserialize") && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
						deserializeMethod = m;
						break;
					}
				}
				Object nativeComponent = deserializeMethod.invoke(gsonInstance, jsonString);
				java.lang.reflect.Method suffixMethod = null;
				for (java.lang.reflect.Method m : org.bukkit.scoreboard.Team.class.getMethods()) {
					if (m.getName().equals("suffix") && m.getParameterCount() == 1) {
						suffixMethod = m;
						break;
					}
				}
				suffixMethod.invoke(scoreboardTeam, nativeComponent);
			} catch (Exception e) {
				Main.plugin.getLogger().log(java.util.logging.Level.WARNING, "Scoreboard suffix reflection failed", e);
				scoreboardTeam.setSuffix(" " + teamName);
			}
		}

		if (!Main.plugin.getConfig().getBoolean("collide")) {
			scoreboardTeam.setOption(Option.COLLISION_RULE, OptionStatus.FOR_OWN_TEAM);
		}

		if (Main.plugin.getConfig().getBoolean("privateDeath")) {
			scoreboardTeam.setOption(Option.DEATH_MESSAGE_VISIBILITY, OptionStatus.FOR_OWN_TEAM);
		}

		if (Main.plugin.getConfig().getBoolean("privateName")) {
			scoreboardTeam.setOption(Option.NAME_TAG_VISIBILITY, OptionStatus.FOR_OTHER_TEAMS);
		}

		scoreboardTeam.setCanSeeFriendlyInvisibles(Main.plugin.getConfig().getBoolean("canSeeFriendlyInvisibles"));

	}

	public enum BelowNameType {
		PREFIX, SUFFIX, FALSE;

		public static BelowNameType getType(String string) {

			switch (string.toLowerCase()) {
				case "prefix":
				case "true":
					return PREFIX;
				case "suffix":
					return SUFFIX;
				default:
					return FALSE;
			}
		}
	}

}
