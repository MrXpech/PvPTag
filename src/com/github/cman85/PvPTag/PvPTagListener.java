package com.github.cman85.PvPTag;

import com.massivecraft.factions.FFlag;
import com.massivecraft.factions.entity.BoardColls;
import com.massivecraft.factions.entity.FactionColls;
import com.massivecraft.factions.entity.UConf;
import com.massivecraft.factions.entity.UPlayer;
import com.massivecraft.mcore.ps.PS;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkUnloadEvent;

public class PvPTagListener implements Listener {
   private PvPTag pvptag;
   private long lastLogout = System.currentTimeMillis();

   public PvPTagListener(PvPTag pt) {
      this.pvptag = pt;
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onHit(EntityDamageByEntityEvent e) {

      if(! pvptag.configuration.isPVPWorld(e)) return;
      if(! pvptag.taggingEnabled) return;
      if(e.getDamager() instanceof Snowball) e.setCancelled(true);
      PvPTag.debug("Event fired");
      if(e.getEntity() instanceof Player) {
         PvPTag.debug("Entity is a player");
         Player hitter;
         Player hitted = (Player)e.getEntity();
         if(e.getDamager() instanceof Arrow) {
            Arrow arrow = (Arrow)e.getDamager();
            if(arrow.getShooter() instanceof Player) {
               hitter = (Player)arrow.getShooter();
            } else {
               return;
            }
         } else if(e.getDamager() instanceof Player) {
            hitter = (Player)e.getDamager();

         } else if(e.getDamager() instanceof Zombie) {
            if(PvPLoggerZombie.isPvPZombie((Zombie)e.getDamager())) {
               if(pvptag.isSafe(hitted.getName())) e.setCancelled(true);
            }
            return;
         } else {
            return;
         }
         if (! e.isCancelled()) {
            PvPTag.debug("Event shouldn't be cancelled");
            if(pvptag.isSafe(hitted.getName())) {
               pvptag.addUnsafe(hitted);
            } else {
               pvptag.resetSafeTime(hitted);
            }
            if(pvptag.isSafe(hitter.getName())) {
               pvptag.addUnsafe(hitter);
            } else {
               pvptag.resetSafeTime(hitter);
            }
         } else {
            PvPTag.debug("Event should be cancelled");
            if (pvptag.factionsEnabled) {
               if (UPlayer.get(hitted).getFaction().equals(UPlayer.get(hitter).getFaction()) &&
                       !BoardColls.get().getForUniverse("default").getFactionAt(PS.valueOf((Entity)hitted)).getFlag(FFlag.FRIENDLYFIRE)) {
                  return;
               }
            }
            if(! pvptag.isSafe(hitted.getName()) && hitter.getInventory().getItemInHand() != null) {
               PvPTag.debug("Player isn't safe: " + hitted.getName());
               pvptag.resetSafeTime(hitted);
               if(pvptag.isSafe(hitter.getName())) {
                  PvPTag.debug("Player is safe: " + hitter.getName());
                  if(! pvptag.antiPilejump) {
                     e.setCancelled(false);
                     pvptag.addUnsafe(hitter);
                  }
               } else {
                  e.setCancelled(false);
                  pvptag.resetSafeTime(hitter);
               }
            }
         }
      }
   }

   @EventHandler
   public void onDeath(PlayerRespawnEvent e) {
      pvptag.safeTimes.remove(e.getPlayer().getName());
      pvptag.clearFromBoard(e.getPlayer());

      if(pvptag.useDeathTP)
         pvptag.deathTimes.put(e.getPlayer().getName(), pvptag.calcSafeTime(pvptag.DEATH_TP_DELAY));
   }

   @EventHandler
   public void onEnderpearl(PlayerInteractEvent e) {

      if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) && e.getMaterial() == Material.ENDER_PEARL &&
              ! pvptag.isSafe(e.getPlayer().getName()) && pvptag.preventTeleport) {

         e.getPlayer().sendMessage(ChatColor.RED + "You cannot enderpearl until you are safe.");
         e.setCancelled(true);

      }

   }

   @EventHandler(priority =  EventPriority.LOW, ignoreCancelled = true)
   public void onTpEvent(PlayerTeleportEvent e) {
      if(! pvptag.isSafe(e.getPlayer().getName()) && ! e.getPlayer().isOp() && pvptag.preventTeleport &&
              (e.getCause() == PlayerTeleportEvent.TeleportCause.COMMAND || e.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN)) {
         e.setCancelled(true);
         e.getPlayer().sendMessage(ChatColor.RED + "You cannot teleport until you are safe.");
      } else {
         if(pvptag.deathTimes.containsKey(e.getPlayer().getName()) && pvptag.useDeathTP && ! e.getPlayer().isOp()) {
            Long deathTime = pvptag.deathTimes.get(e.getPlayer().getName());
            Long currTime = System.currentTimeMillis();
            if(deathTime > currTime) {
               e.getPlayer().sendMessage("§cYou cannot teleport for " + (pvptag.DEATH_TP_DELAY / 1000) + " seconds after dying. Time left: §6" + (deathTime / 1000 - currTime / 1000));
               e.setCancelled(true);
            } else {

            }
         }
      }
   }

   @SuppressWarnings("deprecation")
   @EventHandler
   public void onJoin(PlayerJoinEvent e) {
      if(PvPLoggerZombie.waitingToDie.contains(e.getPlayer().getName())) {
         e.getPlayer().getInventory().clear();
         e.getPlayer().setHealth(0);
         e.getPlayer().updateInventory();
         PvPLoggerZombie.waitingToDie.remove(e.getPlayer().getName());
      }
      PvPLoggerZombie pz = PvPLoggerZombie.getByOwner(e.getPlayer().getName());
      if(pz != null) {
         pvptag.addUnsafe(e.getPlayer());
         e.getPlayer().teleport(pz.getZombie().getLocation());
         pz.despawnNoDrop(true, true);
         e.getPlayer().setHealth(pz.getHealthForOwner());
      }
   }

   @EventHandler
   public void entityDeath(EntityDeathEvent e) {
      if(e.getEntity() instanceof Zombie) {
         PvPLoggerZombie pz = PvPLoggerZombie.getByZombie((Zombie)e.getEntity());
         if(pz != null) {
            PvPLoggerZombie.waitingToDie.add(pz.getPlayer());
            pz.despawnDrop(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onFlight(PlayerToggleFlightEvent e) {
      if(pvptag.disableFlight && ! pvptag.isSafe(e.getPlayer().getName())) {
         e.getPlayer().setFlying(false);
         e.getPlayer().setAllowFlight(false);
         e.setCancelled(true);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent e) {
      if(! pvptag.isSafe(e.getPlayer().getName()) && pvptag.pvpZombEnabled) {
         lastLogout = System.currentTimeMillis();
         new PvPLoggerZombie(e.getPlayer().getName());
      }
   }

   @EventHandler(priority = EventPriority.HIGHEST)
   public void onCreature(CreatureSpawnEvent e) {
      if(e.getEntity() instanceof Zombie) {
         if(System.currentTimeMillis() - lastLogout < 20) {
            e.setCancelled(false);
         } else {
         }
      }
   }

   @EventHandler
   public void onChunk(ChunkUnloadEvent e) {
      Chunk c = e.getChunk();
      for(Entity en : c.getEntities()) {
         if(en.getType() == EntityType.ZOMBIE) {
            Zombie z = (Zombie)en;
            if(PvPLoggerZombie.isPvPZombie(z)) {
               PvPLoggerZombie pz = PvPLoggerZombie.getByZombie(z);
               pz.despawnDrop(true);
               pz.killOwner();
            }
         }
      }
   }

   @EventHandler
   public void onProject(ProjectileLaunchEvent e) {
      if(pvptag.disableEnderpearls) {
         if(e.getEntity() instanceof EnderPearl) {
            EnderPearl pearl = (EnderPearl)e.getEntity();
            if(pearl.getShooter() instanceof Player) {
               Player p = (Player)pearl.getShooter();
               if(! pvptag.isSafe(p.getName())) e.setCancelled(true);
            }
         }
      }
   }

   @EventHandler
   public void command(PlayerCommandPreprocessEvent e) {
      if(pvptag.configuration.isBannedCommand(e.getMessage()) && ! pvptag.isSafe(e.getPlayer().getName()))
         e.setCancelled(true);
   }

}