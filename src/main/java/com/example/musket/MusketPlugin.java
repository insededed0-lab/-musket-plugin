package com.example.musket;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MusketPlugin extends JavaPlugin implements Listener {

    private NamespacedKey musketKey;
    private NamespacedKey powderKey;
    private NamespacedKey bulletKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    @Override
    public void onEnable() {
        musketKey = new NamespacedKey(this, "is_musket");
        powderKey = new NamespacedKey(this, "has_powder");
        bulletKey = new NamespacedKey(this, "has_bullet");

        getServer().getPluginManager().registerEvents(this, this);

        // Таймер перезарядки
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Long end = cooldowns.get(p.getUniqueId());
                    if (end != null) {
                        if (now >= end) {
                            cooldowns.remove(p.getUniqueId());
                            p.sendActionBar(Component.text("✔ Готов", NamedTextColor.GREEN));
                        } else {
                            double sec = (end - now) / 1000.0;
                            p.sendActionBar(Component.text("⟳ " + String.format("%.1f", sec) + "с", NamedTextColor.YELLOW));
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Только игрок.");
            return true;
        }

        ItemStack musket = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) musket.getItemMeta();
        meta.displayName(Component.text("Кремнёвый мушкет", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(musketKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(powderKey, PersistentDataType.BYTE, (byte) 0);
        meta.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 0);
        musket.setItemMeta(meta);

        player.getInventory().addItem(musket);
        return true;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        
        if (mainHand.getType() != Material.CROSSBOW) return;

        ItemMeta meta = mainHand.getItemMeta();
        if (meta == null) return;
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(musketKey, PersistentDataType.BYTE)) return;

        byte hasPowder = pdc.getOrDefault(powderKey, PersistentDataType.BYTE, (byte) 0);
        byte hasBullet = pdc.getOrDefault(bulletKey, PersistentDataType.BYTE, (byte) 0);

        ItemStack offHand = player.getInventory().getItemInOffHand();

        // 1. Зарядка пороха
        if (hasPowder == 0 && offHand != null && offHand.getType() == Material.GUNPOWDER) {
            pdc.set(powderKey, PersistentDataType.BYTE, (byte) 1);
            mainHand.setItemMeta(meta);
            
            if (offHand.getAmount() > 1) {
                offHand.setAmount(offHand.getAmount() - 1);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            
            player.sendActionBar(Component.text("✔ Порох", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            return;
        }

        // 2. Зарядка пули
        if (hasPowder == 1 && hasBullet == 0 && offHand != null && offHand.getType() == Material.IRON_NUGGET) {
            pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 1);
            mainHand.setItemMeta(meta);
            
            if (offHand.getAmount() > 1) {
                offHand.setAmount(offHand.getAmount() - 1);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
            
            player.sendActionBar(Component.text("✔ Пуля", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return;
        }

        // 3. Выстрел
        if (hasPowder == 1 && hasBullet == 1) {
            Long cooldownEnd = cooldowns.get(player.getUniqueId());
            if (cooldownEnd != null && System.currentTimeMillis() < cooldownEnd) {
                return; // Ещё перезаряжается
            }

            event.setCancelled(true);
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);

            // Сброс зарядки
            pdc.set(powderKey, PersistentDataType.BYTE, (byte) 0);
            pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 0);
            mainHand.setItemMeta(meta);

            Location eye = player.getEyeLocation();
            World world = player.getWorld();
            Vector dir = eye.getDirection().clone();
            dir.setY(dir.getY() + 0.08);

            // Визуальная пуля
            Item bullet = world.dropItem(eye, new ItemStack(Material.IRON_NUGGET));
            bullet.setVelocity(dir.multiply(2.5));
            bullet.setGravity(true);
            bullet.setPickupDelay(Integer.MAX_VALUE);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (bullet.isValid()) bullet.remove();
                }
            }.runTaskLater(this, 80L);

            // Проверка попадания (ray trace)
            org.bukkit.util.RayTraceResult ray = world.rayTraceEntities(eye, dir, 80.0, 0.3);
            
            if (ray != null && ray.getHitEntity() instanceof LivingEntity target) {
                target.damage(14.0, player);
                world.spawnParticle(Particle.DAMAGE_INDICATOR, ray.getHitPosition().toLocation(world), 5, 0.2, 0.3, 0.2, 0.01);
            }

            // Эффекты выстрела
            Location fx = eye.clone().add(dir.multiply(1.5));
            world.spawnParticle(Particle.LARGE_SMOKE, fx, 2, 0.05, 0.05, 0.05, 0.005);
            world.spawnParticle(Particle.FLAME, fx, 2, 0.05, 0.05, 0.05, 0.005);
            world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
            world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.1f);
        }
    }
}
