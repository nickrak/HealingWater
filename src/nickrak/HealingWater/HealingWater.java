package nickrak.HealingWater;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class HealingWater extends JavaPlugin implements Runnable
{
	private final Thread t_healing = new Thread(this);
	private final Logger l = Logger.getLogger("HealingWater");
	private volatile boolean running = false;
	protected final ConcurrentHashMap<String, Integer> healingAmounts = new ConcurrentHashMap<String, Integer>();

	@Override
	public final void onDisable()
	{
		try
		{
			this.running = false;
			this.t_healing.join();
			this.l.info("[HealingWater] Disabled.");
		}
		catch (final InterruptedException e)
		{
		}
	}

	@Override
	public final void onEnable()
	{
		final PlayerListener pl = new PlayerListener()
		{
			@Override
			public final void onPlayerQuit(PlayerQuitEvent event)
			{
				final String name = event.getPlayer().getName();
				HealingWater.this.healingAmounts.remove(name);
			}

			@Override
			public final void onPlayerJoin(PlayerJoinEvent event)
			{
				final Player p = event.getPlayer();
				HealingWater.this.healingAmounts.put(p.getName(), getHealAmount(p));
			}

			private final int getHealAmount(final Player p)
			{
				int amount = 0;
				for (int i = 1; i <= 20; i++)
				{
					if (p.hasPermission("healingwater.heal." + i))
					{
						amount += i;
					}
					if (p.hasPermission("healingwater.damage." + i))
					{
						amount -= i;
					}
				}

				return amount;
			}
		};

		this.running = true;

		final PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvent(Type.PLAYER_JOIN, pl, Priority.Monitor, this);
		pm.registerEvent(Type.PLAYER_QUIT, pl, Priority.Monitor, this);

		this.t_healing.start();
		this.l.info("[HealingWater] Enabled (Version " + this.getDescription().getVersion() + ").");
	}

	private final boolean shouldHeal(final Player p)
	{
		int b = p.getRemainingAir();

		if (b == p.getMaximumAir())
		{
			return true;
		}

		if (b == 0 && !p.hasPermission("healingwater.mode.hydrophobic"))
		{
			return p.hasPermission("healingwater.mode.aquatic");
		}

		return !p.hasPermission("healingwater.mode.hydrophobic");
	}

	@Override
	public final void run()
	{
		while (this.running)
		{
			for (final Player p : this.getServer().getOnlinePlayers())
			{
				final Material m = p.getLocation().getBlock().getType();
				if ((m == Material.WATER || m == Material.STATIONARY_WATER) && shouldHeal(p))
				{
					final int a = this.healingAmounts.get(p.getName());
					if (a < 0)
					{
						p.damage(-a);
					}
					else if (a > 0)
					{
						final int newHealth = p.getHealth() + a;
						p.setHealth(newHealth >= 20 ? 20 : newHealth);
					}
				}
			}

			try
			{
				Thread.sleep(1000);
			}
			catch (InterruptedException e)
			{
			}
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args)
	{
		if (label.equalsIgnoreCase("starve"))
		{
			if (sender instanceof Player)
			{
				((Player) sender).setFoodLevel(0);
				return true;
			}
		}
		return false;
	}
}
