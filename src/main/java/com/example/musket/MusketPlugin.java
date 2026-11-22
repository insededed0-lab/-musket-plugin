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
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MusketPlugin extends JavaPlugin implements Listener {

    private NamespacedKey musketKey;
    private NamespacedKey powderKey;
    private NamespacedKey bulletKey;
    private final Map<UUID, Long> reloadMap = new HashMap<>();
    private static final long RELOAD_MS = 3000L;

    @Override
    public void onEnable() {
        musketKey = new NamespacedKey(this, "is_musket");
        powderKey = new NamespacedKey(this, "musket_powder");
        bulletKey = new NamespacedKey(this, "musket_bullet");

        getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    Long end = reloadMap.get(p.getUniqueId());
                    if (end != null) {
                        long left = end - now;
                        if (left <= 0) {
                            reloadMap.remove(p.getUniqueId());
                            p.sendActionBar(Component.text("✔ Готов к выстрелу", NamedTextColor.GREEN));
                        } else {
                            double seconds = left / 1000.0;
                            p.sendActionBar(Component.text(
                                    "⟳ Перезарядка: " + String.format("%.1f", seconds) + "с",
                                    NamedTextColor.YELLOW));
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
        if (!player.hasPermission("musket.give")) {
            player.sendMessage(Component.text("Нет прав.", NamedTextColor.RED));
            return true;
        }

        ItemStack musket = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) musket.getItemMeta();
        meta.displayName(Component.text("Кремнёвый мушкет", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(musketKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(powderKey, PersistentDataType.BYTE, (byte) 0);
        meta.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 0);
        musket.setItemMeta(meta);

        player.getInventory().addItem(musket);
        player.sendMessage(Component.text("🔫 Ты получил Кремнёвый мушкет!", NamedTextColor.GRAY));
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.CROSSBOW) return;

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(musketKey, PersistentDataType.BYTE)) return;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        byte powderLoaded = pdc.getOrDefault(powderKey, PersistentDataType.BYTE, (byte) 0);
        byte bulletLoaded = pdc.getOrDefault(bulletKey, PersistentDataType.BYTE, (byte) 0);

        // 1. ЗАРЯДКА ПОРОХА
        if (offHand != null && offHand.getType() == Material.GUNPOWDER && powderLoaded == 0) {
            pdc.set(powderKey, PersistentDataType.BYTE, (byte) 1);
            hand.setItemMeta(meta);
            if (offHand.getAmount() > 1) offHand.setAmount(offHand.getAmount() - 1);
            else player.getInventory().setItemInOffHand(null);
            
            player.sendActionBar(Component.text("✔ Порох заряжен", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            event.setCancelled(true);
            return;
        }

        // 2. ЗАРЯДКА ПУЛИ
        if (offHand != null && offHand.getType() == Material.IRON_NUGGET && powderLoaded == 1 && bulletLoaded == 0) {
            pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 1);
            hand.setItemMeta(meta);
            if (offHand.getAmount() > 1) offHand.setAmount(offHand.getAmount() - 1);
            else player.getInventory().setItemInOffHand(null);
            
            player.sendActionBar(Component.text("✔ Пуля заряжена", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            event.setCancelled(true);
            return; 
        }

        // 3. ПРЕДУПРЕЖДЕНИЯ
        if (powderLoaded == 0) {
            player.sendActionBar(Component.text("⚠ Нужен порох", NamedTextColor.YELLOW));
            event.setCancelled(true);
            return;
        }
        if (powderLoaded == 1 && bulletLoaded == 0) {
            player.sendActionBar(Component.text("⚠ Нужна пуля (железный самородок)", NamedTextColor.YELLOW));
            event.setCancelled(true);
            return;
        }

        // 4. ВЫСТРЕЛ
        Long reloadEnd = reloadMap.get(player.getUniqueId());
        if (reloadEnd != null && System.currentTimeMillis() < reloadEnd) {
            player.sendActionBar(Component.text("⏳ Перезарядка...", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        reloadMap.put(player.getUniqueId(), System.currentTimeMillis() + RELOAD_MS);

        // Разряжаем мушкет
        pdc.set(powderKey, PersistentDataType.BYTE, (byte) 0);
        pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 0);
        hand.setItemMeta(meta);

        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        Vector direction = eye.getDirection().clone();

        // === Мгновенный урон по лучу (RayTrace) ===
        LivingEntity hitTarget = null;
        Location hitLocation = null;
        RayTraceResult ray = world.rayTraceEntities(eye, direction, 80.0, 0.5);
        if (ray != null && ray.getHitEntity() instanceof LivingEntity living) {
            hitTarget = living;
            hitLocation = ray.getHitPosition().toLocation(world);
        }

        // === ВИЗУАЛЬНАЯ ПУЛЯ (Исправлено от отскока) ===
        // Сначала спавним её прямо в глазах, но мгновенно телепортируем на 2 блока вперёд
        Location spawnLoc = eye.clone().add(direction.clone().multiply(2.0));
        
        // Временный 0-вектор чтобы предмет не отскочил
        Item bullet = world.dropItem(eye, new ItemStack(Material.IRON_NUGGET)); 
        bullet.teleport(spawnLoc); // Телепорт на 2 блока вперёд
        
        Vector vel = direction.clone();
        vel.setY(vel.getY() + 0.08); 
        bullet.setVelocity(vel.multiply(3.0));
        bullet.setGravity(true);
        bullet.setPickupDelay(Integer.MAX_VALUE);

        // Удаляем через 2.5 секунды
        new BukkitRunnable() {
            @Override
            public void run() {
                if (bullet.isValid()) bullet.remove();
            }
        }.runTaskLater(this, 50L);

        // === Эффекты ===
        Location fx = eye.clone().add(direction.multiply(1.2));
        world.spawnParticle(Particle.LARGE_SMOKE, fx, 3, 0.05, 0.05, 0.05, 0.005);
        world.spawnParticle(Particle.FLAME, fx, 3, 0.05, 0.05, 0.05, 0.005);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
        world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.1f);

        // === Нанесение урона ===
        if (hitTarget != null && hitLocation != null) {
            hitTarget.damage(14.0, player);
            
            // Стандартные эффекты попадания (без сердец)
            world.playSound(hitLocation, Sound.ENTITY_PLAYER_HURT, 1.5f, 0.7f);
            world.spawnParticle(Particle.EXPLOSION, hitLocation, 1, 0, 0, 0, 0); // Маленький взрыв на месте попадания
            player.sendActionBar(Component.text("💥 Попадание! 14 урона", NamedTextColor.RED));
        } else {
            player.sendActionBar(Component.text("💥 Промах", NamedTextColor.GRAY));
        }
    }
}
