package nickrak.HealingWater;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Scanner;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.nijiko.permissions.PermissionHandler;
import com.nijiko.permissions.User;
import com.nijikokun.bukkit.Permissions.Permissions;
import org.bukkit.plugin.Plugin;

public class HealingWater extends JavaPlugin implements Runnable
{
	private static PermissionHandler ph;

	private Thread t_healing = new Thread(this);
	private boolean healingActive = false;

	private int healAmount;
	private int healDelay;

	private enum HealAirMode {
		HEAL_ALWAYS, HEAL_WITH_FULL_AIR, HEAL_WITH_SOME_AIR
	}

	private HealAirMode healAirMode;

	@Override
	public void onDisable()
	{
		healingActive = false;
		try
		{
			System.out.println("[Healing Water] Stopping...");
			t_healing.join();
			System.out.println("[Healing Water] Stopped.");
		}
		catch (InterruptedException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void onEnable()
	{
		File f = new File(this.getDataFolder().getAbsolutePath() + File.separatorChar + "HealingWater.conf");

		if (!f.exists())
		{
			this.getDataFolder().mkdirs();
			try
			{
				FileWriter fw = new FileWriter(f);
				fw.write("# Healing Water Configuration.\n\r");
				fw.write("# healingDelay=[millisecond delay between consecutive healing]\n\r");
				fw.write("# healingAmount=[number of healts to heal]\n\r");
				fw.write("# healAirMode=[ HEAL_ALWAYS | HEAL_WITH_FULL_AIR | HEAL_WITH_SOME_AIR ]\n\r#\n\r");
				fw.write("# Defaults: healingDelay=500, healingAmount=0.5 ... heals half a heart every half a second, healAirMode=HEAL_WITH_FULL_AIR\n\r");
				fw.write("healingDelay=500\n\r");
				fw.write("healingAmount=0.5\n\r");
				fw.write("healAirMode=HEAL_WITH_FULL_AIR");
				fw.flush();
				fw.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		try
		{
			Scanner scan = new Scanner(f);
			while (scan.hasNextLine())
			{
				String line = scan.nextLine();
				if (line.startsWith("#"))
					continue;
				String[] parts = line.split("=");

				if (parts[0].trim().equalsIgnoreCase("healingDelay"))
				{
					this.healDelay = Integer.parseInt(parts[1]);
				}
				if (parts[0].trim().equalsIgnoreCase("healingAmount"))
				{
					this.healAmount = (int) (Double.parseDouble(parts[1]) * 2);
				}
				if (parts[0].trim().equalsIgnoreCase("healAirMode"))
				{
					this.healAirMode = null;
					this.healAirMode = HealAirMode.valueOf(parts[1]);
					if (this.healAirMode == null)
					{
						this.healAirMode = HealAirMode.HEAL_WITH_FULL_AIR;
					}
				}
			}
		}
		catch (FileNotFoundException e)
		{
			e.printStackTrace();
		}

		healingActive = true;
		t_healing.start();

		Plugin pp = this.getServer().getPluginManager().getPlugin("Permissions");
		if (ph == null)
		{
			if (pp != null)
			{
				ph = ((Permissions) pp).getHandler();
			}
			else
			{
				System.out.println("[Healing Water] Permissions not found, defaulting to file");
			}
		}

		System.out.println("[Healing Water] Version " + this.getDescription().getVersion() + " Started. (" + this.healAmount / 2.0 + " hearts every "
				+ this.healDelay + " milliseconds.)");
	}

	private boolean shouldHeal(Player p)
	{
		if (this.healAirMode == HealAirMode.HEAL_ALWAYS)
		{
			return true; 
		}
		if (this.healAirMode == HealAirMode.HEAL_WITH_SOME_AIR)
		{
			return p.getRemainingAir() > 0;
		}
		return p.getRemainingAir() == p.getMaximumAir();
	}

	@Override
	public void run()
	{
		while (healingActive)
		{
			for (Player p : this.getServer().getOnlinePlayers())
			{
				int dHealth = 0;

				if (ph != null)
				{
					try
					{
						final User user = ph.getUserObject(p.getWorld().getName(), p.getName());
						final Set<String> perms = (user == null ? ph.getDefaultGroup(p.getWorld().getName()).getAllPermissions() : user
								.getAllPermissions());

						for (String perm : perms)
						{
							final String pe = perm.toLowerCase();
							if (pe.matches("healingwater.heal.*"))
							{
								try
								{
									final String p2 = pe.substring("healingwater.heal.".length());
									final int amount = Integer.parseInt(p2);
									dHealth += amount;
								}
								catch (NumberFormatException e)
								{
								}
							}
							else if (pe.matches("healingwater.damage.*"))
							{
								try
								{
									final String p2 = pe.substring("healingwater.damage.".length());
									final int amount = Integer.parseInt(p2);
									dHealth -= amount;
								}
								catch (NumberFormatException e)
								{
								}
							}
						}
					}
					catch (NullPointerException npe)
					{
						continue;
					}
				}
				else
				{
					dHealth = this.healAmount;
				}

				final Material s = p.getLocation().getBlock().getType();
				if ((s == Material.WATER || s == Material.STATIONARY_WATER) && shouldHeal(p))
				{
					int h = p.getHealth();
					if (dHealth <= 0)
					{
						p.damage(-dHealth);
					}
					else
					{
						h += dHealth;
						if (h > 20)
						{
							h = 20;
						}
						p.setHealth(h);
					}
				}
			}

			try
			{
				Thread.sleep(this.healDelay);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
}