package com.example.musket;

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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
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
    private final Map<UUID, Long> reloadMap = new HashMap<>();
    private static final long RELOAD_MS = 3000L;

    @Override
    public void onEnable() {
        musketKey = new NamespacedKey(this, "is_musket");
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
        meta.displayName(Component.text("Мушкет", NamedTextColor.GOLD));
        meta.getPersistentDataContainer().set(musketKey, PersistentDataType.BYTE, (byte) 1);
        musket.setItemMeta(meta);

        player.getInventory().addItem(musket);
        player.sendMessage(Component.text("🔫 Ты получил Мушкет!", NamedTextColor.GOLD));
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

    Long reloadEnd = reloadMap.get(player.getUniqueId());
    if (reloadEnd != null && System.currentTimeMillis() < reloadEnd) {
        player.sendMessage(Component.text("⏳ Мушкет ещё перезаряжается!", NamedTextColor.RED));
        return;
    }

    event.setCancelled(true);
    reloadMap.put(player.getUniqueId(), System.currentTimeMillis() + RELOAD_MS);

    Location eye = player.getEyeLocation();
    World world = player.getWorld();

    // === RAY TRACING — мгновенная проверка попадания ===
    double maxDistance = 80.0; // дальность мушкета
    org.bukkit.util.RayTraceResult ray = world.rayTraceEntities(
            eye,
            eye.getDirection(),
            maxDistance,
            0.3 // радиус проверки (чуть больше хитбокса)
    );

    LivingEntity hitTarget = null;
    Location hitLocation = eye.clone().add(eye.getDirection().multiply(maxDistance));

    if (ray != null && ray.getHitEntity() instanceof LivingEntity living) {
        hitTarget = living;
        hitLocation = ray.getHitPosition().toLocation(world);
    }

    // === Визуальный снежок (летит для красоты) ===
    Snowball bullet = player.launchProjectile(Snowball.class);
    bullet.setVelocity(eye.getDirection().multiply(6)); // высокая скорость для визуала
    bullet.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 1);
    bullet.setCustomName("Мушкетная пуля");
    bullet.setGravity(false); // убираем гравитацию — летит прямо

    // Удаляем снежок через 1.5 секунды (чтобы не засорял мир)
    new BukkitRunnable() {
        @Override
        public void run() {
            if (bullet.isValid()) bullet.remove();
        }
    }.runTaskLater(this, 30L);

    // === Эффекты выстрела ===
    world.spawnParticle(Particle.LARGE_SMOKE, eye, 25, 0.3, 0.3, 0.3, 0.02);
    world.spawnParticle(Particle.EXPLOSION, eye, 1, 0, 0, 0, 0);
    world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.8f);
    world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 3.0f, 1.2f);

    // === Наносим урон через ray trace (надёжно!) ===
    if (hitTarget != null) {
        hitTarget.damage(14.0, player);

        // Эффекты попадания
        world.spawnParticle(Particle.DAMAGE_INDICATOR, hitLocation, 15, 0.3, 0.5, 0.3, 0.01);
        world.spawnParticle(Particle.EXPLOSION, hitLocation, 1, 0, 0, 0, 0);
        world.playSound(hitLocation, Sound.ENTITY_PLAYER_HURT, 2.0f, 0.6f);
    }
}
        event.setCancelled(true);

        reloadMap.put(player.getUniqueId(), System.currentTimeMillis() + RELOAD_MS);

        Location eye = player.getEyeLocation();
        Snowball bullet = player.launchProjectile(Snowball.class);
        bullet.setVelocity(eye.getDirection().multiply(6));
        bullet.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 1);
        bullet.setCustomName("Мушкетная пуля");

        World world = player.getWorld();
        world.spawnParticle(Particle.LARGE_SMOKE, eye, 25, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticle(Particle.EXPLOSION, eye, 1, 0, 0, 0, 0);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.8f);
        world.playSound(eye, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 3.0f, 1.2f);
    }
    
}
