# Big Hank 🎣

A fishing game Discord bot. Cast your line, catch fish, sell them for CarpCoins, and save up for
better rods and bait. Compete on per-server and global leaderboards.

> The original freshman-year version is preserved on the [`Human`](../../tree/Human) branch.

## How to play

Big Hank lives in a private channel, **#big-hanks-lake**, that only players can see. Anyone in the server
joins by typing **`/start`** in any channel. That gives them the **Lake Angler** role, and the lake appears.
People who never join never see the channel or any of the game's messages. Your gear and coins follow you
to every server.

| Command | What it does |
|---|---|
| `/start` | Join the lake (works in any channel). Run it again any time for the how-to-play guide |
| `/fish [bait]` | Cast a line with your best rod. Pick bait from the list; Hank remembers it until it runs out. The catch message has **Cast again** and **Sell** buttons, with no wait between casts |
| `/store` | Your wallet, gear, and bucket, with buttons to **buy rods and bait** and **sell your fish** |
| `/profile [angler]` | Stats, gear, and bucket for you or another player. Yours has a **Leave the lake** button |
| `/leaderboard` | The biggest fish ever caught, with buttons to switch between this server and global |
| `/setup` | Server managers only: rebuild the lake channel and role if something got deleted |

### 👑 Fish God

Whoever holds the **biggest catch in a server** is that server's **Fish God**. They get a gold **Fish God**
role in Discord, a crown next to their name in the lake, and the top spot on the server leaderboard. Catch
something bigger and the title (and role) moves to you on the spot. Sizes go to a tenth of an inch, and if
two fish are exactly the same size, whoever caught theirs first keeps the crown.

Only catches are posted publicly (in the lake). The store, profiles, leaderboards, and errors are private
messages that only you can see, and "Cast again" updates your catch message instead of posting a new one,
so the lake stays readable.

Better rods catch everything the cheaper rods can, plus bigger species:

| Rod | Price | New fish |
|---|---|---|
| Guppy Getter | starter | Goldfish, Guppy, Pufferfish |
| Trout Tickler | 100 | Rainbow Trout, Smallmouth Bass |
| Catfish Clobberer | 1,000 | Largemouth Bass, Catfish |
| Salmon Slapper | 5,000 | Salmon, Halibut |
| Tuna Tamer | 20,000 | Tuna |

Getting the Tuna Tamer takes about 1,000 casts with spinner lures (roughly half an hour of fishing), or
about 2,000 with no bait. The Tuna Tamer is the only way to catch Tuna, the biggest fish in the lake.

Bait comes in packs of 5 casts and adds inches to every fish: Worm (5), PowerBait (10),
Glitter PowerBait (15), Spinner Lure (20). A bigger fish sells for more *and* gives you a better shot
at Fish God. Prices and fish sizes are defined in the enums in `src/main/java/bighank/game/`, so they're
easy to tune.

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
     and select exactly these permissions: **View Channels**, **Manage Channels**, and **Manage Roles**.
     That's what lets Big Hank build his private lake (see [Security](#security) for why each is needed).
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
(it requests exactly the three permissions above).
Player progress is saved to `data/inventories.json` after every action. Back that file up occasionally.

### 4. Add it to a server

1. Open the invite link (from the console or the Installation tab), pick a server where you have
   **Manage Server**, and keep all three permissions checked.
2. Big Hank creates the **Lake Angler** role and a private **#big-hanks-lake** channel right away. If a
   channel with that name already exists, he takes it over and makes it private instead of making a second one.
3. Tell your server: "type `/start` to play Big Hank's fishing game."

Notes for server managers:
- **Big Hank's roles grant no permissions.** The **Fish God** role is gold, but it only shows its color if it's
  the highest colored role a member has. To make it stand out, drag it higher in Server Settings → Roles, or
  turn on "Display role members separately". Keep it *below* Big Hank's own role so he can still move it.
- **The Lake Angler role** only unlocks the lake. You can hand it out yourself, or
  offer it in Discord's **Onboarding / Channels & Roles** (Community servers). Anyone who gets it that way is
  signed up automatically the first time they play.
- You can move the channel into any category. If you do, check its permissions still hide it from @everyone.
- If someone deletes the channel, Big Hank won't recreate it on his own. Run `/setup` to bring it back.
- If Big Hank is missing a permission, `/start` says which one. Add it to his role (or re-open the invite
  link, which updates his permissions), then run `/setup`.
- The slash commands show up in everyone's `/` menu; Discord doesn't let bots hide them. Non-players who try
  one just get a private "use `/start` to join" reply. To hide them anyway, go to Server Settings →
  Integrations → Big Hank.

Slash commands can take a minute to appear the first time the bot starts. If they don't show up,
restart your Discord app (Ctrl+R).

### 5. Keep it online 24/7

The bot is only online while the program is running. For an always-on bot, run it on a machine or
service that stays on.

#### Railway (recommended)

Railway builds the included `Dockerfile` straight from GitHub and redeploys on every push to `main`.

1. **Push the code to GitHub:** `git push origin main`.
2. **Create the service.** In [Railway](https://railway.com), click **New Project → Deploy from GitHub repo**.
   Authorize the Railway GitHub app for this repo, then select it. Railway starts a first build right away;
   it's expected to fail until the token is added.
3. **Add the token.** Click the service → **Variables** → **New Variable**, with name `DISCORD_TOKEN` and your
   bot token as the value. Don't set `DATA_FILE`; the default is what the volume expects.
4. **Add a volume.** Right-click the service on the project canvas (or press `Ctrl/Cmd + K` and search
   "volume") → **Attach Volume**, with mount path **`/app/data`**. Without this, everyone's progress is wiped
   on every deploy.
5. **Check the settings.** In the service's **Settings**, keep **Serverless** off, keep **Replicas** at 1
   (two copies of the bot would fight over the save file), and leave the restart policy at **On Failure**.
6. **Deploy.** Click **Deploy** (or push a commit). In the deployment's logs, wait for
   `Big Hank is on the lake as ...`. The invite link is printed right below it.
7. **Upgrading from an older Big Hank?** If he's already in your server from before the private lake,
   he needs the new permissions. Re-open the invite link and authorize again, or add **Manage Roles**
   and **View Channels** to his role in Server Settings → Roles. Then run `/setup` once.

The container fixes the volume's ownership at startup, then runs the bot as an unprivileged user. You
don't need `RAILWAY_RUN_UID=0`; don't set it. Memory is capped with `JAVA_OPTS=-Xmx256m` to keep the bill
low. Override that variable in Railway if you ever need more.

#### Docker anywhere else

On a VPS, a Raspberry Pi, or any host that runs Docker containers:

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
    It tells Big Hank when he joins or leaves a server and which channels and roles exist. The bot can't read messages,
    see member lists, or watch presences.
  - **Three permissions, each for one job.** Big Hank doesn't need Send Messages or anything else, because
    slash-command replies work without them.
    - **Manage Channels** creates #big-hanks-lake.
    - **Manage Roles** creates the Lake Angler role, hands it out on `/start`, removes it on Leave, and sets
      the channel so only anglers can see it. It also moves the Fish God role to each server's #1.
    - **View Channels** lets him grant anglers access to the channel. Discord only lets a bot grant
      permissions it has itself.
    - Manage Roles can only touch roles *below* Big Hank's own role. Both roles he creates (Lake Angler and
      Fish God) have zero permissions.
  - **Private by default.** The lake is hidden from @everyone. Only Lake Anglers, Big Hank, and admins can see
    it, so non-players never get notifications or unread badges from the game.
  - **Respects deletions.** If a server deletes #big-hanks-lake, Big Hank never recreates it on his own.
  - **Forgets servers that remove him.** That server's settings are deleted when he's kicked.
  - **`/setup` is restricted to Manage Server.** Discord hides it from everyone else, and the bot
    re-checks the permission when it runs.
  - **Guild-only commands.** Nothing works in DMs or group chats.
  - **No pings.** Mentions are disabled globally, so a player named `@everyone` can't ping anyone.
  - **No admin-only data commands are exposed to players.** The old `/save` and `/load`, which any player
    could run, are gone. Saving is automatic.
  - **Unprivileged container.** The Docker image only uses root long enough to take ownership of the data
    volume, then drops to an unprivileged user before the bot starts.
- `data/` is git-ignored too, since it contains Discord user IDs.

## Project layout

```
src/main/java/bighank/
├── BigHank.java            Startup: reads config, connects to Discord, registers commands
├── Config.java             Reads DISCORD_TOKEN etc. from the environment or .env
├── commands/BotCommands.java   The six slash commands, bait autocomplete, and button clicks
├── commands/Views.java         Every message, embed, and button layout
├── commands/ButtonId.java      Button IDs carry their owner and state, so nothing is stored per message
├── commands/LakeKeeper.java    Builds the private #big-hanks-lake channel and Lake Angler role
├── data/GameStore.java     Saves/loads players and server settings (JSON)
├── data/GuildSettings.java Per-server channel, role, and member list
└── game/                   The game rules: Inventory, Fish, Rod, Bait, Species, Leaderboard
```

Run the tests with `./mvnw test`.
