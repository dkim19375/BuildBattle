

package pl.plajer.buildbattle.handlers.reward;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import pl.plajer.buildbattle.ConfigPreferences;
import pl.plajer.buildbattle.Main;
import pl.plajer.buildbattle.arena.ArenaRegistry;
import pl.plajer.buildbattle.arena.impl.BaseArena;
import pl.plajer.buildbattle.utils.Debugger;
import pl.plajerlair.commonsbox.minecraft.configuration.ConfigUtils;
import pl.plajerlair.commonsbox.minecraft.engine.ScriptEngine;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Tom on 30/01/2016.
 */
public class RewardsFactory {

  private Set<Reward> rewards = new HashSet<>();
  private FileConfiguration config;
  private boolean enabled;

  public RewardsFactory(Main plugin) {
    enabled = plugin.getConfigPreferences().getOption(ConfigPreferences.Option.REWARDS);
    config = ConfigUtils.getConfig(plugin, "rewards");
    registerRewards();
  }

  public void performReward(BaseArena arena, Reward.RewardType type) {
    if (!enabled) {
      return;
    }
    for (Player p : arena.getPlayers()) {
      performReward(p, type, -1);
    }
  }

  public void performReward(Player player, Reward.RewardType type, int place) {
    if (!enabled) {
      return;
    }
    if (!config.contains("rewards")) {
      Debugger.debug(Debugger.Level.WARN, "[RewardsFactory] Rewards section not found in the file. Rewards won't be loaded.");
      return;
    }
    BaseArena arena = ArenaRegistry.getArena(player);
    ScriptEngine engine = new ScriptEngine();
    engine.setValue("player", player);
    engine.setValue("server", Bukkit.getServer());
    engine.setValue("arena", arena);
    for (Reward reward : rewards) {
      if (reward.getType() == type) {
        if (reward.getPlace() != -1){
          if (reward.getPlace() != place){
            continue;
          }
        }
        //cannot execute if chance wasn't met
        if (reward.getChance() != -1 && ThreadLocalRandom.current().nextInt(0, 100) > reward.getChance()) {
          continue;
        }
        String command = reward.getExecutableCode();
        command = StringUtils.replace(command, "%PLAYER%", player.getName());
        command = formatCommandPlaceholders(command, arena, place);
        switch (reward.getExecutor()) {
          case CONSOLE:
            Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
            break;
          case PLAYER:
            player.performCommand(command);
            break;
          case SCRIPT:
            engine.execute(command);
            break;
          default:
            break;
        }
      }
    }
  }

  private String formatCommandPlaceholders(String command, BaseArena arena, int place) {
    String formatted = command;
    formatted = StringUtils.replace(formatted, "%ARENA-ID%", arena.getID());
    formatted = StringUtils.replace(formatted, "%MAPNAME%", arena.getMapName());
    formatted = StringUtils.replace(formatted, "%PLACE%", "" + place);
    formatted = StringUtils.replace(formatted, "%PLAYERAMOUNT%", String.valueOf(arena.getPlayers().size()));
    return formatted;
  }

  private void registerRewards() {
    if (!enabled) {
      return;
    }
    Debugger.debug(Debugger.Level.INFO, "[RewardsFactory] Starting rewards registration");
    long start = System.currentTimeMillis();

    Map<Reward.RewardType, Integer> registeredRewards = new EnumMap<>(Reward.RewardType.class);
    for (Reward.RewardType rewardType : Reward.RewardType.values()) {
      if (rewardType == Reward.RewardType.PLACE) {
        ConfigurationSection section = config.getConfigurationSection("rewards." + rewardType.getPath());
        if (section == null) {
          Debugger.debug(Debugger.Level.WARN, "Rewards section "+ rewardType.getPath() +" is missing! Was it manually removed?");
          continue;
        }
        for (String key : section.getKeys(false)) {
          for (String reward : config.getStringList("rewards." + rewardType.getPath() + "." + key)) {
            rewards.add(new Reward(rewardType, reward, Integer.parseInt(key)));
            registeredRewards.put(rewardType, registeredRewards.getOrDefault(rewardType, 0) + 1);
          }
        }
        continue;
      }
      for (String reward : config.getStringList("rewards." + rewardType.getPath())) {
        rewards.add(new Reward(rewardType, reward));
        registeredRewards.put(rewardType, registeredRewards.getOrDefault(rewardType, 0) + 1);
      }
    }
    for (Map.Entry<Reward.RewardType, Integer> entry : registeredRewards.entrySet()) {
      Debugger.debug(Debugger.Level.INFO, "[RewardsFactory] Registered "+entry.getValue()+" "+entry.getKey().name()+" rewards!");
    }
    Debugger.debug(Debugger.Level.INFO, "[RewardsFactory] Registered all rewards took " + (System.currentTimeMillis() - start) +"ms");
  }

}
