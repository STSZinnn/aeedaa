package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Assassin's stealth/ambush tree. Uses ordinary server events and never grants Night Vision. */
public final class AssassinPassives implements Listener {
    private final UltraboundPlugin plugin;
    private final Map<UUID, Long> lastAttack = new HashMap<>();
    private final Map<UUID, Long> lastHurtBoost = new HashMap<>();
    private final Map<UUID, Long> escapeShield = new HashMap<>();
    private final Map<UUID, UUID> previousTarget = new HashMap<>();
    private final Map<UUID, Long> previousHit = new HashMap<>();
    private final Map<UUID, Long> sneakSince = new HashMap<>();
    private final BukkitTask task;

    AssassinPassives(UltraboundPlugin p) {
        plugin=p;
        task=Bukkit.getScheduler().runTaskTimer(p,this::tick,40,20);
    }
    private boolean isAssassin(Player p) { return "ASSASSIN".equals(plugin.players().get(p.getUniqueId()).skillset); }
    private int rank(Player p,String key){return plugin.players().rank(plugin.players().get(p.getUniqueId()),key);}
    private boolean enemy(Player user,Player other){return !user.equals(other) && !plugin.players().isTrusted(user.getUniqueId(),other.getUniqueId());}

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player user) || !isAssassin(user) || !(e.getEntity() instanceof LivingEntity target))return;
        if(target instanceof Player other && (!enemy(user,other) || !user.getWorld().getPVP()))return;
        long now=System.currentTimeMillis();
        double multiplier=1;
        if(now-lastAttack.getOrDefault(user.getUniqueId(),0L)>5000)multiplier+=.04*rank(user,"first-strike");
        lastAttack.put(user.getUniqueId(),now);
        org.bukkit.util.Vector direction=target.getLocation().getDirection().setY(0);
        org.bukkit.util.Vector fromVictim=user.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if(direction.lengthSquared()>.01 && fromVictim.lengthSquared()>.01 && direction.normalize().dot(fromVictim.normalize())<-.50)
            multiplier+=.04*rank(user,"backstab");
        if(user.isSneaking())multiplier+=.02*rank(user,"predator-focus");
        UUID targetId=target.getUniqueId();
        if(targetId.equals(previousTarget.get(user.getUniqueId())) && now-previousHit.getOrDefault(user.getUniqueId(),0L)<4000)
            multiplier+=.03*rank(user,"focused-hunter");
        previousTarget.put(user.getUniqueId(),targetId);previousHit.put(user.getUniqueId(),now);
        e.setDamage(e.getDamage()*Math.min(1.30,multiplier));
        if(rank(user,"crippling-strike")>0 && Math.random()<.05*rank(user,"crippling-strike"))
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,40,0,true,false,true));
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void hurt(EntityDamageEvent e){
        if(!(e.getEntity() instanceof Player user) || !isAssassin(user))return;
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL && rank(user,"soft-landing")>0)
            e.setDamage(e.getDamage()*(1-Math.min(.40,.07*rank(user,"soft-landing"))));
        if(e instanceof EntityDamageByEntityEvent && rank(user,"evasive-step")>0){
            long now=System.currentTimeMillis();
            if(now-lastHurtBoost.getOrDefault(user.getUniqueId(),0L)>20000){
                lastHurtBoost.put(user.getUniqueId(),now);
                user.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,40*rank(user,"evasive-step"),0,true,false,true));
            }
        }
        if(rank(user,"escape-instinct")>0 && user.getHealth()-e.getFinalDamage()<=6 && user.getHealth()>e.getFinalDamage()){
            long now=System.currentTimeMillis();
            if(now-escapeShield.getOrDefault(user.getUniqueId(),0L)>90000){
                escapeShield.put(user.getUniqueId(),now);
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(user.isOnline()&&!user.isDead())user.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION,80,0,true,false,true));
                });
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void mobTarget(EntityTargetLivingEntityEvent e){
        if(!(e.getTarget() instanceof Player user)||!isAssassin(user)||!user.isSneaking()||!(e.getEntity() instanceof Monster))return;
        int r=rank(user,"quiet-step");
        if(r<=0)return;
        // Mob AI may still retain an existing target; suppresses only a fresh long-range acquisition.
        double squared=e.getEntity().getLocation().distanceSquared(user.getLocation());
        if(squared>Math.pow(Math.max(4,14-r*2),2))e.setCancelled(true);
    }

    private void tick(){
        long now=System.currentTimeMillis();
        for(Player user:Bukkit.getOnlinePlayers()){
            if(!isAssassin(user)){sneakSince.remove(user.getUniqueId());continue;}
            if(!user.isSneaking()){sneakSince.remove(user.getUniqueId());continue;}
            long since=sneakSince.computeIfAbsent(user.getUniqueId(),k->now);
            if(rank(user,"patient-shadow")>0 && now-since>=3000)
                user.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,30,0,true,false,false));
            int danger=rank(user,"danger-sense");
            if(danger>0){
                int radius=5+2*danger;
                int enemies=0;
                for(Player nearby:user.getWorld().getNearbyPlayers(user.getLocation(),radius))if(enemy(user,nearby)&&nearby.getGameMode()==GameMode.SURVIVAL)enemies++;
                if(enemies>0)user.sendActionBar(net.kyori.adventure.text.Component.text("§6Nearby opponents: "+enemies));
            }
            int recovery=rank(user,"steady-escape");
            if(recovery>0){
                PotionEffect slow=user.getPotionEffect(PotionEffectType.SLOWNESS);
                if(slow!=null && slow.getDuration()>30){
                    user.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        Math.max(1,slow.getDuration()-2*recovery),slow.getAmplifier(),slow.isAmbient(),slow.hasParticles(),slow.hasIcon()));
                }
            }
        }
    }
    public void shutdown(){task.cancel();}
}
