package dev.ultrabound;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Additional real server-side passive hooks. Never marks a node as implemented unless its
 * rank is consumed in an actual event or periodic game mechanic. All skillsets checked.
 * The remaining unsupported concepts stay locked in skillsets.yml.
 */
public final class ExpandedPassives implements Listener {
    private final UltraboundPlugin plugin;
    private final BukkitTask pulseTask;
    private final Map<UUID,UUID> comboTarget=new HashMap<>();
    private final Map<UUID,Long> comboTime=new HashMap<>();
    private final Map<String,Long> effectCooldown=new HashMap<>();
    private final Set<String> eligibleDrops=new HashSet<>();
    private String posKey(Block b){return b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();}
    ExpandedPassives(UltraboundPlugin plugin){
        this.plugin=plugin;
        pulseTask=Bukkit.getScheduler().runTaskTimer(plugin,this::pulse,60L,40L);
    }
    private boolean cls(Player p,String skill){return skill.equals(plugin.players().get(p.getUniqueId()).skillset);}
    private int rank(Player p,String key){return plugin.players().rank(plugin.players().get(p.getUniqueId()),key);}
    private boolean proc(double chance){return Math.random()<Math.max(0,Math.min(.9,chance));}
    private boolean ready(Player p,String key,long delayMillis){
        String id=p.getUniqueId()+":"+key;
        long now=System.currentTimeMillis();
        if(effectCooldown.getOrDefault(id,0L)>now)return false;
        effectCooldown.put(id,now+delayMillis);
        return true;
    }
    private boolean melee(Player p){return p.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD") || p.getInventory().getItemInMainHand().getType().name().endsWith("_AXE");}
    private double healthRatio(Player p){var att=p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);return p.getHealth()/Math.max(1,att==null?20:att.getValue());}
    private void effect(Player p,PotionEffectType type,int ticks,int amplifier){
        p.addPotionEffect(new PotionEffect(type,ticks,Math.max(0,amplifier),true,false,false));
    }
    private boolean nearTrusted(Player p,double radius){
        for(Player other:p.getWorld().getNearbyPlayers(p.getLocation(),radius)){
            if(!other.equals(p) && plugin.players().isTrusted(p.getUniqueId(),other.getUniqueId()))return true;
        }
        return false;
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void fight(EntityDamageByEntityEvent event){
        if(event.getEntity() instanceof Player hurt){
            double cut=0;
            if(cls(hurt,"MELEE")){
                if(hurt.isBlocking())cut+=rank(hurt,"iron-will")*.025+rank(hurt,"counterguard")*.015;
                if(hurt.isSprinting())cut+=rank(hurt,"battle-focus")*.012;
                if(healthRatio(hurt)<.35)cut+=rank(hurt,"last-stand")*.035;
            }
            if(cls(hurt,"GUARDIAN")){
                if(hurt.isBlocking())cut+=rank(hurt,"shield-training")*.03+rank(hurt,"firm-ground")*.025;
                if(nearTrusted(hurt,6))cut+=rank(hurt,"shared-cover")*.02;
                if(healthRatio(hurt)<.35)cut+=rank(hurt,"unyielding")*.04;
            }
            if(cls(hurt,"BEAST_TAMER")){
                for(Wolf wolf:hurt.getWorld().getEntitiesByClass(Wolf.class)){
                    if(wolf.getOwner() instanceof Player owner && owner.getUniqueId().equals(hurt.getUniqueId()) && wolf.getLocation().distanceSquared(hurt.getLocation())<=36){
                        cut+=rank(hurt,"shared-instinct")*.02;
                        break;
                    }
                }
            }
            if(cls(hurt,"ENGINEER") && rank(hurt,"reinforced-frame")>0)cut+=rank(hurt,"reinforced-frame")*.015;
            if(cut>0)event.setDamage(event.getDamage()*(1-Math.min(.40,cut)));
            if(cls(hurt,"GUARDIAN") && healthRatio(hurt)<.3 && rank(hurt,"last-stand")>0 && ready(hurt,"guardian-last-stand",90000))
                effect(hurt,PotionEffectType.ABSORPTION,100,0);
            if(cls(hurt,"ALCHEMIST") && healthRatio(hurt)<.3 && rank(hurt,"emergency-vial")>0 && ready(hurt,"alchemist-emergency",120000))
                effect(hurt,PotionEffectType.REGENERATION,100,0);
            if(cls(hurt,"MELEE") && healthRatio(hurt)<.3 && rank(hurt,"unbreakable-spirit")>0 && ready(hurt,"melee-last-stand",90000))
                effect(hurt,PotionEffectType.ABSORPTION,60,0);
        }
        if(event.getDamager() instanceof Player attacker && event.getEntity() instanceof LivingEntity victim){
            if(cls(attacker,"MELEE") && melee(attacker)){
                int combo=rank(attacker,"combo-rhythm")+rank(attacker,"relentless");
                if(combo>0){
                    UUID id=victim.getUniqueId();long now=System.currentTimeMillis();
                    if(id.equals(comboTarget.get(attacker.getUniqueId())) && now-comboTime.getOrDefault(attacker.getUniqueId(),0L)<4000)
                        event.setDamage(event.getDamage()*(1+Math.min(.20,combo*.015)));
                    comboTarget.put(attacker.getUniqueId(),id);comboTime.put(attacker.getUniqueId(),now);
                }
                if(rank(attacker,"pursuit")>0 && ready(attacker,"pursuit",6500))effect(attacker,PotionEffectType.SPEED,25+rank(attacker,"pursuit")*8,0);
                if(rank(attacker,"disarming-technique")>0 && proc(.025*rank(attacker,"disarming-technique"))
                    && victim instanceof Player other && !plugin.players().isTrusted(attacker.getUniqueId(),other.getUniqueId()))
                    effect(other,PotionEffectType.MINING_FATIGUE,35,0);
            }
            if(cls(attacker,"ARCHER") && rank(attacker,"keen-eye")>0 && victim instanceof Player other && ready(attacker,"keen-eye",8000))
                effect(other,PotionEffectType.GLOWING,20+10*rank(attacker,"keen-eye"),0);
        }
        if(event.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player archer && cls(archer,"ARCHER")){
            int training=rank(archer,"steady-breath")+rank(archer,"keen-eye");
            if(training>0 && event.getEntity() instanceof LivingEntity && !arrow.isCritical())
                event.setDamage(event.getDamage()*(1+Math.min(.18,training*.015)));
        }
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void receiveDamage(EntityDamageEvent event){
        if(!(event.getEntity() instanceof Player p))return;
        double cut=0;
        if(event.getCause()==EntityDamageEvent.DamageCause.FALL){
            if(cls(p,"MELEE"))cut+=rank(p,"light-feet")*.04+rank(p,"combat-roll")*.05;
            if(cls(p,"AVIATOR"))cut+=rank(p,"safe-route")*.06+rank(p,"steady-flight")*.04;
        }
        if(cls(p,"BOMBARDEER") && (event.getCause()==EntityDamageEvent.DamageCause.ENTITY_EXPLOSION || event.getCause()==EntityDamageEvent.DamageCause.BLOCK_EXPLOSION))
            cut+=rank(p,"explosive-discipline")*.04;
        if(cls(p,"GUARDIAN") && event.getCause()==EntityDamageEvent.DamageCause.PROJECTILE)
            cut+=rank(p,"intercept")*.03;
        if(cut>0)event.setDamage(Math.max(0,event.getDamage()*(1-Math.min(.40,cut))));
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void heal(EntityRegainHealthEvent event){
        if(!(event.getEntity() instanceof Player p))return;
        double factor=0;
        if(cls(p,"MELEE"))factor+=rank(p,"battle-focus")*.02;
        if(cls(p,"GUARDIAN"))factor+=rank(p,"second-breath")*.04;
        if(cls(p,"ALCHEMIST"))factor+=rank(p,"restorative-draft")*.05;
        if(cls(p,"BEAST_TAMER"))factor+=rank(p,"deep-bond")*.025;
        if(factor>0)event.setAmount(event.getAmount()*(1+Math.min(.25,factor)));
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void food(PlayerItemConsumeEvent e){
        Player p=e.getPlayer();
        if(cls(p,"ARCHER") && rank(p,"field-rations")>0 && e.getItem().getType().isEdible()){
            int rank=rank(p,"field-rations");
            Bukkit.getScheduler().runTask(plugin,()->{
                if(p.isOnline())p.setFoodLevel(Math.min(20,p.getFoodLevel()+Math.min(2,rank)));
            });
        }
        if(cls(p,"ALCHEMIST") && e.getItem().getType()==Material.POTION && rank(p,"careful-dose")>0){
            int r=rank(p,"careful-dose");
            Bukkit.getScheduler().runTaskLater(plugin,()->{
                if(!p.isOnline())return;
                for(PotionEffect buff:new ArrayList<>(p.getActivePotionEffects())){
                    if(!buff.getType().isBeneficial())continue;
                    int longer=Math.min(buff.getDuration()+r*15,12000);
                    p.addPotionEffect(new PotionEffect(buff.getType(),longer,buff.getAmplifier(),buff.isAmbient(),buff.hasParticles(),buff.hasIcon()));
                }
            },1L);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void killed(EntityDeathEvent event){
        Player killer=event.getEntity().getKiller();
        if(killer==null)return;
        if(cls(killer,"MELEE") && rank(killer,"blood-rush")>0 && ready(killer,"blood-rush",10000))
            effect(killer,PotionEffectType.SPEED,45+rank(killer,"blood-rush")*15,0);
        if(cls(killer,"ENCHANTER") && rank(killer,"old-knowledge")>0)
            event.setDroppedExp(event.getDroppedExp()+Math.min(3,rank(killer,"old-knowledge")));
        if(cls(killer,"ARCHER") && rank(killer,"supply-recovery")>0 && proc(.03*rank(killer,"supply-recovery")))
            event.getDrops().add(new ItemStack(Material.ARROW,1));
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ore(BlockBreakEvent event){
        Player p=event.getPlayer();
        Block block=event.getBlock();Material type=block.getType();
        if(!cls(p,"MINER") || !plugin.events().eligibleNaturalOre(block))return;
        if(p.getGameMode()!=GameMode.SURVIVAL || !event.isDropItems())return;
        if(p.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE") == false)return;
        Material drop=switch(type){
            case DIAMOND_ORE,DEEPSLATE_DIAMOND_ORE->Material.DIAMOND;
            case EMERALD_ORE,DEEPSLATE_EMERALD_ORE->Material.EMERALD;
            case GOLD_ORE,DEEPSLATE_GOLD_ORE->Material.RAW_GOLD;
            case IRON_ORE,DEEPSLATE_IRON_ORE->Material.RAW_IRON;
            case COPPER_ORE,DEEPSLATE_COPPER_ORE->Material.RAW_COPPER;
            case COAL_ORE,DEEPSLATE_COAL_ORE->Material.COAL;
            case LAPIS_ORE,DEEPSLATE_LAPIS_ORE->Material.LAPIS_LAZULI;
            case REDSTONE_ORE,DEEPSLATE_REDSTONE_ORE->Material.REDSTONE;
            default->null;
        };
        if(drop==null)return;
        if(plugin.abilities().autoSmeltActive(p.getUniqueId())){
            String key=posKey(block);eligibleDrops.add(key);
            Bukkit.getScheduler().runTaskLater(plugin,()->eligibleDrops.remove(key),2L);
        }
        boolean rare=drop==Material.DIAMOND || drop==Material.EMERALD;
        double chance=rank(p,"lucky-find")*.02;
        if(plugin.abilities().miningBoostActive(p.getUniqueId()))chance+=.15;
        if(drop==Material.RAW_GOLD)chance+=rank(p,"gold-rush")*.05;
        if(drop==Material.DIAMOND)chance+=rank(p,"diamond-eye")*.02;
        if(drop==Material.EMERALD)chance+=rank(p,"emerald-hunter")*.02;
        if(rare)chance+=rank(p,"fortune-s-favor")*.025;
        if(rank(p,"treasure-seeker")>0)chance+=rank(p,"treasure-seeker")*.005;
        if(proc(Math.min(.25,chance))){
            Location loc=block.getLocation().add(.5,.5,.5);
            // Executes after vanilla broke the block; only if it is now air.
            Bukkit.getScheduler().runTask(plugin,()->{
                if(loc.getBlock().isEmpty() && loc.getWorld()!=null)loc.getWorld().dropItemNaturally(loc,new ItemStack(drop,1));
            });
        }
        int repair=rank(p,"repairman")+rank(p,"reinforced-pickaxe");
        if(repair>0 && proc(.01*repair)){
            ItemStack tool=p.getInventory().getItemInMainHand();
            if(tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta && meta.getDamage()>0){
                meta.setDamage(Math.max(0,meta.getDamage()-1));tool.setItemMeta(meta);
            }
        }
    }

    /** Modifies vanilla drops (not creating a second drop), only for previously validated
     * natural ores. Claims can cancel the initial break event; no drops then get changed.
     */
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void smeltOreDrops(BlockDropItemEvent event){
        if(!eligibleDrops.remove(posKey(event.getBlock())))return;
        if(!cls(event.getPlayer(),"MINER") || !plugin.abilities().autoSmeltActive(event.getPlayer().getUniqueId()))return;
        for(org.bukkit.entity.Item item:event.getItems()){
            ItemStack stack=item.getItemStack();
            Material after=switch(stack.getType()){
                case RAW_IRON->Material.IRON_INGOT;
                case RAW_GOLD->Material.GOLD_INGOT;
                case RAW_COPPER->Material.COPPER_INGOT;
                default->null;
            };
            if(after!=null){
                ItemStack cooked=stack.clone();cooked.setType(after);
                item.setItemStack(cooked);
            }
        }
    }

    /** Paper-only trade completion event. Never edits merchant recipes or creates emeralds.
     * Grants small configurable utility bursts and modest earned skill points instead.
     */
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void villagerTrade(PlayerTradeEvent event){
        Player player=event.getPlayer();
        if(!cls(player,"VILLAGER_MASTER"))return;
        int speed=rank(player,"swift-delivery");
        if(speed>0 && ready(player,"swift-delivery",15000))effect(player,PotionEffectType.SPEED,25+speed*15,0);
        int emerald=rank(player,"emerald-eye")+rank(player,"market-insight");
        if(rank(player,"bulk-buyer")+rank(player,"reliable-client")>0 && ready(player,"trade-knowledge",30000)){
            int points=Math.min(3,rank(player,"bulk-buyer")+rank(player,"reliable-client"));
            player.giveExp(points);
        }
        // Trading milestones have a time gate; no free repeated emerald loop.
        if(emerald>0 && ready(player,"trade-progression",60000) && Math.random()<Math.min(.35,emerald*.035)){
            PlayerStore.Data data=plugin.players().get(player.getUniqueId());
            data.availablePoints++;data.earnedPoints++;
            plugin.players().save(data);
            player.sendMessage(ChatColor.AQUA+"Trade mastery: earned 1 Ultrabound point.");
        }
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void enchant(EnchantItemEvent e){
        Player p=e.getEnchanter();
        if(!cls(p,"ENCHANTER"))return;
        int r=rank(p,"efficient-table")+rank(p,"lorekeeper");
        if(r>0)e.setExpLevelCost(Math.max(1,e.getExpLevelCost()-Math.min(3,r)));
    }

    private void pulse(){
        for(Player p:Bukkit.getOnlinePlayers()){
            // Proximity awareness gives brief, visible, non-ESP feedback.
            if(cls(p,"VILLAGER_MASTER") && rank(p,"local-knowledge")>0){
                int count=0;
                double radius=6+2*rank(p,"local-knowledge");
                for(Villager villager:p.getWorld().getNearbyEntitiesByType(Villager.class,p.getLocation(),radius)){
                    if(++count>4)break;
                    p.spawnParticle(Particle.HAPPY_VILLAGER,villager.getEyeLocation(),2,.15,.2,.15,0);
                }
            }
            if(cls(p,"ARCHER") && rank(p,"keen-ear")>0 && ready(p,"keen-ear-warning",12000)){
                for(Player nearby:p.getWorld().getNearbyPlayers(p.getLocation(),5+rank(p,"keen-ear")*2)){
                    if(!nearby.equals(p) && !plugin.players().isTrusted(p.getUniqueId(),nearby.getUniqueId())){
                        p.sendActionBar(net.kyori.adventure.text.Component.text("Archer instinct: nearby player"));break;
                    }
                }
            }
            if(cls(p,"AVIATOR") && rank(p,"wind-reader")>0 && !p.isOnGround() && p.getFallDistance()>4 && ready(p,"wind-warning",10000))
                p.sendActionBar(net.kyori.adventure.text.Component.text("Wind Reader: prepare to land!"));
            if(cls(p,"GUARDIAN") && rank(p,"steady-heart")>0 && p.hasPotionEffect(PotionEffectType.SLOWNESS) && ready(p,"steady-heart",9000)){
                PotionEffect slow=p.getPotionEffect(PotionEffectType.SLOWNESS);
                if(slow!=null && slow.getDuration()>25){
                    p.removePotionEffect(PotionEffectType.SLOWNESS);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,Math.max(1,slow.getDuration()-rank(p,"steady-heart")*15),slow.getAmplifier(),slow.isAmbient(),slow.hasParticles(),slow.hasIcon()));
                }
            }
            if(cls(p,"MINER") && rank(p,"iron-stomach")>0 && ready(p,"iron-stomach",11000)){
                for(PotionEffect effect:new ArrayList<>(p.getActivePotionEffects())){
                    if(effect.getType()!=PotionEffectType.POISON && effect.getType()!=PotionEffectType.HUNGER)continue;
                    if(effect.getDuration()<=30)continue;
                    p.removePotionEffect(effect.getType());
                    p.addPotionEffect(new PotionEffect(effect.getType(),Math.max(1,effect.getDuration()-rank(p,"iron-stomach")*15),effect.getAmplifier(),effect.isAmbient(),effect.hasParticles(),effect.hasIcon()));
                }
            }
            if(cls(p,"ASSASSIN") && rank(p,"silent-effects")>0){
                for(PotionEffect active:new ArrayList<>(p.getActivePotionEffects())){
                    if(!active.hasParticles())continue;
                    p.addPotionEffect(new PotionEffect(active.getType(), active.getDuration(), active.getAmplifier(),
                        active.isAmbient(), false, active.hasIcon()));
                }
            }
            if(cls(p,"MINER")){
                if(p.getLocation().getY()<0){
                    if(rank(p,"underground-instinct")>0)effect(p,PotionEffectType.SPEED,65,0);
                    if(rank(p,"master-explorer")>0)effect(p,PotionEffectType.HASTE,65,0);
                }
                if(p.isInWater() && rank(p,"oxygen-efficiency")>0)effect(p,PotionEffectType.CONDUIT_POWER,65,0);
                if(rank(p,"quick-hands")>0 && p.getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE"))
                    effect(p,PotionEffectType.HASTE,65,0);
                if(rank(p,"stone-breaker")>0 || rank(p,"demolition-expert")>0){
                    Block looked=p.getTargetBlockExact(5);
                    if(looked!=null && (looked.getType().name().contains("STONE") || looked.getType().name().contains("DEEPSLATE")))
                        effect(p,PotionEffectType.HASTE,65,0);
                }
            }
            if(cls(p,"MELEE")){
                if(rank(p,"chaser")>0 && melee(p)){
                    for(Player target:p.getWorld().getNearbyPlayers(p.getLocation(),8)){
                        if(!target.equals(p) && !plugin.players().isTrusted(p.getUniqueId(),target.getUniqueId())){
                            effect(p,PotionEffectType.SPEED,65,0);break;
                        }
                    }
                }
            }
            if(cls(p,"ARCHER") && rank(p,"light-quiver")>0 && p.getInventory().getItemInMainHand().getType()==Material.BOW)
                effect(p,PotionEffectType.SPEED,65,0);
            if(cls(p,"AVIATOR")){
                if(rank(p,"spring-step")>0 && p.isOnGround())effect(p,PotionEffectType.JUMP_BOOST,65,0);
                if(rank(p,"traveler")>0 && p.isSprinting())effect(p,PotionEffectType.SPEED,65,0);
                if(rank(p,"featherweight")>0 && !p.isOnGround())effect(p,PotionEffectType.SLOW_FALLING,65,0);
            }
            if(cls(p,"BEAST_TAMER") && rank(p,"mount-training")>0 && p.isInsideVehicle())
                effect(p,PotionEffectType.SPEED,65,0);
        }
    }
    public void shutdown(){pulseTask.cancel();}
}
