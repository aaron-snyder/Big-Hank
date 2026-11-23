# Big Hank 🎣

A fishing game Discord bot. Cast your line, catch fish, sell them for CarpCoins, and save up for
better rods and bait. Compete on per-server and global leaderboards.

> The original freshman-year version is preserved on the [`Human`](../../tree/Human) branch.

## How to play

When Big Hank joins a server he creates his own channel, **#big-hanks-lake**, and plays only there.
Everywhere else he answers privately, so people who aren't playing never get spammed (and anyone who isn't
playing can simply mute the channel). Players join each server's lake with `/start`. Your gear and coins
follow you to every server.

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
| `/setup` | Rebuild #big-hanks-lake if it was deleted. Only shown to members with **Manage Server** |

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
     and select exactly one permission: **Manage Channels**. That's what lets Big Hank create #big-hanks-lake.
     He replies through slash commands, which don't need any other permissions.
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

When it's working you'll see `Big Hank is on the lake as ...` and an invite link in the console
(it requests only Manage Channels).
Player progress is saved to `data/inventories.json` after every action. Back that file up occasionally.

### 4. Add it to a server

1. Open the invite link (from the console or the Installation tab), pick a server where you have
   **Manage Server**, and keep **Manage Channels** checked.
2. Big Hank creates **#big-hanks-lake** right away. Its topic tells people how to play. If a channel with
   that name already exists, he uses it instead of making a second one.
3. Players run `/start` in #big-hanks-lake to join.

Optional: drag the channel into whatever category you like, or adjust who can see it. Big Hank doesn't
mind. If someone deletes the channel, Big Hank won't recreate it on his own; a server manager can run
`/setup` to bring it back. If the bot was invited without Manage Channels, grant it to his role and
run `/setup`.

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
  - **No optional gateway intents.** Only the basic, non-privileged `GUILDS` intent, which JDA always uses.
    It tells Big Hank when he joins or leaves a server and which channels exist. The bot can't read messages,
    see member lists, or watch presences.
  - **One permission: Manage Channels.** It's needed to create #big-hanks-lake. Big Hank doesn't need Send
    Messages, because slash-command replies work without it, and the channel topic explains how to join. Server
    owners who want even less can remove Manage Channels from his role once the channel exists. Everything
    keeps working, and `/setup` will just ask for it back if the channel ever needs recreating.
  - **Respects deletions.** If a server deletes #big-hanks-lake, Big Hank never recreates it on his own.
  - **Forgets servers that remove him.** That server's settings are deleted when he's kicked.
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
├── commands/LakeKeeper.java    Creates #big-hanks-lake when Big Hank joins a server
├── data/GameStore.java     Saves/loads players and server settings (JSON)
├── data/GuildSettings.java Per-server channel and member list
└── game/                   The game rules: Inventory, Fish, Rod, Bait, Species, Leaderboard
```

Run the tests with `./mvnw test`.
