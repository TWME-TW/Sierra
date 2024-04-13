package de.feelix.sierra.manager.storage;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.User;
import de.feelix.sierra.Sierra;
import lombok.Getter;
import de.feelix.sierraapi.user.UserRepository;
import de.feelix.sierraapi.user.impl.SierraUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class DataManager implements UserRepository {

    /**
     * DataManager class represents a singleton instance that manages player data in the application.
     * It provides methods to manipulate and retrieve player data from the underlying data structures.
     */
    @Getter
    private static DataManager           instance;
    /**
     * This variable represents a map of User objects to PlayerData objects.
     */
    private final  Map<User, PlayerData> playerData = new ConcurrentHashMap<>();

    /**
     * ArrayList to store the history documents.
     */
    private final ArrayList<HistoryDocument> histories = new ArrayList<>();

    /**
     * The DataManager function initializes the packet listeners.
     */
    public DataManager() {
        instance = this;

        this.initializePacketListeners();
    }

    /**
     * The initializePacketListeners function is used to register a PacketListenerCommon object with the
     * PacketEvents API. This listener will be called whenever a player connects or disconnects from the server,
     * and we can use this to add/remove PlayerData objects for each player.
     */
    private void initializePacketListeners() {
        PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerCommon() {
            @Override
            public void onUserConnect(UserConnectEvent event) {
                addPlayerData(event.getUser());
                checkForUpdate(event.getUser());
            }

            @Override
            public void onUserDisconnect(UserDisconnectEvent event) {
                removePlayerData(event.getUser());
            }
        });
    }

    /**
     * Checks for updates of the Sierra plugin.
     * <p>
     * This method checks if the current version of the Sierra plugin is outdated by comparing it
     * with the latest release version from the UpdateChecker. If the versions are the same,
     * indicating that the plugin is up to date, the method returns without taking any further action.
     * If the user is null or not an online player, the method also returns without doing anything.
     * <p>
     * If the user has the "sierra.update" permission or is an operator, the method sends a message
     * to the player indicating that the current version of Sierra is outdated.
     *
     * @param user The user to check for update. Must be an online player.
     */
    private void checkForUpdate(User user) {

        Bukkit.getScheduler().runTaskLaterAsynchronously(Sierra.getPlugin(), () -> {
            // Is same version -> latest
            String localVersion         = Sierra.getPlugin().getDescription().getVersion();
            String latestReleaseVersion = Sierra.getPlugin().getUpdateChecker().getLatestReleaseVersion();

            if (latestReleaseVersion.equalsIgnoreCase(localVersion)) return;

            if (user == null) return;

            if (user.getName() == null) return;

            Player player = Bukkit.getPlayer(user.getName());

            if (player == null) return;

            if (!player.hasPermission("sierra.update") || !player.isOp()) return;

            player.sendMessage(Sierra.PREFIX + " §cThis version of Sierra is outdated!");
            player.sendMessage(Sierra.PREFIX + " §fLocal: §c" + localVersion + "§f, Latest: §a" + latestReleaseVersion);
        }, 5);
    }

    /**
     * The getPlayerData function is used to get the PlayerData object associated with a given User.
     *
     * @param user user Get the player data for a specific user
     * @return A weak reference to the player data object
     */
    public WeakReference<PlayerData> getPlayerData(User user) {
        return new WeakReference<>(this.playerData.get(user));
    }

    /**
     * The addPlayerData function adds a new PlayerData object to the playerData HashMap.
     *
     * @param user user Get the player's data
     */
    public void addPlayerData(User user) {
        PlayerData value = new PlayerData(user);
        value.setGameMode(GameMode.defaultGameMode());
        this.playerData.put(user, value);
    }

    /**
     * The removePlayerData function removes the player data from the HashMap.
     *
     * @param user user Remove the player data of a specific user
     */
    public void removePlayerData(User user) {
        this.playerData.remove(user);
    }

    @Override
    public Optional<SierraUser> queryUserByUuid(UUID uuid) {
        for (User user : playerData.keySet()) {
            if (user.getUUID() == uuid) {
                return Optional.of(playerData.get(user));
            }
        }
        return Optional.empty();
    }

    /**
     * Queries for a SierraUser by the given entityId.
     *
     * @param id The entityId to query for
     * @return An Optional containing the SierraUser object if found, otherwise an empty Optional
     */
    @Override
    public Optional<SierraUser> queryUserByEntityId(int id) {
        for (User user : playerData.keySet()) {
            if (user.getEntityId() == id) {
                return Optional.of(playerData.get(user));
            }
        }
        return Optional.empty();
    }

    /**
     * The queryUserByName method is used to query for a SierraUser object
     * by the given name. It searches for a matching user in the playerData HashMap
     * and returns the corresponding SierraUser object if found.
     *
     * @param name The name to search for in the playerData HashMap
     * @return Optional containing the SierraUser object if found, otherwise an empty Optional
     */
    @Override
    public Optional<SierraUser> queryUserByName(String name) {
        for (User user : playerData.keySet()) {
            if (user.getName().equalsIgnoreCase(name)) {
                return Optional.of(playerData.get(user));
            }
        }
        return Optional.empty();
    }
}
