# Big Hank 🎣

A fishing game Discord bot. Cast your line, catch fish, sell them for CarpCoins, and save up for
better rods and bait. Compete on per-server and global leaderboards.

> The original freshman-year version is preserved on the [`Human`](../../tree/Human) branch.

## How to play

Big Hank only plays in **one channel per server**, chosen by a server manager. Everywhere else he answers
privately, so people who aren't playing never get spammed. Players join each server's lake with `/start`.
Your gear and coins follow you to every server.

| Command | What it does |
|---|---|
| `/start` | Get your fishin' license and join this server's lake |
| `/fish [rod]` | Cast a line (uses your best rod unless you pick one). 5 second cooldown |
| `/inventory` | Your CarpCoins, rods, bait, and bucket |
| `/getfish` | Every fish in your bucket |
| `/setbait <bait>` | Put bait on your hook (one is used up per cast) |
| `/store` | What Hank's selling |
| `/buy <item> [packs]` | Buy a rod or bait |
| `/sell` | Sell all your fish for 1 CarpCoin per inch |
| `/leaderboard [scope] [category]` | Top anglers in this server or globally, by CarpCoins earned, biggest catch, or fish caught |
| `/leave` | Leave this server's lake and its leaderboard (you keep your gear) |
| `/commands` | List all commands |
| `/setup [channel]` | Pick Big Hank's channel. Only shown to members with **Manage Server** |

Better rods catch everything the cheaper rods can, plus bigger species:

| Rod | Price | New fish |
|---|---|---|
| Guppy Getter | starter | Goldfish, Guppy, Pufferfish |
| Trout Tickler | 100 | Rainbow Trout, Smallmouth Bass |
| Catfish Clobberer | 1,000 | Largemouth Bass, Catfish |
| Salmon Slapper | 10,000 | Salmon, Halibut |
| Tuna Tamer | 1,000,000 | Tuna |

Bait comes in packs of 5 casts and adds inches to every fish: Worm (5), PowerBait (10),
Glitter PowerBait (15), Spinner Lure (20). Prices and fish sizes are defined in the enums in
`src/main/java/bighank/game/`, so they're easy to tune.

---

## Setting it up

You need **Java 17 or newer** (check with `java -version`). You do **not** need to install Maven: the
included `mvnw` / `mvnw.cmd` wrapper downloads it automatically.

### 1. Create the Discord application

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications) and click **New Application**. Name it "Big Hank".
2. Open the **Bot** tab:
   - Click **Reset Token**, then copy the token. **Treat it like a password.** Anyone with it controls your bot.
   - Leave all three **Privileged Gateway Intents** turned **off**. Big Hank doesn't need any of them.
   - Leave **Public Bot** on if you want other people to be able to add Big Hank to their servers.
     Turn it off if only you should be able to invite it.
3. Open the **Installation** tab:
   - Under **Installation Contexts**, check **Guild Install** only (uncheck User Install).
   - Under **Default Install Settings → Guild Install**, set scopes to `bot` and `applications.commands`,
     and leave **Permissions empty**. Big Hank replies through slash commands, which don't need any permissions.
   - Set **Install Link** to "Discord Provided Link". That link is what you share so people can add the bot.

### 2. Give the bot its token (privately)

Copy `.env.example` to `.env` and paste your token in:

```
DISCORD_TOKEN=your-token-here
```

`.env` is listed in `.gitignore`, so it will never be committed. (Alternatively, set a `DISCORD_TOKEN`
environment variable. That's what you'd do on a hosting service.)

### 3. Build and run

Windows (PowerShell):

```powershell
.\mvnw.cmd clean package
java -jar target\BigHank.jar
```

macOS / Linux:

```bash
./mvnw clean package
java -jar target/BigHank.jar
```

When it's working you'll see `Big Hank is on the lake as ...` and an invite link in the console.
Player progress is saved to `data/inventories.json` after every action. Back that file up occasionally.

### 4. Add it to a server

1. Open the invite link (from the console or the Installation tab) and pick a server where you have
   **Manage Server**.
2. Create a channel for the game, e.g. `#big-hank`. Optionally make it opt-in so only players see it
   (for example, put it behind a role, or use Discord's **Channels & Roles** onboarding).
3. In that channel, run `/setup`. Big Hank will now only play there.
4. Players run `/start` in that channel to join.

Slash commands can take a minute to appear the first time the bot starts. If they don't show up,
restart your Discord app (Ctrl+R).

### 5. Keep it online 24/7

The bot is only online while the program is running. For an always-on bot, run it on a machine that
stays on: a cheap VPS, a free cloud tier (e.g. Oracle Cloud Always Free), a Raspberry Pi, or a hosting
service that runs Docker containers. A `Dockerfile` is included:

```bash
docker build -t bighank .
docker run -d --name bighank --restart unless-stopped \
  --env-file .env -v bighank-data:/app/data bighank
```

The `-v bighank-data:/app/data` volume keeps player progress across restarts and redeploys.

Without Docker on a Linux server, a minimal `systemd` service looks like this
(`/etc/systemd/system/bighank.service`):

```ini
[Unit]
Description=Big Hank Discord bot
After=network-online.target

[Service]
User=bighank
WorkingDirectory=/opt/bighank
EnvironmentFile=/opt/bighank/.env
ExecStart=/usr/bin/java -jar /opt/bighank/BigHank.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

Then run `sudo systemctl enable --now bighank`.

### Growing past 100 servers

Discord requires bots in 100+ servers to be **verified**. Starting at 75 servers you'll be prompted
in the Developer Portal. Verification needs a Terms of Service and Privacy Policy URL. Big Hank stores
only Discord user IDs, display names, server/channel IDs, and game progress, which keeps the privacy
policy short. Because Big Hank uses no privileged intents, verification doesn't need any intent approvals.

---

## Security

- **The token is never in the code.** It comes from the `DISCORD_TOKEN` environment variable or a
  git-ignored `.env` file, so the repository is safe to share publicly.
- **If your token ever leaks,** go to Developer Portal → Bot → **Reset Token** immediately. The old token
  stops working the moment you reset it.
- **Least privilege:**
  - **Zero gateway intents.** The bot can't read messages, see member lists, or watch presences. Slash
    commands don't need any of that.
  - **Zero permissions.** The invite link asks for no permissions. Interaction replies work without them.
  - **`/setup` is restricted to Manage Server.** Discord hides it from everyone else, and the bot
    re-checks the permission when it runs.
  - **Guild-only commands.** Nothing works in DMs or group chats.
  - **No pings.** Mentions are disabled globally, so a player named `@everyone` can't ping anyone.
  - **No admin-only data commands are exposed to players.** The old `/save` and `/load`, which any player
    could run, are gone. Saving is automatic.
  - **Unprivileged container.** The Docker image runs as an unprivileged user.
- `data/` is git-ignored too, since it contains Discord user IDs.

## Project layout

```
src/main/java/bighank/
├── BigHank.java            Startup: reads config, connects to Discord, registers commands
├── Config.java             Reads DISCORD_TOKEN etc. from the environment or .env
├── commands/BotCommands.java   Every slash command
├── data/GameStore.java     Saves/loads players and server settings (JSON)
├── data/GuildSettings.java Per-server channel and member list
└── game/                   The game rules: Inventory, Fish, Rod, Bait, Species, Leaderboard
```

Run the tests with `./mvnw test`.
