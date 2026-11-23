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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
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
    private NamespacedKey recipeKey;
    private final Map<UUID, Long> reloadMap = new HashMap<>();
    private static final long RELOAD_MS = 3000L;

    @Override
    public void onEnable() {
        musketKey = new NamespacedKey(this, "is_musket");
        powderKey = new NamespacedKey(this, "musket_powder");
        bulletKey = new NamespacedKey(this, "musket_bullet");

        getServer().getPluginManager().registerEvents(this, this);

        registerMusketRecipe();

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
    public void onDisable() {
        if (recipeKey != null) Bukkit.removeRecipe(recipeKey);
    }

    // ================== СОЗДАНИЕ МУШКЕТА ==================

    private ItemStack createMusket() {
        ItemStack musket = new ItemStack(Material.CROSSBOW);
        CrossbowMeta meta = (CrossbowMeta) musket.getItemMeta();
        meta.displayName(Component.text("Кремнёвый мушкет", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(musketKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(powderKey, PersistentDataType.BYTE, (byte) 0);
        meta.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 0);
        musket.setItemMeta(meta);
        return musket;
    }

    // ================== КРАФТ ==================

    private void registerMusketRecipe() {
        recipeKey = new NamespacedKey(this, "musket");
        Bukkit.removeRecipe(recipeKey);

        ShapedRecipe recipe = new ShapedRecipe(recipeKey, createMusket());
        
        // Крафт по вашему скриншоту
        recipe.shape(
                " ID", // Пусто, Железный слиток, Алмаз
                "SBB", // Палка, Железный блок, Железный блок
                "SS "  // Палка, Палка, Пусто
        );
        
        recipe.setIngredient('I', Material.IRON_INGOT);
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('S', Material.STICK);
        recipe.setIngredient('B', Material.IRON_BLOCK);

        Bukkit.addRecipe(recipe);
    }

    // Страховка: принудительно подсовываем правильный результат
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (!(recipe instanceof Keyed keyed)) return;
        if (recipeKey == null || !recipeKey.equals(keyed.getKey())) return;

        event.getInventory().setResult(createMusket());
    }

    // ================== КОМАНДА /musket ==================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("musket.give")) return true;

        player.getInventory().addItem(createMusket());
        return true;
    }

    // ================== ВЗАИМОДЕЙСТВИЕ ==================

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

        // Зарядка пороха
        if (offHand != null && offHand.getType() == Material.GUNPOWDER && powderLoaded == 0) {
            pdc.set(powderKey, PersistentDataType.BYTE, (byte) 1);
            hand.setItemMeta(meta);
            if (offHand.getAmount() > 1) offHand.setAmount(offHand.getAmount() - 1);
            else player.getInventory().setItemInOffHand(null);
            player.sendActionBar(Component.text("✔ Порох заряжен", NamedTextColor.GREEN));
            event.setCancelled(true);
            return;
        }

        // Зарядка пули
        if (offHand != null && offHand.getType() == Material.IRON_NUGGET && powderLoaded == 1 && bulletLoaded == 0) {
            pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 1);
            hand.setItemMeta(meta);
            if (offHand.getAmount() > 1) offHand.setAmount(offHand.getAmount() - 1);
            else player.getInventory().setItemInOffHand(null);
            player.sendActionBar(Component.text("✔ Пуля заряжена", NamedTextColor.GREEN));
            event.setCancelled(true);
            return;
        }

        if (powderLoaded == 0 || bulletLoaded == 0) {
            player.sendActionBar(Component.text("⚠ Не заряжено", NamedTextColor.YELLOW));
            event.setCancelled(true);
            return;
        }

        // Выстрел
        Long reloadEnd = reloadMap.get(player.getUniqueId());
        if (reloadEnd != null && System.currentTimeMillis() < reloadEnd) {
            player.sendActionBar(Component.text("⏳ Перезарядка...", NamedTextColor.RED));
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        reloadMap.put(player.getUniqueId(), System.currentTimeMillis() + RELOAD_MS);

        pdc.set(powderKey, PersistentDataType.BYTE, (byte) 0);
        pdc.set(bulletKey, PersistentDataType.BYTE, (byte) 0);
        hand.setItemMeta(meta);

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().clone();

        // Невидимый снежок (настоящий урон)
        Snowball bullet = player.launchProjectile(Snowball.class);
        bullet.setVelocity(direction.multiply(4.0));
        bullet.setGravity(true);
        bullet.getPersistentDataContainer().set(bulletKey, PersistentDataType.BYTE, (byte) 1);

        // Визуальный самородок
        ArmorStand visualBullet = player.getWorld().spawn(eye.clone().add(direction.clone().multiply(1.5)), ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(true);
            stand.setInvulnerable(true);
            stand.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_NUGGET));
        });
        visualBullet.setVelocity(direction.multiply(4.0));

        // Эффекты
        Location fx = eye.clone().add(direction.clone().multiply(1.5));
        player.getWorld().spawnParticle(Particle.SMOKE, fx, 5, 0.1, 0.1, 0.1, 0.01);
        player.getWorld().spawnParticle(Particle.FLAME, fx, 3, 0.1, 0.1, 0.1, 0.01);
        player.getWorld().playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (visualBullet.isValid()) visualBullet.remove();
            }
        }.runTaskLater(this, 40L);
    }

    // ================== УРОН ==================

    @EventHandler
    public void onSnowballHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Snowball snowball)) return;
        if (!snowball.getPersistentDataContainer().has(bulletKey, PersistentDataType.BYTE)) return;

        if (event.getEntity() instanceof LivingEntity living) {
            event.setDamage(14.0);

            living.getWorld().playSound(living.getLocation(), Sound.ENTITY_PLAYER_HURT, 1.5f, 0.7f);
            living.getWorld().spawnParticle(Particle.CRIT, living.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.1);

            snowball.remove();
        }
    }
}
