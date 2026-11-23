package bighank.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bighank.game.Inventory;
import bighank.game.Leaderboard;
import bighank.game.Rod;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.Test;

class ViewsTest {
    private static final long OWNER = 123L;

    /** Every button in the message, keyed by action:args (owner id left out). */
    private static Map<String, Button> buttons(MessageCreateData message) {
        return message.getComponents().stream()
                .flatMap(c -> ((ActionRow) c).getButtons().stream())
                .collect(Collectors.toMap(b -> {
                    ButtonId id = ButtonId.parse(b.getCustomId());
                    return id.action() + (id.args().isEmpty() ? "" : ":" + String.join(":", id.args()));
                }, b -> b));
    }

    @Test
    void storeButtonsReflectWhatYouOwnAndCanAfford() {
        Inventory inv = new Inventory("hank"); // 100 coins, starter rod
        Map<String, Button> buttons = buttons(Views.store(inv, OWNER, null));

        assertFalse(buttons.get("buyrod:TROUT_TICKLER").isDisabled(), "100 coins buys the Trout Tickler");
        assertTrue(buttons.get("buyrod:CATFISH_CLOBBERER").isDisabled(), "can't afford 1,000");
        assertFalse(buttons.get("buybait:SPINNER_LURE").isDisabled());
        assertTrue(buttons.get("storesell").isDisabled(), "nothing to sell yet");
        assertNull(buttons.get("buyrod:GUPPY_GETTER"), "the starter rod isn't for sale");

        inv.buyRod(Rod.TROUT_TICKLER);
        buttons = buttons(Views.store(inv, OWNER, "✅ bought"));
        assertTrue(buttons.get("buyrod:TROUT_TICKLER").isDisabled(), "already owned");
        assertTrue(buttons.get("buybait:WORM").isDisabled(), "0 coins left");
    }

    @Test
    void castAgainCarriesTheTripTotals() {
        Inventory inv = new Inventory("hank");
        Inventory.Catch c = inv.cast(Rod.STARTER, 100_000, new Random(3));
        MessageCreateData message = Views.catchMessage(inv, OWNER, c, 4, 613, false, false);

        ButtonId cast = ButtonId.parse(buttons(message).get("cast:4:613").getCustomId());
        assertEquals(OWNER, cast.ownerId());
        assertEquals(4, cast.longArg(0));
        assertEquals(613, cast.longArg(1), "trip inches are carried in tenths");
        assertFalse(buttons(message).get("sellcatch").isDisabled());
        assertTrue(message.getContent().contains("this trip: 4 fish, 61.3 in"), message.getContent());
        assertFalse(message.getContent().contains("Fish God"));
    }

    @Test
    void catchMessageCrownsTheFishGod() {
        Inventory inv = new Inventory("hank");
        Inventory.Catch c = inv.cast(Rod.STARTER, 100_000, new Random(3));

        String holding = Views.catchMessage(inv, OWNER, c, 1, 20, true, false).getContent();
        assertTrue(holding.startsWith("🎣 👑 Fish God **hank**"), holding);
        assertFalse(holding.contains("new **Fish God**"));

        String crowned = Views.catchMessage(inv, OWNER, c, 1, 20, true, true).getContent();
        assertTrue(crowned.contains("is the new **Fish God**"), crowned);
    }

    @Test
    void leaderboardTabsHighlightTheCurrentView() {
        // buttons() would also throw on a duplicate id, which Discord rejects
        Map<String, Button> buttons = buttons(Views.leaderboard(List.of(), OWNER, true));
        assertEquals(2, buttons.size());
        assertTrue(buttons.get("lb:global").isDisabled(), "current scope");
        assertFalse(buttons.get("lb:server").isDisabled());
    }

    @Test
    void serverLeaderIsShownAsFishGodButGlobalLeaderIsNot() {
        Inventory inv = new Inventory("hank");
        inv.cast(Rod.STARTER, 100_000, new Random(3));
        List<Leaderboard.Entry> ranking = Leaderboard.rank(Map.of(OWNER, inv));

        String server = Views.leaderboard(ranking, OWNER, false).getEmbeds().get(0).getDescription();
        String global = Views.leaderboard(ranking, OWNER, true).getEmbeds().get(0).getDescription();
        assertTrue(server.startsWith("👑 **Fish God**"), server);
        assertTrue(global.startsWith("🥇"), global);
    }

    @Test
    void onlyYourOwnProfileHasALeaveButton() {
        Inventory inv = new Inventory("hank");
        assertEquals(1, buttons(Views.profile(inv, OWNER, OWNER, false)).size());
        assertTrue(Views.profile(inv, OWNER, 999L, false).getComponents().isEmpty());
        assertTrue(Views.profile(inv, OWNER, 999L, true).getEmbeds().get(0).getTitle().startsWith("👑"));
    }

    @Test
    void buttonIdsRoundTrip() {
        ButtonId id = ButtonId.parse(ButtonId.of(ButtonId.LEADERBOARD, 42L, "global", "EARNINGS"));
        assertEquals(ButtonId.LEADERBOARD, id.action());
        assertEquals(42L, id.ownerId());
        assertEquals(List.of("global", "EARNINGS"), id.args());
        assertNull(ButtonId.parse("someone-elses-button"));
        assertTrue(ButtonId.of(ButtonId.CAST, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE).length() <= 100,
                "Discord's custom id limit");
    }
}
