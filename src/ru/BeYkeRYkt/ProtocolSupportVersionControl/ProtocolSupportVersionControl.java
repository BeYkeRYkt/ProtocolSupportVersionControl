package ru.BeYkeRYkt.ProtocolSupportVersionControl;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerOptions;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;

import protocolsupport.api.ProtocolSupportAPI;
import protocolsupport.api.ProtocolVersion;

public class ProtocolSupportVersionControl extends JavaPlugin {

	private List<Integer> versions;
	private List<Integer> entities;

	private static ProtocolSupportVersionControl plugin;
	private String kickMessage;
	private ProtocolManager manager;

	private ProtocolVersion minProtocolVersion;
	private ProtocolVersion maxProtocolVersion;
	private String versionMsg;

	@SuppressWarnings("static-access")
	@Override
	public void onEnable() {
		this.plugin = this;
		this.versions = new ArrayList<Integer>();
		this.entities = new ArrayList<Integer>();
		FileConfiguration fc = getConfig();
		try {
			if (!new File(getDataFolder(), "config.yml").exists()) {
				fc.options().header("ProtocolSupportVersionControl (PSVC) v" + getDescription().getVersion() + " Configuration" + "\nHave fun :3" + "\nby BeYkeRYkt" + "\nSupported protocol versions: " + "\n- 51 (1.4.7)" + "\n- 60 (1.5.1)" + "\n- 61 (1.5.2)" + "\n- 74 (1.6.2)" + "\n- 78 (1.6.4)" + "\n- 4 (1.7.5)" + "\n- 5 (1.7.10)" + "\n- 47 (1.8)" + "\nReplacers formula:" + "\n- ProtocolVersion : oldID : newID");
				// protocol versions
				List<Integer> versions = new ArrayList<Integer>();
				// versions.add(-2); // PE
				versions.add(51); // 1.4.7
				versions.add(60); // 1.5.1
				versions.add(61); // 1.5.2
				versions.add(74); // 1.6.2
				versions.add(78); // 1.6.4
				versions.add(4); // 1.7.5
				versions.add(5); // 1.7.10
				versions.add(47); // 1.8
				fc.set("SupportProtocolVersions", versions);

				// block replacer
				List<String> block = new ArrayList<String>();
				block.add("51:95:20"); // ProtocolVersion:oldID:newID
				fc.set("ReplaceBlockIDs", block);

				// item replacer
				List<String> item = new ArrayList<String>();
				item.add("51:95:20"); // ProtocolVersion:oldID:newID
				fc.set("ReplaceItemIDs", item);

				// living entity spawn block
				List<Integer> livingIds = new ArrayList<Integer>();
				livingIds.add(0);
				fc.set("EntitySpawnBlock", livingIds);

				fc.set("Messages.kick", "Your version of game is not supported on this server. \nActual versions: &a%VERSIONS%");
				saveConfig();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		this.manager = ProtocolLibrary.getProtocolManager();

		// register ProtocolLib
		manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Login.Client.START) {
			@Override
			public void onPacketReceiving(PacketEvent event) {
				ProtocolVersion version = ProtocolSupportAPI.getProtocolVersion(event.getPlayer());
				if (!getSupportedProtocolVersions().contains(version.getId())) {
					PacketContainer packet = new PacketContainer(PacketType.Login.Server.DISCONNECT);
					packet.getModifier().writeDefaults();
					packet.getChatComponents().write(0, WrappedChatComponent.fromText(kickMessage));
					event.setCancelled(true);
					sendPacket(event.getPlayer(), packet);
				}
			}
		});

		manager.addPacketListener(new PacketAdapter(this, ListenerPriority.NORMAL, Arrays.asList(PacketType.Status.Server.OUT_SERVER_INFO), ListenerOptions.ASYNC) {
			@Override
			public void onPacketSending(PacketEvent event) {
				ProtocolVersion version = ProtocolSupportAPI.getProtocolVersion(event.getPlayer());
				if (!getSupportedProtocolVersions().contains(version.getId())) {
					if (version == ProtocolVersion.getLatest()) {
						event.getPacket().getServerPings().read(0).setVersionProtocol(maxProtocolVersion.getId());
					} else {
						event.getPacket().getServerPings().read(0).setVersionProtocol(minProtocolVersion.getId());
					}
					event.getPacket().getServerPings().read(0).setVersionName(minProtocolVersion.getName() + " - " + maxProtocolVersion.getName());
				}
			}
		});

		// remapping...
		loadProtocolVersions(fc.getStringList("SupportProtocolVersions"));
		loadItemReplace(fc.getStringList("ReplaceItemIDs"));
		loadBlockReplace(fc.getStringList("ReplaceBlockIDs"));
		loadBlockedMobs(fc.getIntegerList("EntitySpawnBlock"));

		// messages
		setKickMessage(ChatColor.translateAlternateColorCodes('&', fc.getString("Messages.kick")));

		getServer().getPluginManager().registerEvents(new PSVCListener(this), this);
	}

	@SuppressWarnings("static-access")
	@Override
	public void onDisable() {
		this.plugin = null;
		this.versions.clear();
		this.entities.clear();
		this.kickMessage = null;
		this.versionMsg = null;
		this.manager = null;
		this.minProtocolVersion = null;
		this.maxProtocolVersion = null;
	}

	public void sendPacket(Player player, PacketContainer packet) {
		try {
			manager.sendServerPacket(player, packet);
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}
	}

	private void loadProtocolVersions(List<String> list) {
		ProtocolVersion min = ProtocolVersion.getLatest();
		ProtocolVersion max = ProtocolVersion.getOldest(); // a temporary fixes
		for (String string : list) {
			int protocolVersion = Integer.parseInt(string);
			ProtocolVersion version = ProtocolVersion.fromId(protocolVersion);
			if (version == ProtocolVersion.UNKNOWN) {
				getLogger().info("Cannot load protocol version: " + protocolVersion + ". Reason: Unknown protocol version");
				return;
			}

			// init minimum protocol version
			if (version.ordinal() > min.ordinal()) {
				min = version;
			}

			// init max protocol version
			if (version.ordinal() < max.ordinal()) {
				max = version;
			}

			if (versionMsg == null) {
				versionMsg = version.getName() + ", ";
			} else {
				versionMsg = versionMsg + version.getName() + ", ";
			}

			versions.add(version.getId());
		}

		versionMsg = versionMsg.substring(0, versionMsg.length() - 2);
		minProtocolVersion = min;
		maxProtocolVersion = max;
	}

	private void loadBlockReplace(List<String> list) {
		for (String string : list) {
			String[] parts = string.split(":");
			if (parts.length < 3) {
				getLogger().info("Cannot load block. Reason: length < 3");
				return;
			}
			int protocolVersion = Integer.parseInt(parts[0]);
			int oldId = Integer.parseInt(parts[1]);
			int newId = Integer.parseInt(parts[2]);

			if (protocolVersion == 0) {
				getLogger().info("Cannot load block: " + oldId + ". Reason: Unknown protocol version");
				return;
			}

			ProtocolVersion version = ProtocolVersion.fromId(protocolVersion);
			if (version == null) {
				getLogger().info("Cannot load block: " + oldId + ". Reason: Unknown protocol version");
				return;
			}
			ProtocolSupportAPI.getBlockRemapper(version).setRemap(oldId, newId);
		}
	}

	private void loadItemReplace(List<String> list) {
		for (String string : list) {
			String[] parts = string.split(":");
			if (parts.length < 3) {
				getLogger().info("Cannot load item. Reason: length < 3");
				return;
			}
			int protocolVersion = Integer.parseInt(parts[0]);
			int oldId = Integer.parseInt(parts[1]);
			int newId = Integer.parseInt(parts[2]);

			if (protocolVersion == 0) {
				getLogger().info("Cannot load item " + oldId + " to " + newId + ". Reason: Unknown protocol version");
				return;
			}

			ProtocolVersion version = ProtocolVersion.fromId(protocolVersion);
			if (version == null) {
				getLogger().info("Cannot load item " + oldId + " to " + newId + ". Reason: Unknown protocol version");
				return;
			}
			ProtocolSupportAPI.getItemRemapper(version).setRemap(oldId, newId);
		}
	}

	private void loadBlockedMobs(List<Integer> list) {
		for (Integer id : list) {
			if (!entities.contains(id)) {
				entities.add(id);
			}
		}
	}

	public static ProtocolSupportVersionControl getInstance() {
		return plugin;
	}

	public List<Integer> getSupportedProtocolVersions() {
		return versions;
	}

	public String getKickMessage() {
		return kickMessage;
	}

	public void setKickMessage(String message) {
		message = message.replace("%MIN_VERSION%", minProtocolVersion.getName());
		message = message.replace("%MAX_VERSION%", maxProtocolVersion.getName());
		message = message.replace("%VERSIONS%", versionMsg);
		this.kickMessage = message;
	}

	public List<Integer> getBlockedMobs() {
		return entities;
	}

	public void setMobs(List<Integer> mobs) {
		this.entities = mobs;
	}
}
