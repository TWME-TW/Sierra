package de.feelix.sierra.check;

import com.github.retrooper.packetevents.event.ProtocolPacketEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import de.feelix.sierra.Sierra;
import de.feelix.sierra.check.violation.Violation;
import de.feelix.sierra.manager.storage.PlayerData;
import de.feelix.sierra.manager.storage.SierraDataManager;
import de.feelix.sierra.utilities.message.ConfigValue;
import de.feelix.sierraapi.check.SierraCheckData;
import de.feelix.sierraapi.check.impl.SierraCheck;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import de.feelix.sierraapi.check.CheckType;
import de.feelix.sierraapi.violation.PunishType;

import java.util.Collection;
import java.util.logging.Logger;

/**
 * The SierraDetection class is used to detect violations in player data.
 */
@Getter
public class SierraDetection implements SierraCheck {

    private final PlayerData playerData;
    private final CheckType  rawCheckType;

    private String friendlyName;
    private int    checkId;
    private int    violations = 0;

    /**
     * The SierraDetection class is used to detect violations in player data.
     *
     * @param playerData The PlayerData object containing the player's data
     */
    public SierraDetection(PlayerData playerData) {
        // Initialize member variables
        this.playerData = playerData;
        this.rawCheckType = initializeCheckType();
        if (this.rawCheckType != null) {
            this.friendlyName = rawCheckType.getFriendlyName();
            this.checkId = rawCheckType.getId();
        }
    }

    /**
     * Initializes the CheckType from an annotation.
     *
     * @return The initialized CheckType or null if the class does not have the SierraCheckData annotation.
     */
    // Private method to initialize CheckType from annotation
    private CheckType initializeCheckType() {
        // Check if the class has the SierraCheckData annotation
        if (this.getClass().isAnnotationPresent(SierraCheckData.class)) {
            // Retrieve annotation data and return the CheckType
            SierraCheckData checkData = this.getClass().getAnnotation(SierraCheckData.class);
            return checkData.checkType();
        }
        return null;
    }

    /**
     * Handle violation event.
     *
     * @param event             The ProtocolPacketEvent that triggered the violation
     * @param violation The ViolationDocument containing information about the violation
     */
    // Handle violation event
    public void violation(ProtocolPacketEvent<Object> event, Violation violation) {

        // Cancel the packet event
        event.setCancelled(true);
        event.cleanUp();

        if (playerData.isReceivedPunishment()) return;

        // Update violation count
        this.violations++;

        // Asynchronously call user detection event
        throwDetectionEvent(violation);

        // Log to console, alert staff, create history, and potentially punish
        User user = event.getUser();
        consoleLog(user, violation);
        alert(user, violation);

        if (violation.getPunishType() != PunishType.MITIGATE) {

            Sierra.getPlugin().getSierraDataManager().addKick(this.checkType());

            Sierra            plugin            = Sierra.getPlugin();
            SierraDataManager sierraDataManager = plugin.getSierraDataManager();

            // Todo: patch it

            // sierraDataManager
            //     .createPunishmentHistory(
            //         playerData.username(), playerData.version(), violation.getPunishType(),
            //         playerData.getPingProcessor().getPing(),
            //         violation.debugInformation()
            //     );

            blockAddressIfEnabled(violation);
            playerData.punish(violation.getPunishType());
        }
    }

    /**
     * Block the player's address if the punishment type is set to BAN, the ban feature is enabled in the
     * punishment configuration, and the "block-connections-after-ban" property is set to true in the Sierra
     * configuration.
     *
     * @param violation The ViolationDocument object containing information about the violation.
     */
    private void blockAddressIfEnabled(Violation violation) {
        if (violation.getPunishType() == PunishType.BAN && Sierra.getPlugin().getPunishmentConfig().isBan()
            && Sierra.getPlugin().getSierraConfigEngine().config().getBoolean("block-connections-after-ban", true)) {
            Sierra.getPlugin()
                .getAddressStorage()
                .addIPAddress(this.playerData.getUser().getAddress().getAddress().getHostAddress());
        }
    }

    /**
     * Throws a detection event asynchronously.
     *
     * @param violation The ViolationDocument containing information about the violation
     */
    private void throwDetectionEvent(Violation violation) {

        // Todo: Fix new violation

        // FoliaScheduler.getAsyncScheduler().runNow(
        //     Sierra.getPlugin(),
        //     o -> Sierra.getPlugin()
        //         .getEventBus()
        //         .publish(new AsyncUserDetectionEvent(violation, playerData, checkType(), this.violations))
        // );
    }

    /**
     * Logs a message to the console.
     *
     * @param user              The User object representing the player.
     * @param violation The ViolationDocument containing information about the violation.
     */
    protected void consoleLog(User user, Violation violation) {

        if (!Sierra.getPlugin().getSierraConfigEngine().config().getBoolean("log-violation-to-console", true)) {
            return;
        }

        if (violation.getPunishType() == PunishType.MITIGATE) return;

        logToConsole(createGeneralMessage(user, violation.getPunishType()));
        logToConsole(createGeneralInformation(violation));
        logToConsole(createGeneralCheck());
    }

    /**
     * Creates a general message for a given user and punish type.
     * The message is formatted as "Player [username] got [friendlyMessage] sending an protocol packet".
     *
     * @param user       The User object representing the player.
     * @param punishType The PunishType enum representing the type of punishment.
     * @return A string representing the general message.
     */
    private String createGeneralMessage(User user, PunishType punishType) {
        return "Player " + user.getName() + " got " + punishType.friendlyMessage() + " sending an protocol packet";
    }

    /**
     * Creates general information for a violation document.
     *
     * @param violation The ViolationDocument object containing information about the violation.
     * @return A string representing the general information.
     */
    private String createGeneralInformation(Violation violation) {
        // Todo : Patch it
        // return String.format(
        //     "Debug information: %s", violation.getDebugInformation().isEmpty()
        //         ? "No debug available" : FormatUtils.shortenString(violation.getDebugInformation()));
        return "";
    }

    /**
     * Generates a general check message for a violation.
     *
     * @return A string representing the general check information.
     */
    private String createGeneralCheck() {
        return String.format("Check Information: %s/%d - VL: %d", this.friendlyName, this.checkId, this.violations);
    }

    /**
     * Logs a message to the console.
     *
     * @param message The message to be logged.
     */
    private void logToConsole(String message) {
        Logger logger = Sierra.getPlugin().getLogger();
        logger.info(message);
    }


    /**
     * Sends an alert message to staff members with information about the violation.
     *
     * @param user              The User object representing the player.
     * @param violation The ViolationDocument containing information about the violation.
     */
    protected void alert(User user, Violation violation) {

        PunishType punishType    = violation.getPunishType();
        String     staffAlert    = formatStaffAlertMessage(user, punishType);
        String     username      = this.playerData.getUser().getName();
        String     clientVersion = this.playerData.getUser().getClientVersion().getReleaseName();

        String content = new ConfigValue(
            "layout.detection-message.alert-content",
            " &7Username: &b{username}{n} &7Version: &b{clientVersion}{n} &7Brand: &b{brand}{n} &7Exist since: "
            + "&b{ticksExisted}{n} &7Game mode: &b{gameMode}{n} &7Tag: &b{tags}{n} &7Debug info: &b{debugInfo}{n}{n} "
            + "{alertNote}",
            true
        )
            .replace("{username}", username)
            .replace("{clientVersion}", clientVersion)
            .replace("{brand}", this.playerData.brand())
            .replace("{ticksExisted}", this.playerData.ticksExisted() + " ticks")
            .replace("{gameMode}", this.playerData.gameMode().name())
            .replace("{tags}", this.friendlyName.toLowerCase())
            // .replace("{debugInfo}", FormatUtils.shortenString(violation.getDebugInformation())) // Todo: Patch
            .replace("{alertNote}", getAlertNote())
            .stripped().colorize().replacePrefix().message();

        String command = getPunishmentCommand(username);

        Collection<PlayerData> playerDataList = Sierra.getPlugin().getSierraDataManager().getPlayerData().values();

        if (punishType == PunishType.MITIGATE) {
            for (PlayerData playerData : playerDataList) {
                if (playerData.getMitigationSettings().enabled()) {
                    playerData.getUser().sendMessage(
                        LegacyComponentSerializer.legacy('&')
                            .deserialize(staffAlert)
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .hoverEvent(HoverEvent.showText(Component.text(content))));
                }
            }
        } else {
            for (PlayerData playerData : playerDataList) {
                if (playerData.getAlertSettings().enabled()) {
                    playerData.getUser().sendMessage(
                        LegacyComponentSerializer.legacy('&')
                            .deserialize(staffAlert)
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, command))
                            .hoverEvent(HoverEvent.showText(Component.text(content))));
                }
            }
        }
    }

    /**
     * Retrieves the alert note from the Sierra configuration.
     *
     * @return The alert note from the configuration.
     */
    private String getAlertNote() {
        return new ConfigValue(
            "layout.detection-message.alert-command-note",
            "&fClick to teleport", true
        ).colorize().replacePrefix().message();
    }

    /**
     * Retrieves the punishment command for a given user from the Sierra configuration.
     *
     * @param username The username of the user.
     * @return The punishment command for the user.
     */
    private String getPunishmentCommand(String username) {
        return new ConfigValue(
            "layout.detection-message.alert-command",
            "/tp {username}", true
        ).replace("{username}", username).message();
    }

    /**
     * Formats the staff alert message with the given user, punish type, and SierraConfigEngine.
     *
     * @param user       The User object representing the player.
     * @param punishType The PunishType enum representing the type of punishment.
     * @return The formatted staff alert message.
     */
    private String formatStaffAlertMessage(User user, PunishType punishType) {

        return new ConfigValue(
            "layout.detection-message.staff-alert",
            "{prefix} &b{username} &8┃ &f{mitigation} &b{checkname} &8┃ &3x{violations}", true
        ).colorize().replacePrefix()
            .replace("{username}", user.getName())
            .replace("{mitigation}", punishType.friendlyMessage())
            .replace("{checkname}", this.friendlyName)
            .replace("{violations}", String.valueOf(violations)).message();
    }

    /**
     * Returns the number of violations detected.
     *
     * @return The number of violations detected.
     */
    @Override
    public double violations() {
        return this.violations;
    }

    /**
     * Sets the number of violations found by this check.
     *
     * @param violations The number of violations to set.
     */
    @Override
    public void setViolations(double violations) {
        this.violations = (int) violations;
    }

    /**
     * Retrieves the check type of the current instance.
     *
     * @return The check type of the instance.
     */
    @Override
    public CheckType checkType() {
        return this.rawCheckType;
    }
}
