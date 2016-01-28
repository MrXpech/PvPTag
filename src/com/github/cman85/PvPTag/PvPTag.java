package com.github.cman85.PvPTag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Objective;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PvPTag extends JavaPlugin implements Listener {

   private final MainCommandListener mainCommandListener = new MainCommandListener(this);
   HashMap<String, Long> safeTimes = new HashMap<String, Long>();
   HashMap<String, Long> deathTimes = new HashMap<String, Long>();

   private Set<String> couldFly = new HashSet<String>();
   private Set<String> hadFlight = new HashSet<String>();
   private Set<String> consoleCommands = new HashSet<String>();
   private Set<String> playerCommands = new HashSet<String>();

   private static Logger logger;

   private static final boolean DEBUG = false;

   private TagAPEye tagApi;
   private Updater updater;
   private DeathChestListener dcl;
   ScoreboardFeatures scoreboard;
   private static PvPTag instance;
   Config configuration;
   ChatColor nameTagColor;

   long SAFE_DELAY = 30000;
   long DEATH_TP_DELAY = 30000;

   boolean useDeathTP = true;
   boolean disableFlight = true;
   boolean antiPilejump = false;
   boolean unInvis = true;
   boolean preventTeleport = true;
   boolean pvpZombEnabled = true;
   boolean taggingEnabled = true;
   boolean disableEnderpearls = true;
   boolean deathChestEnabled = true;
   boolean factionsEnabled = false;

   boolean healthObjective = true;
   boolean safeTimeObjective = true;

   static boolean keepPlayerHealthZomb = true;

   public void onEnable() {
      instance = this;
      configuration = new Config(this);
      logger = getLogger();
      dcl = new DeathChestListener(this);
      manageConfig();
      manageInstances();
      scoreboard = new ScoreboardFeatures(false, safeTimeObjective);
      getServer().getPluginManager().registerEvents(new PvPTagListener(this), this);
      task();
   }

   private void manageInstances() {
      if(configuration.getConfig().getBoolean("Death.DeathChest Enabled"))
         getServer().getPluginManager().registerEvents(dcl, this);
      if(configuration.getConfig().getBoolean("Tagging.Use TagAPI") && getServer().getPluginManager().getPlugin("TagAPI") != null) {
         this.tagApi = new TagEnabled(this);
      } else {
         this.tagApi = new TagDisabled();
      }
      getServer().getPluginManager().registerEvents(tagApi, this);

      if(configuration.getConfig().getBoolean("Auto update", false))
         updater = new Updater(this, "pvp-tag", this.getFile(), Updater.UpdateType.DEFAULT, false);
      if(configuration.getConfig().getBoolean("Send Usage")) {
         try {
            Metrics m = new Metrics(this);
            m.start();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
   }

   void manageConfig() {
      configuration.enable();
      this.SAFE_DELAY = configuration.getConfig().getInt("Tagging.Safe Time", 30) * 1000;
      this.DEATH_TP_DELAY = configuration.getConfig().getInt("Death.DeathTP Time", 30) * 1000;
      DeathChest.CHEST_BREAK_DELAY = configuration.getConfig().getInt("Death.Chest Time", 45) * 1000;
      useDeathTP = configuration.getConfig().getBoolean("Death.DeathTP Enabled", true);
      this.nameTagColor = configuration.parseNameTagColor();

      PvPLoggerZombie.HEALTH = configuration.getConfig().getInt("PvPLogger Zombie.Health", 50);

      this.disableFlight = configuration.getConfig().getBoolean("Tagging.Disable Flying", true);
      this.unInvis = configuration.getConfig().getBoolean("Tagging.Remove Invisible", true);
      this.taggingEnabled = configuration.getConfig().getBoolean("Tagging.Enabled", true);
      this.antiPilejump = configuration.getConfig().getBoolean("Tagging.Anti Pilejump", true);
      this.pvpZombEnabled = configuration.getConfig().getBoolean("PvPLogger Zombie.Enabled", true);
      this.disableEnderpearls = configuration.getConfig().getBoolean("Tagging.Disable Enderpearls", true);
      this.preventTeleport = configuration.getConfig().getBoolean("Tagging.Prevent Teleport", true);
      PvPTag.keepPlayerHealthZomb = configuration.getConfig().getBoolean("PvPLogger Zombie.Keep Player Health", true);
      this.deathChestEnabled = configuration.getConfig().getBoolean("Death.DeathChest Enabled", true);
      this.safeTimeObjective = configuration.getConfig().getBoolean("Scoreboard Display.Safe Time", true);
      this.healthObjective = configuration.getConfig().getBoolean("Scoreboard Display.Health", true);
      this.factionsEnabled = configuration.getConfig().getBoolean("Factions Enables", false);
   }

   private void resetNameTagsAuto() {
      Iterator<String> iter = safeTimes.keySet().iterator();
      final Objective displaySafeTime = scoreboard.getBoard().getObjective("displaySafeTime");
      while(iter.hasNext()) {
         String s = iter.next();
         Player player = getServer().getPlayer(s);
         if(player == null) {
            OfflinePlayer p = getServer().getOfflinePlayer(s);
            clearFromBoard(p);
            iter.remove();
         } else if(isSafe(s)) {
            iter.remove();
            player.sendMessage("§cYou are now safe.");
            clearFromBoard(player);
            fixFlying(player);
            refresh(player);
         } else {
            if(safeTimeObjective) {
               long currTime = System.currentTimeMillis();
               long safeTime = safeTimes.get(s);
               displaySafeTime.getScore(player).setScore((int)(safeTime / 1000 - currTime / 1000));
            }
         }
      }
   }

   void clearFromBoard(OfflinePlayer player) {
      if(safeTimeObjective || healthObjective) {
         if(player instanceof Player)
            ((Player)player).setScoreboard(getServer().getScoreboardManager().getNewScoreboard());
         scoreboard.getBoard().resetScores(player);
      }
   }

   private void fixFlying(Player player) {
      if(couldFly.contains(player.getName())) {
         couldFly.remove(player.getName());
         player.setAllowFlight(true);
      }
      if(hadFlight.contains(player.getName())) {
         hadFlight.remove(player.getName());
         player.setFlying(true);
      }
   }

   void callSafeAllManual() {
      Iterator<String> iter = safeTimes.keySet().iterator();
      while(iter.hasNext()) {
         String s = iter.next();
         iter.remove();
         callSafe(getServer().getPlayer(s));
      }
   }

   public void onDisable() {
      callSafeAllManual();
   //   dealWithZombies();
      dcl.breakAll();
      instance = null;
   }

   private void dealWithZombies() {
      for(PvPLoggerZombie pz: PvPLoggerZombie.zombies){
         pz.despawn();

      }
   }

   public void task() {
      this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
         public void run() {
            resetNameTagsAuto();
         }
      }, 40L, 40L);
   }

   void addUnsafe(Player p) {
      PvPTag.debug("Setting player unsafe: " + p.getName());
      addToBoard(p);
      resetSafeTime(p);
      p.sendMessage("§cYou can now be hit anywhere for at least " + (SAFE_DELAY / 1000) + " seconds!");
      removeFlight(p);
      refresh(p);
      unInvis(p);
      configuration.performConsoleUnsafeCommands(p);
      configuration.performUnsafeCommands(p);
   }

   private void addToBoard(Player p) {
      if(healthObjective || safeTimeObjective)
         p.setScoreboard(scoreboard.getBoard());
   }

   void resetSafeTime(Player p) {
      safeTimes.put(p.getName(), calcSafeTime(SAFE_DELAY));
   }

   private void unInvis(Player p) {
      if(unInvis) p.removePotionEffect(PotionEffectType.INVISIBILITY);
   }

   private void removeFlight(Player p) {
      if(disableFlight && p.getGameMode() != GameMode.CREATIVE) {
         if(p.getAllowFlight()) couldFly.add(p.getName());
         if(p.isFlying()) hadFlight.add(p.getName());
         p.setFlying(false);
         p.setAllowFlight(false);
      }
   }

   void callSafe(Player player) {
      if(player != null) {
         clearFromBoard(player);
         safeTimes.remove(player.getName());
         refresh(player);
         player.sendMessage("§cYou are now safe.");
         configuration.performConsoleSafeCommands(player);
         configuration.performSafeCommands(player);
      }
   }

   public boolean isSafe(String player) {
      if(safeTimes.containsKey(player)) {
         return (safeTimes.get(player) < System.currentTimeMillis());
      }
      return true;
   }

   public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
      return mainCommandListener.onCommand(sender, cmd, commandLabel, args);
   }

   long calcSafeTime(Long time) {
      return System.currentTimeMillis() + time;
   }

   void refresh(Player p) {
      tagApi.refresh(p);
   }

   public void setNameTagColor(ChatColor nameTagColor) {
      this.nameTagColor = nameTagColor;
   }

   public ChatColor getNameTagColor() {
      return nameTagColor;
   }

   public static void log(Level level, String message) {
      logger.log(level, message);
   }

   public static PvPTag getInstance() {
      return instance;
   }

   public static void debug(String message) {
      if(! DEBUG) return;
      Player cman = Bukkit.getServer().getPlayer("cman85_con");
      if(cman != null) cman.sendMessage("§4PvPTag: " + message);
   }
}