package org.innectis.Nether;

import java.util.ListIterator;

import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;

public class NetherPlayerListener extends PlayerListener {
	private static final int NETHER_COMPRESSION = 8;
	private static final boolean DEBUG = true;
	
	private NetherMain main;
	
	public NetherPlayerListener(NetherMain plugin) {
		main = plugin;
	}
	
	@Override
	public void onPlayerRespawn(PlayerRespawnEvent event) {		
		// Return nether-deaths to normal world
		if (event.getRespawnLocation().getWorld().getEnvironment().equals(Environment.NETHER)) {
			
            //get the world by name for respawning
            World respawnWorld = main.getServer().getWorld(main.getConfiguration().getString("respawn-world-name"));

            Location respawnLocation = respawnWorld.getSpawnLocation();
            System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + " respawns to world '" + respawnWorld.getName() + "'");
			event.setRespawnLocation(respawnLocation);
		}
	}
	
	@Override
	public void onPlayerMove(PlayerMoveEvent event) {
		Location loc = event.getTo();
		World world = loc.getWorld();

		int locX = loc.getBlockX();
		int locY = loc.getBlockZ();
		int locZ = loc.getBlockY();

		Block b = world.getBlockAt(locX, locZ, locY);
		if (!b.getType().equals(Material.PORTAL)) {
			// Not a portal.
			return;
		}
		
		if (DEBUG)
			System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Hit portal at " + locX + ", "+ locY);
		
		// For better mapping between nether and normal, always use the lowest
		// xyz portal block
		while (world.getBlockAt(locX, locZ - 1, locY).getType().equals(Material.PORTAL))
			--locZ;
		while (world.getBlockAt(locX - 1, locZ, locY).getType().equals(Material.PORTAL))
			--locX;
		while (world.getBlockAt(locX, locZ, locY - 1).getType().equals(Material.PORTAL))
			--locY;
		
		if (DEBUG)
			System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Using portal block:" + locX + ", " + locY + ", " + locZ);
		
		// Now check to see which way the portal is oriented.
		boolean orientX = world.getBlockAt(locX + 1, locZ, locY).getType().equals(Material.PORTAL);
		
		if (DEBUG) {
			if (orientX)
				System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Portal is X oriented.");
			else
				System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Portal is Y oriented.");
		}

		if (world.getEnvironment().equals(Environment.NORMAL)) {
			// First of all see if there IS a nether yet
			// Here we use "netherworld"
			World nether = main.getServer().getWorld("netherworld");
			if (nether == null) {
				nether = main.getServer().createWorld("netherworld", Environment.NETHER);
			}
			
			if (!nether.getEnvironment().equals(Environment.NETHER)) {
				// Don't teleport to a non-nether world
				System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": ERROR: Nether world not found, aborting transport.");
				return;
			}
			
			int signAdjX = 0;
			if (locX < 0)
				signAdjX = 1;
			int signAdjY = 0;
			if (locY < 0)
				signAdjY = 1;
			
			// Try to find a portal near where the player should land
			Block dest = nether.getBlockAt(((locX+signAdjX) / NETHER_COMPRESSION)-signAdjX, locZ, ((locY + signAdjY) / NETHER_COMPRESSION)-signAdjY);
			NetherPortal portal = NetherPortal.findPortal(dest, 2, event.getPlayer().getName());
			if (portal == null) {
				portal = NetherPortal.createPortal(dest, orientX);
			}
			
			// Go!
			Location spawn = portal.getSpawn(event.getPlayer().getLocation().getYaw());
			nether.loadChunk(spawn.getBlock().getChunk());
			event.getPlayer().teleportTo(spawn);
			event.setTo(spawn);
			
			event.setTo(spawn);
		} else if (world.getEnvironment().equals(Environment.NETHER)) {
			
            //find a sign on the portal that has the name of the world to portal to on it.
            String worldName = getWorldNameFromSign(loc, orientX);

            World normalWorld = main.getServer().getWorld(worldName);

            if (normalWorld == null) {
				// Don't teleport to a non-normal world
                System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": ERROR: Normal world (" + worldName + ") not found, aborting transport.");
                event.getPlayer().chat("Couldn't teleport you to the " + worldName + " world. Contact an Admin");
				return;
			}

			// Try to find a portal near where the player should land
            Block dest = normalWorld.getBlockAt(locX * NETHER_COMPRESSION, locZ, locY * NETHER_COMPRESSION);
			NetherPortal portal = NetherPortal.findPortal(dest, NETHER_COMPRESSION*2, event.getPlayer().getName());
			if (portal == null) {
				portal = NetherPortal.createPortal(dest, orientX);
			}
			
			// Go!
			Location spawn = portal.getSpawn(event.getPlayer().getLocation().getYaw());
            normalWorld.loadChunk(spawn.getBlock().getChunk());
			event.getPlayer().teleport(spawn);
			event.setTo(spawn);
		}
	}
	
    /**
     * Find a sign on the portal that will tell us which world the portal is destined to
     *
     * @param loc
     * @return
     */
    private String getWorldNameFromSign(Location loc, boolean orientX) {
        String worldName = null;
        int locX = loc.getBlockX();
        int locY = loc.getBlockY();
        int locZ = loc.getBlockZ();

        World w = loc.getWorld();

        if (orientX) {
            //portal is xOriented, so lets look up and down the columns for a sign

            Location[] cols = locateXColumns(loc);
            if (cols != null) {
                //got the two columns, so crawl up them and find a sign
                for (Location l : cols) {
                    worldName = dataOnSign(l);
                    if (worldName != null) break;
                }
            }
        } else {

        }
        if (worldName == null) {
            main.getConfiguration().getString("default-normal-world");
        }
        return worldName;
    }

    /**
     * Search around the location for a sign and upon finding it, extract the text
     *
     * @param l
     * @return
     */
    private String dataOnSign(Location l) {
        String output = null;
        Sign s = null;
        World w = l.getWorld();

        //loop up the thing, and while we don't have a sign
        for (int y = 0; y < 3 && s == null; y++) {
            for (int x = -1; x < 1; x += 2) {
                Block b = w.getBlockAt(l.getBlockX() + x, l.getBlockY(), l.getBlockZ());
                if (b.getType() == Material.SIGN) {
                    //Holy crap found a sign!
                    s = (Sign) b.getState();
                    break;
                }
            }
            if (s == null) {
                for (int z = -1; z < 1; z += 2) {
                    Block b = w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ() + z);
                    if (b.getType() == Material.SIGN) {
                        //Holy crap found a sign!
                        s = (Sign) b.getState();
                        break;
                    }
                }
            }
        }
        if (s != null) {
            StringBuilder sb = new StringBuilder();
            for (String line : s.getLines()) {
                sb.append(line);
            }
            output = sb.toString();
        }
        return output;
    }

    private Location[] locateXColumns(Location l) {
        //limited search scope
        Location[] columns = null;
        World w = l.getWorld();
        for (int x = -2; x < 2; x++) {
            if (w.getBlockAt(l.getBlockX() + x, l.getBlockY(), l.getBlockZ()).getType() == Material.OBSIDIAN &&
                    w.getBlockAt(l.getBlockX() + x + 1, l.getBlockY(), l.getBlockZ()).getType() == Material.PORTAL &&
                    w.getBlockAt(l.getBlockX() + x + 2, l.getBlockY(), l.getBlockZ()).getType() == Material.PORTAL &&
                    w.getBlockAt(l.getBlockX() + x + 3, l.getBlockY(), l.getBlockZ()).getType() == Material.OBSIDIAN) {
                //Found the two columns!
                columns = new Location[2];
                columns[0] = new Location(l.getWorld(), l.getBlockX() + x, l.getBlockY(), l.getBlockZ());
                columns[1] = new Location(l.getWorld(), l.getBlockX() + x + 3, l.getBlockY(), l.getBlockZ());
                break;
            }
        }
        return columns;
    }

    private Location[] locateYColumns(Location l) {
        //limited search scope
        Location[] columns = new Location[2];
        World w = l.getWorld();
        for (int x = -2; x < 2; x++) {
            if (w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ() + x).getType() == Material.OBSIDIAN &&
                    w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ() + x + 1).getType() == Material.PORTAL &&
                    w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ() + x + 2).getType() == Material.PORTAL &&
                    w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ() + x + 3).getType() == Material.OBSIDIAN) {
                //Found the two columns!
                columns[0] = new Location(l.getWorld(), l.getBlockX() + x, l.getBlockY(), l.getBlockZ());
                columns[1] = new Location(l.getWorld(), l.getBlockX() + x + 3, l.getBlockY(), l.getBlockZ());
                break;
            }
        }
        return columns;
    }

	public void ProcessMoveTo(Player player, Location location)	{
		if (location.getWorld().getEnvironment().equals(Environment.NETHER))
			System.out.println("NETHER_PLUGIN: " + player.getName() + ": Teleporting to NETHER!");
		else
			System.out.println("NETHER_PLUGIN: " + player.getName() + ": Teleporting to NORMAL WORLD!");
		
		player.teleportTo(location);
	}
}