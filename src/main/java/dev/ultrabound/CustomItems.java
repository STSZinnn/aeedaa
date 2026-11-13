package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** PDC-based item identity; display names are never authentication. */
public final class CustomItems {
    public static final String RESET_CLASS="class_reset_token",RESET_TREE="skill_tree_reset_token",
       INFIN_TNT="infinite_tnt",GUARDIAN_SHIELD="guardian_shield",INFUSIUM="infusium";
    private final UltraboundPlugin plugin;
    private final NamespacedKey idKey,ownerKey,versionKey;
    CustomItems(UltraboundPlugin p){plugin=p;idKey=new NamespacedKey(p,"item_id");ownerKey=new NamespacedKey(p,"owner_uuid");versionKey=new NamespacedKey(p,"item_version");}
    public String identity(ItemStack item){
        if(item==null || !item.hasItemMeta())return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey,PersistentDataType.STRING);
    }
    public UUID owner(ItemStack item){
        if(item==null || !item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(ownerKey,PersistentDataType.STRING);
        try{return UUID.fromString(id);}catch(Exception e){return null;}
    }
    public boolean is(ItemStack item,String name){return name.equals(identity(item));}
    public ItemStack make(String id,UUID owner){
        Material mat=switch(id){
            case RESET_CLASS->Material.NETHER_STAR;
            case RESET_TREE->Material.ECHO_SHARD;
            case INFIN_TNT->Material.TNT;
            case GUARDIAN_SHIELD->Material.SHIELD;
            case INFUSIUM->Material.AMETHYST_SHARD;
            case "wrench_stone","wrench_iron","wrench_diamond","wrench_netherite"->Material.TRIPWIRE_HOOK;
            default->Material.PAPER;
        };
        ItemStack item=new ItemStack(mat);
        ItemMeta m=item.getItemMeta();
        m.setDisplayName(switch(id){
            case RESET_CLASS->ChatColor.LIGHT_PURPLE+"Skillset Reset Token";
            case RESET_TREE->ChatColor.AQUA+"Skill Tree Refund Token";
            case INFIN_TNT->ChatColor.GOLD+"Bombardeer's TNT";
            case GUARDIAN_SHIELD->ChatColor.BLUE+"Guardian Shield";
            case INFUSIUM->ChatColor.DARK_PURPLE+"Infusium";
            case "wrench_stone"->ChatColor.GRAY+"Stone Wrench";
            case "wrench_iron"->ChatColor.WHITE+"Iron Wrench";
            case "wrench_diamond"->ChatColor.AQUA+"Diamond Wrench";
            case "wrench_netherite"->ChatColor.DARK_PURPLE+"Netherite Wrench";
            default->"Ultrabound Custom Item";
        });
        m.setLore(switch(id){
            case RESET_CLASS->List.of(ChatColor.GRAY+"Confirm before changing your class.");
            case RESET_TREE->List.of(ChatColor.GRAY+"Refund allocated skill points.");
            case INFIN_TNT->List.of(ChatColor.GRAY+"Owner-bound. 4 second fuse and 4 second cooldown.",ChatColor.DARK_GRAY+"Returned to the Bombardeer after death.");
            case GUARDIAN_SHIELD->List.of(ChatColor.GRAY+"Owner-bound Guardian shield.");
            case INFUSIUM->List.of(ChatColor.GRAY+"Combines potion effects for Alchemists.");
            case "wrench_stone","wrench_iron","wrench_diamond","wrench_netherite"->List.of(ChatColor.GRAY+"Engineer-only: right-click to browse machines.",ChatColor.GRAY+"Machine node unlocks are still required.");
            default->List.of();
        });
        m.getPersistentDataContainer().set(idKey,PersistentDataType.STRING,id);
        m.getPersistentDataContainer().set(versionKey,PersistentDataType.INTEGER,1);
        if(owner!=null)m.getPersistentDataContainer().set(ownerKey,PersistentDataType.STRING,owner.toString());
        if(id.equals(GUARDIAN_SHIELD))m.setUnbreakable(true);
        item.setItemMeta(m);
        return item;
    }
    public void grantKit(Player p,String cls){
        PlayerStore.Data data=plugin.players().get(p.getUniqueId());
        if(data.kitClaimed)return;
        List<ItemStack> kit=new ArrayList<>();
        switch(cls){
            case "MINER"->{
                ItemStack pick=new ItemStack(Material.IRON_PICKAXE);
                pick.addUnsafeEnchantment(Enchantment.EFFICIENCY,3);
                kit.add(pick);kit.add(new ItemStack(Material.TORCH,32));
                kit.add(new ItemStack(Material.BREAD,16));kit.add(new ItemStack(Material.CRAFTING_TABLE));
            }
            case "BOMBARDEER"->kit.add(make(INFIN_TNT,p.getUniqueId()));
            case "MELEE"->{
                ItemStack sword=new ItemStack(Material.IRON_SWORD);
                sword.addUnsafeEnchantment(Enchantment.SHARPNESS,2);kit.add(sword);
            }
            case "VILLAGER_MASTER"->kit.add(new ItemStack(Material.VILLAGER_SPAWN_EGG,2));
            case "GUARDIAN"->kit.add(make(GUARDIAN_SHIELD,p.getUniqueId()));
            case "ALCHEMIST"->{
                kit.add(new ItemStack(Material.BREWING_STAND));kit.add(new ItemStack(Material.GLASS_BOTTLE,64));
                kit.add(new ItemStack(Material.BLAZE_ROD,4));kit.add(new ItemStack(Material.NETHER_WART,2));
                kit.add(new ItemStack(Material.GLOWSTONE_DUST,8));kit.add(new ItemStack(Material.REDSTONE,8));
            }
            case "ENCHANTER"->{
                kit.add(new ItemStack(Material.ENCHANTING_TABLE));kit.add(new ItemStack(Material.BOOKSHELF,3));
                kit.add(new ItemStack(Material.LAPIS_LAZULI,16));kit.add(new ItemStack(Material.EXPERIENCE_BOTTLE,16));
            }
            case "ARCHER"->{
                kit.add(new ItemStack(Material.BOW));kit.add(new ItemStack(Material.IRON_SWORD));
                kit.add(new ItemStack(Material.ARROW,48));kit.add(new ItemStack(Material.COOKED_BEEF,16));
                kit.add(new ItemStack(Material.BUNDLE));
            }
            case "ASSASSIN"->{
                kit.add(new ItemStack(Material.IRON_SWORD));kit.add(new ItemStack(Material.ENDER_PEARL,2));
                kit.add(new ItemStack(Material.SPYGLASS));kit.add(new ItemStack(Material.COOKED_CHICKEN,16));
                ItemStack invis=new ItemStack(Material.POTION);org.bukkit.inventory.meta.PotionMeta meta=(org.bukkit.inventory.meta.PotionMeta)invis.getItemMeta();
                meta.setBasePotionType(org.bukkit.potion.PotionType.INVISIBILITY);invis.setItemMeta(meta);kit.add(invis);
            }
            case "AVIATOR"->{
                kit.add(new ItemStack(Material.SPYGLASS));kit.add(new ItemStack(Material.WIND_CHARGE,8));
                kit.add(new ItemStack(Material.COOKED_CHICKEN,16));kit.add(new ItemStack(Material.COMPASS));
            }
            case "BEAST_TAMER"->{
                kit.add(new ItemStack(Material.BONE,16));kit.add(new ItemStack(Material.LEAD,2));
                kit.add(new ItemStack(Material.COOKED_BEEF,8));
            }
            case "ENGINEER"->{
                kit.add(new ItemStack(Material.COBBLESTONE,16));kit.add(new ItemStack(Material.STICK,8));
                kit.add(new ItemStack(Material.CRAFTING_TABLE));kit.add(new ItemStack(Material.BREAD,12));
            }
            default->{}
        }
        // Save claim BEFORE distributing. Reissued only through authorized admin action on failure.
        data.kitClaimed=true;plugin.players().save(data);
        for(ItemStack it:kit) {
            Map<Integer,ItemStack> overflow=p.getInventory().addItem(it);
            for(ItemStack extra:overflow.values())p.getWorld().dropItemNaturally(p.getLocation(),extra);
        }
    }
    public void updateBoundTntLabel(Player player){
        for(ItemStack item:player.getInventory().getContents()){
            if(!is(item,INFIN_TNT)||!player.getUniqueId().equals(owner(item)))continue;
            ItemMeta meta=item.getItemMeta();if(meta==null)continue;
            meta.setDisplayName(ChatColor.GOLD+"Bombardeer\'s TNT");
            meta.setLore(List.of(ChatColor.GRAY+"Owner-bound; four-second cooldown and fuse.",
                ChatColor.DARK_GRAY+"Returns to your inventory after death."));
            item.setItemMeta(meta);
        }
    }
    public boolean containsOwnedTnt(Player p){
        for(ItemStack item:p.getInventory().getContents())
            if(is(item,INFIN_TNT) && p.getUniqueId().equals(owner(item)))return true;
        return false;
    }
    public void restoreTntIfEligible(Player p){
        PlayerStore.Data d=plugin.players().get(p.getUniqueId());
        if(!"BOMBARDEER".equals(d.skillset) || !d.kitClaimed || containsOwnedTnt(p))return;
        // Only reissue after the player's original bound TNT was removed (death/respawn).
        // No world drop: full inventories will receive the item when a slot becomes available.
        var remainder=p.getInventory().addItem(make(INFIN_TNT,p.getUniqueId()));
        if(!remainder.isEmpty())p.sendMessage(ChatColor.RED+"Make an inventory slot for your bound Bombardeer's TNT; it will be restored on next login.");
    }
    public boolean containsOwnedShield(Player p){
        for(ItemStack item:p.getInventory().getContents())
            if(is(item,GUARDIAN_SHIELD) && p.getUniqueId().equals(owner(item)))return true;
        return false;
    }
    public void restoreShieldIfEligible(Player p){
        PlayerStore.Data d=plugin.players().get(p.getUniqueId());
        if(!"GUARDIAN".equals(d.skillset) || !d.kitClaimed || containsOwnedShield(p))return;
        var leftovers=p.getInventory().addItem(make(GUARDIAN_SHIELD,p.getUniqueId()));
        for(ItemStack extra:leftovers.values())p.getWorld().dropItemNaturally(p.getLocation(),extra);
    }
}
