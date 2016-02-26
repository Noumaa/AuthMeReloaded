package fr.xephi.authme.process.quit;

import fr.xephi.authme.AuthMe;
import fr.xephi.authme.cache.auth.PlayerAuth;
import fr.xephi.authme.cache.auth.PlayerCache;
import fr.xephi.authme.cache.limbo.LimboCache;
import fr.xephi.authme.cache.limbo.LimboPlayer;
import fr.xephi.authme.datasource.CacheDataSource;
import fr.xephi.authme.datasource.DataSource;
import fr.xephi.authme.settings.Settings;
import fr.xephi.authme.util.StringUtils;
import fr.xephi.authme.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class AsynchronousQuit {

    private final AuthMe plugin;
    private final DataSource database;
    private final Player player;
    private final String name;
    private boolean isOp = false;
    private boolean needToChange = false;
    private boolean isKick = false;

    public AsynchronousQuit(Player p, AuthMe plugin, DataSource database,
                            boolean isKick) {
        this.player = p;
        this.plugin = plugin;
        this.database = database;
        this.name = p.getName().toLowerCase();
        this.isKick = isKick;
    }

    public void process() {
        if (player == null || Utils.isUnrestricted(player)) {
            return;
        }

        String ip = plugin.getIP(player);

        if (PlayerCache.getInstance().isAuthenticated(name)) {
            if (Settings.isSaveQuitLocationEnabled) {
                Location loc = player.getLocation();
                PlayerAuth auth = PlayerAuth.builder()
                    .name(name).location(loc)
                    .realName(player.getName()).build();
                database.updateQuitLoc(auth);
            }
            PlayerAuth auth = new PlayerAuth(name, ip, System.currentTimeMillis(), player.getName());
            database.updateSession(auth);
        }

        LimboPlayer limbo = LimboCache.getInstance().getLimboPlayer(name);
        if (limbo != null) {
            if (!StringUtils.isEmpty(limbo.getGroup())) {
                Utils.addNormal(player, limbo.getGroup());
            }
            needToChange = true;
            isOp = limbo.getOperator();
            LimboCache.getInstance().deleteLimboPlayer(name);
        }
        if (Settings.isSessionsEnabled && !isKick) {
            if (Settings.getSessionTimeout != 0) {
                if (plugin.isEnabled()) {
                    BukkitTask task = plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, new Runnable() {

                        @Override
                        public void run() {
                            postLogout();
                        }

                    }, Settings.getSessionTimeout * 20 * 60);

                    plugin.sessions.put(name, task);
                } else {
                    //plugin is disable we canno schedule more tasks so run it directly here
                    postLogout();
                }
            }
        } else {
            PlayerCache.getInstance().removePlayer(name);
            database.setUnlogged(name);
        }

        plugin.realIp.remove(name);
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new ProcessSyncronousPlayerQuit(plugin, player, isOp, needToChange));
        }
        // remove player from cache
        if (database instanceof CacheDataSource) {
            ((CacheDataSource) database).getCachedAuths().invalidate(name);
        }
    }

    private void postLogout() {
        PlayerCache.getInstance().removePlayer(name);
        database.setUnlogged(name);
        plugin.sessions.remove(name);
    }
}
