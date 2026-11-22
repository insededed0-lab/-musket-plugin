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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class MusketPlugin extends JavaPlugin implements Listener {

    private NamespacedKey musketKey;

    @Override
    public void onEnable() {
        musketKey = new NamespacedKey(this, "is_musket");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        ItemStack musket = new ItemStack(Material.CROSSBOW);
        var meta = musket.getItemMeta();
        meta.displayName(Component.text("DEBUG Мушкет", NamedTextColor.GRAY));
        meta.getPersistentDataContainer().set(musketKey, PersistentDataType.BYTE, (byte) 1);
        musket.setItemMeta(meta);
        player.getInventory().addItem(musket);
        return true;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() != Material.CROSSBOW) return;
        var meta = hand.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(musketKey, PersistentDataType.BYTE)) return;

        event.setCancelled(true);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection();
        World world = player.getWorld();

        // Луч 80 блоков
        RayTraceResult ray = world.rayTraceEntities(eye, dir, 80.0, 0.5);

        player.sendMessage(Component.text("=== DEBUG ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("Глаза: " + eye.toVector(), NamedTextColor.GRAY));
        player.sendMessage(Component.text("Направление: " + dir, NamedTextColor.GRAY));

        if (ray == null) {
            player.sendMessage(Component.text("❌ Ray = NULL (никого не видит)", NamedTextColor.RED));
        } else {
            player.sendMessage(Component.text("✅ Ray hit: " + ray.getHitEntity(), NamedTextColor.GREEN));
            player.sendMessage(Component.text("Hit position: " + ray.getHitPosition(), NamedTextColor.GREEN));
            if (ray.getHitEntity() instanceof LivingEntity living) {
                player.sendMessage(Component.text("Живое: " + living.getType() + " HP=" + living.getHealth(), NamedTextColor.YELLOW));
                living.damage(14.0, player);
                player.sendMessage(Component.text("Урон нанесён!", NamedTextColor.RED));
            } else {
                player.sendMessage(Component.text("Не LivingEntity: " + (ray.getHitEntity() == null ? "null" : ray.getHitEntity().getType()), NamedTextColor.YELLOW));
            }
        }
    }
}
