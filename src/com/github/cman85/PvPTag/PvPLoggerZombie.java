package com.github.cman85.PvPTag;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

public class PvPLoggerZombie {
   public static Set<PvPLoggerZombie> zombies = new HashSet<PvPLoggerZombie>();
   public static Set<String> waitingToDie = new HashSet<String>();
   public static Set<Integer> zombieIds = new HashSet<Integer>();
   public static int HEALTH = 50;
   private double hp = 10;
   private Zombie zombie;
   private String player;
   private PlayerInventory contents;

   public PvPLoggerZombie(String player) {
      this.player = player;
      Player p = Bukkit.getPlayer(player);
      hp = p.getHealth();
      zombieIds.add((zombie = (Zombie)p.getWorld().spawnEntity(p.getLocation(), EntityType.ZOMBIE)).getEntityId());
      zombie.getWorld().playEffect(zombie.getLocation(), Effect.MOBSPAWNER_FLAMES, 1, 1);
      zombie.setRemoveWhenFarAway(false);
      setInventoryContents(p.getInventory());
      Iterator<PvPLoggerZombie> it = zombies.iterator();
      while(it.hasNext()) {
         PvPLoggerZombie pz = it.next();
         if(pz.getPlayer().equalsIgnoreCase(player)) {
            despawnDrop(false);
            it.remove();
         }
      }
      zombies.add(this);
   }

   public Zombie getZombie() {
      return zombie;
   }

   public void setZombie(Zombie zombie) {
      this.zombie = zombie;
   }

   public String getPlayer() {
      return player;
   }

   public void setPlayer(String player) {
      this.player = player;
   }

   public void setInventoryContents(PlayerInventory pi) {
      zombie.setMaxHealth(getHealth());
      zombie.setHealth(getHealth());
      zombie.setRemoveWhenFarAway(false);
      zombie.setCanPickupItems(false);
      zombie.getEquipment().setArmorContents(pi.getArmorContents());
      zombie.getEquipment().setItemInHand(pi.getItemInHand());
      zombie.getEquipment().setBootsDropChance(100);
      zombie.getEquipment().setChestplateDropChance(100);
      zombie.getEquipment().setHelmetDropChance(100);
      zombie.getEquipment().setLeggingsDropChance(100);
      zombie.getEquipment().setItemInHandDropChance(100);
      pi.setArmorContents(new ItemStack[] { null, null, null, null });
      pi.setItemInHand(null);
      this.contents = pi;
   }

   public List<ItemStack> itemsToDrop() {
      List<ItemStack> itemsToDrop = new ArrayList<ItemStack>();
      for(ItemStack i : contents.getContents()) {
         if(i != null) itemsToDrop.add(i);
      }
      return itemsToDrop;
   }

   public void despawnNoDrop(boolean giveToOwner, boolean iterate) {
      zombie.getEquipment().setBootsDropChance(0);
      zombie.getEquipment().setChestplateDropChance(0);
      zombie.getEquipment().setHelmetDropChance(0);
      zombie.getEquipment().setLeggingsDropChance(0);
      zombie.getEquipment().setItemInHandDropChance(0);
      if(giveToOwner) {
         Player p = Bukkit.getPlayer(player);
         if(p == null) {
            PvPTag.log(Level.WARNING, "Player was null!");
            return;
         }
         p.getInventory().setContents(contents.getContents());
         p.getInventory().setArmorContents(zombie.getEquipment().getArmorContents());
         p.getInventory().setItemInHand(zombie.getEquipment().getItemInHand());
      }
      zombie.remove();
      if(iterate)
         despawn();
   }

   public void despawn() {
      Iterator<PvPLoggerZombie> it = zombies.iterator();
      while(it.hasNext()) {
         PvPLoggerZombie pz = it.next();
         if(pz.getPlayer().equalsIgnoreCase(player)) it.remove();
      }
      zombie.remove();
   }

   public void despawnDrop(boolean iterate) {
      zombie.setCanPickupItems(false);
      for(ItemStack is : contents.getContents()) {
         if(is != null)
            zombie.getWorld().dropItemNaturally(zombie.getLocation(), is);
      }
      zombie.getWorld().playEffect(zombie.getLocation(), Effect.ENDER_SIGNAL, 1, 1);
      zombie.setHealth(0);
      zombie.remove();
      if(iterate)
         despawn();
   }

   public static PvPLoggerZombie getByOwner(String owner) {
      for(PvPLoggerZombie pz : zombies) {
         if(pz.getPlayer().equalsIgnoreCase(owner)) return pz;
      }
      return null;
   }

   public static PvPLoggerZombie getByZombie(Zombie z) {
      for(PvPLoggerZombie pz : zombies) {
         if(zombieEquals(pz.getZombie(), z)) return pz;
      }
      return null;
   }

   private static boolean zombieEquals(Zombie z1, Zombie z2) {
      return (z1.getEntityId() == (z2.getEntityId()));
   }

   public static boolean isPvPZombie(Zombie z) {
      return zombieIds.contains(z.getEntityId());
   }

   public void killOwner() {
      waitingToDie.add(player);
   }

   public double getHealth() {
      if(! PvPTag.keepPlayerHealthZomb)
         return HEALTH;
      else {
         return hp;
      }
   }

   public double getHealthForOwner() {
      if(! PvPTag.keepPlayerHealthZomb)
         return hp;
      else
         return zombie.getHealth();

   }
}
