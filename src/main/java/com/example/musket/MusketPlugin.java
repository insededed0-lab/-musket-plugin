package com.example.musket;

import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    private NamespacedKey bulletKey;
    private NamespacedKey powderKey; // ключ для пороха
    private final Map<UUID, Long> reloadMap = new HashMap<>();
    private static final long RELOAD_MS = 3000L;

    @Override
    public void onEnable() {
        musketKey = new NamespacedKey(this, "is_musket");
        bulletKey = new NamespacedKey(this, "musket_bullet");
        powderKey = new NamespacedKey(this, "musket_powder");

        getServer().getPluginManager().registerEvents(this, this);

        // Задача обновления ActionBar во время перезарядки
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
                            p.sendActionBar(Component.text("✔ Мушкет перезаряжен!", NamedTextColor.GREEN));
                        } else {
                            double seconds = left / 1000.0;
                            p.sendActionBar(Component.text(
                                    "⟳ Перезарядка: " + String.format("%.1f", seconds) + " сек",
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
            sender.sendMessage("Команду может использовать только игрок.");
            return true;
        }
        if (!player.hasPermission("musket.give")) {
            player.sendMessage(Component.text("У тебя нет прав.", NamedTextColor.RED));
            return true;
        }

        ItemStack musket = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) musket.getItemMeta();
        meta.displayName(Component.text("Кремнёвый мушкет", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(musketKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(powderKey, PersistentDataType.BYTE, (byte) 0); // порох не заряжен
        meta.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 0); // пуля не заряжена
        musket.setItemMeta(meta);

        player.getInventory().addItem(musket);
        player.sendMessage(Component.text("🔫 Ты получил Мушкет! Заряди порох и пулю.", NamedTextColor.GOLD));
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

        // Проверяем что в руке
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean hasGunpowder = offHand != null && offHand.getType() == Material.GUNPOWDER;
        boolean hasIronNugget = offHand != null && offHand.getType() == Material.IRON_NUGGET;

        // Проверяем состояние зарядки
        byte powderLoaded = pdc.getOrDefault(powderKey, PersistentDataType.BYTE, (byte) 0);
        byte bulletLoaded = pdc.getOrDefault(bulletKey, PersistentDataType.BYTE, (byte) 0);

        // === ЗАРЯДКА ПОРОХА ===
        if (hasGunpowder && powderLoaded == 0) {
            pdc.set(powderKey, PersistentDataType.BYTE, (byte) 1);
            hand.setItemMeta(meta);
            player.sendMessage(Component.text("✔ Порох заряжен! Теперь заряди пулю.", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            return;
        }

        // === ЗАРЯДКА ПУЛИ ===
        if (hasIronNugget && powderLoaded == 1 && bulletLoaded == 0) {
            pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 1);
            hand.setItemMeta(meta);
            player.sendMessage(Component.text("✔ Пуля заряжена! Мушкет готов к выстрелу.", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
            return;
        }

        // === ВЫСТРЕЛ ===
        if (powderLoaded == 1 && bulletLoaded == 1) {
            // Проверка перезарядки
            Long reloadEnd = reloadMap.get(player.getUniqueId());
            if (reloadEnd != null && System.currentTimeMillis() < reloadEnd) {
                player.sendMessage(Component.text("⏳ Мушкет ещё перезаряжается!", NamedTextColor.RED));
                return;
            }

            event.setCancelled(true);
            reloadMap.put(player.getUniqueId(), System.currentTimeMillis() + RELOAD_MS);

            // Сбрасываем зарядку
            pdc.set(powderKey, PersistentDataType.BYTE, (byte) 0);
            pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 0);
            hand.setItemMeta(meta);

            Location eye = player.getEyeLocation();
            World world = player.getWorld();

            // === Железный самородок с БАЛЛИСТИКОЙ ===
            ItemStack bulletItem = new ItemStack(Material.IRON_NUGGET);
            Item bullet = world.dropItem(eye, bulletItem);
            
            Vector direction = eye.getDirection().clone();
            direction.setY(direction.getY() + 0.08);
            
            bullet.setVelocity(direction.multiply(2.5));
            bullet.setGravity(true);
            bullet.setPickupDelay(Integer.MAX_VALUE);
            bullet.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 1);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (bullet.isValid()) bullet.remove();
                }
            }.runTaskLater(this, 80L);

            // === Эффекты выстрела ===
            Location effectLoc = eye.clone().add(eye.getDirection().multiply(0.5));
            world.spawnParticle(Particle.LARGE_SMOKE, effectLoc, 5, 0.1, 0.1, 0.1, 0.01);
            world.spawnParticle(Particle.FLAME, effectLoc, 4, 0.08, 0.08, 0.08, 0.005);
            world.spawnParticle(Particle.EXPLOSION, effectLoc, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.SMOKE, effectLoc, 5, 0.08, 0.08, 0.08, 0.005);
            world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
            world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.8f, 1.0f);

            player.sendMessage(Component.text("⚠ Мушкет разряжен! Заряди порох и пулю.", NamedTextColor.YELLOW));
        } else if (powderLoaded == 0) {
            player.sendMessage(Component.text("⚠ Сначала заряди порох (держите порох в левой руке).", NamedTextColor.YELLOW));
        } else if (bulletLoaded == 0) {
            player.sendMessage(Component.text("⚠ Порох заряжен. Теперь заряди пулю (держите самородок в левой руке).", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onBulletDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Item bullet)) return;
        if (!bullet.getPersistentDataContainer().has(bulletKey, PersistentDataType.BYTE)) return;

        if (event.getEntity() instanceof LivingEntity living) {
            event.setDamage(14.0);
            
            World world = living.getWorld();
            world.spawnParticle(Particle.DAMAGE_INDICATOR, living.getEyeLocation(), 15, 0.3, 0.5, 0.3, 0.01);
            world.spawnParticle(Particle.EXPLOSION, living.getLocation(), 1, 0, 0, 0, 0);
            world.playSound(living.getLocation(), Sound.ENTITY_PLAYER_HURT, 2.0f, 0.6f);
            
            bullet.remove();
        }
    }
}
