package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Specific mechanics added to formerly preview-only passive nodes. */
public final class MorePassives implements Listener {
    private final UltraboundPlugin plugin;
    private final Map<String,Boolean> naturalSmelt = new HashMap<>();
    private final Map<UUID,Integer> stoneChain = new HashMap<>();
    private final Map<String,Long> cooldown = new HashMap<>();
    private final BukkitTask cleanup;
    MorePassives(UltraboundPlugin p){plugin=p;cleanup=Bukkit.getScheduler().runTaskTimer(p,this::expire,20L,100L);}
    private int rank(Player p,String id){return plugin.players().rank(plugin.players().get(p.getUniqueId()),id);}
    private boolean cls(Player p,String id){return id.equals(plugin.players().get(p.getUniqueId()).skillset);}
    private boolean ready(UUID id,String reason,long delay){String key=id+":"+reason;long now=System.currentTimeMillis();if(cooldown.getOrDefault(key,0L)>now)return false;cooldown.put(key,now+delay);return true;}
    private String key(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    private boolean wolfNearby(Player p){for(Wolf wolf:p.getWorld().getEntitiesByClass(Wolf.class))
        if(wolf.getOwner() instanceof Player owner && owner.getUniqueId().equals(p.getUniqueId()) && wolf.getLocation().distanceSquared(p.getLocation())<=36)return true;
        return false;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){
        Player p=e.getPlayer();if(!cls(p,"MINER") || p.getGameMode()!=GameMode.SURVIVAL)return;
        Block b=e.getBlock();
        if(b.getType()==Material.STONE || b.getType()==Material.DEEPSLATE){
            int streak=stoneChain.merge(p.getUniqueId(),1,Integer::sum);
            if(streak>=8 && rank(p,"earthshaper")>0 && ready(p.getUniqueId(),"earthshaper",30000)){
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE,100,1,true,false,true));stoneChain.put(p.getUniqueId(),0);
            }
        }else stoneChain.put(p.getUniqueId(),0);
        if(!plugin.events().eligibleNaturalOre(b))return;
        double chance=rank(p,"natural-smelting")*.10 + rank(p,"industrialist")*.10;
        if(chance>0 && Math.random()<Math.min(.8,chance)){
            String key=key(b);
            naturalSmelt.put(key,Boolean.TRUE);
            Bukkit.getScheduler().runTaskLater(plugin,()->naturalSmelt.remove(key),3L);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void oreDrops(BlockDropItemEvent e){
        if(!naturalSmelt.remove(key(e.getBlock())))return;
        for(Item item:e.getItems()){
            ItemStack stack=item.getItemStack();
            Material cooked=switch(stack.getType()){
                case RAW_IRON->Material.IRON_INGOT;
                case RAW_GOLD->Material.GOLD_INGOT;
                case RAW_COPPER->Material.COPPER_INGOT;
                default->null;
            };
            if(cooked!=null){ItemStack changed=stack.clone();changed.setType(cooked);item.setItemStack(changed);}
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player p))return;
        double reduction=0;
        if(cls(p,"MINER") && p.getLocation().getY()<0 && e instanceof EntityDamageByEntityEvent event && event.getDamager() instanceof Monster)
            reduction+=.04*rank(p,"cave-acclimation");
        if(cls(p,"BEAST_TAMER") && wolfNearby(p)){
            reduction+=.03*rank(p,"guardian-instinct");
            if(rank(p,"retreat-call")>0 && ready(p.getUniqueId(),"retreat-call",30000))
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,60,0,true,false,true));
        }
        if(!cls(p,"GUARDIAN")){
            for(Player ally:p.getWorld().getNearbyPlayers(p.getLocation(),6)){
                if(!cls(ally,"GUARDIAN") || !plugin.players().isTrusted(ally.getUniqueId(),p.getUniqueId()))continue;
                reduction+=.03*rank(ally,"trusted-ward");
            }
        }
        if(reduction>0)e.setDamage(e.getDamage()*(1-Math.min(.35,reduction)));
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void petHit(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Wolf wolf) || !(wolf.getOwner() instanceof Player owner) || !cls(owner,"BEAST_TAMER"))return;
        if(e.getEntity() instanceof Player victim && plugin.players().isTrusted(owner.getUniqueId(),victim.getUniqueId())){e.setCancelled(true);return;}
        e.setDamage(e.getDamage()*(1+.03*rank(owner,"pack-coordination")));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void potionDrink(PlayerItemConsumeEvent e){
        Player p=e.getPlayer();
        if(!cls(p,"ALCHEMIST")||e.getItem().getType()!=Material.POTION)return;
        int lasting=rank(p,"lasting-mixture"),sharing=rank(p,"shared-dose");
        if(lasting==0 && sharing==0)return;
        Bukkit.getScheduler().runTaskLater(plugin,()->{
            if(!p.isOnline())return;
            if(lasting>0)for(PotionEffect effect:new ArrayList<>(p.getActivePotionEffects())){
                if(!effect.getType().isBeneficial() || effect.getDuration()>12000)continue;
                p.addPotionEffect(new PotionEffect(effect.getType(),Math.min(12000,(int)(effect.getDuration()*(1+.04*lasting))),
                    effect.getAmplifier(),effect.isAmbient(),effect.hasParticles(),effect.hasIcon()));
            }
            if(sharing>0)for(Player ally:p.getWorld().getNearbyPlayers(p.getLocation(),5)){
                if(plugin.players().isTrusted(p.getUniqueId(),ally.getUniqueId()))
                    ally.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,20*(1+sharing),0,true,false,true));
            }
        },2L);
    }
    private void expire(){
        long now=System.currentTimeMillis();cooldown.entrySet().removeIf(entry->entry.getValue()<now-60000);
        for(Player p:Bukkit.getOnlinePlayers())if(cls(p,"ALCHEMIST") && rank(p,"measured-toxin")>0){
            int r=rank(p,"measured-toxin");
            for(PotionEffect effect:new ArrayList<>(p.getActivePotionEffects())){
                if(effect.getType().isBeneficial() || effect.getDuration()<120)continue;
                p.addPotionEffect(new PotionEffect(effect.getType(),Math.max(1,effect.getDuration()-10*r),
                    effect.getAmplifier(),effect.isAmbient(),effect.hasParticles(),effect.hasIcon()));
            }
        }
    }
    public void shutdown(){cleanup.cancel();}
}
