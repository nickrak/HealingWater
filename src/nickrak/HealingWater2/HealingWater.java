package nickrak.HealingWater2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HealingWater extends JavaPlugin implements Runnable
{
    private final class CacheUpdater implements Runnable
    {
        private final HealingWater main;

        public CacheUpdater(HealingWater main)
        {
            this.main = main;
        }

        private final int getSum(final Player p, final String prefix)
        {
            int sum = 0;
            for (int i = 1; i <= 20; i++)
            {
                if (p.hasPermission(prefix + "." + i))
                {
                    sum += i;
                }
            }
            return sum;
        }

        @Override
        public final void run()
        {
            for (final Player p : this.main.getServer().getOnlinePlayers())
            {
                if (!p.isOnline()) continue;

                final int waterHeal = this.getSum(p, "healingwater.heal");
                final int waterDamg = this.getSum(p, "healingwater.damage");
                final int lavaHeal = this.getSum(p, "healingwater.lava.heal");
                final int lavaDamg = this.getSum(p, "healingwater.lava.damage");
                final boolean ignoreLava = (lavaHeal == 0 && lavaDamg == 0);
                final String pname = p.getName();

                this.main.healInWater.put(pname, waterHeal);
                this.main.damgInWater.put(pname, waterDamg);
                this.main.healInLava.put(pname, lavaHeal);
                this.main.damgInLava.put(pname, lavaDamg);
                this.main.ignoreGeneralLava.put(pname, ignoreLava);
            }
        }
    }

    private final Logger l = Logger.getLogger("HealingWater");

    private final void log(final String msg)
    {
        l.info("[" + this.getDescription().getName() + "] " + msg);
    }

    private final void clearAll()
    {
        this.healInLava.clear();
        this.healInWater.clear();
        this.damgInLava.clear();
        this.damgInWater.clear();
        this.ignoreGeneralLava.clear();
    }

    @Override
    public final void onDisable()
    {
        this.clearAll();
        this.log("Disabled");
    }

    @Override
    public final void onEnable()
    {
        this.clearAll();

        final PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvent(Type.PLAYER_QUIT, new PlayerListener()
        {
            @Override
            public final void onPlayerQuit(PlayerQuitEvent event)
            {
                final String p = event.getPlayer().getName();

                healInLava.remove(p);
                healInWater.remove(p);
                damgInLava.remove(p);
                damgInWater.remove(p);
                ignoreGeneralLava.remove(p);
            }
        }, Priority.Monitor, this);
        pm.registerEvent(Type.ENTITY_DAMAGE, new EntityListener()
        {
            @Override
            public void onEntityDamage(EntityDamageEvent event)
            {
                if (event.isCancelled()) return;
                if (event.getEntity() instanceof Player)
                {
                    if (event.getCause().equals(DamageCause.LAVA))
                    {
                        final String p = ((Player) event.getEntity()).getName();
                        final boolean cancel = ignoreGeneralLava.containsKey(p) && ignoreGeneralLava.get(p);
                        event.setCancelled(cancel);
                    }
                }
            }
        }, Priority.Normal, this);

        Bukkit.getScheduler().scheduleSyncRepeatingTask(this, this, 20L, 20L);
        Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, this.cu, 20L, 20L);

        this.log("Enabled version " + this.getDescription().getVersion());
    }

    private final boolean shouldAffect(final Player p)
    {
        final int ra = p.getRemainingAir();

        if (ra == p.getMaximumAir()) return true;
        if (p.hasPermission("healingwater.mode.hydrophobic")) return false;
        if (p.hasPermission("healingwater.mode.aquatic")) return true;

        return ra > 0;
    }

    private final CacheUpdater cu = new CacheUpdater(this);
    private final ConcurrentHashMap<String, Integer> healInWater = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentHashMap<String, Integer> damgInWater = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentHashMap<String, Integer> healInLava = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentHashMap<String, Integer> damgInLava = new ConcurrentHashMap<String, Integer>();
    private final ConcurrentHashMap<String, Boolean> ignoreGeneralLava = new ConcurrentHashMap<String, Boolean>();

    @Override
    public final void run()
    {
        for (final Player p : this.getServer().getOnlinePlayers())
        {
            if (p.isDead() || p.isInsideVehicle() || p.isSleeping()) continue;

            final Material m = p.getLocation().getBlock().getType();
            final String pname = p.getName();

            if (m == Material.AIR)
            {
                continue;
            }
            else if (m == Material.WATER || m == Material.STATIONARY_WATER)
            {
                if (this.shouldAffect(p))
                {
                    final int heal = healInWater.get(pname);
                    final int damg = damgInWater.get(pname);
                    final int net = heal - damg;

                    this.applyNetChange(p, net);
                }
            }
            else if (m == Material.LAVA || m == Material.STATIONARY_LAVA)
            {
                final int heal = healInLava.get(pname);
                final int damg = damgInLava.get(pname);
                final int net = heal - damg;

                this.applyNetChange(p, net);
            }
        }
    }

    private final void applyNetChange(final Player p, final int delta)
    {
        if (delta > 0)
        {
            final int oldHealth = p.getHealth();
            final int newHealth = oldHealth + delta;
            final int maxHealth = p.getMaxHealth();
            p.setHealth(newHealth >= maxHealth ? maxHealth : newHealth);
        }
        else if (delta < 0)
        {
            p.damage(delta);
        }
    }
}
