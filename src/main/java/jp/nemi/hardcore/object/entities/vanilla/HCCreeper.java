package jp.nemi.hardcore.object.entities.vanilla;

import jp.nemi.hardcore.object.entities.ai.goal.HCSwellGoal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;
import java.util.Collection;

public class HCCreeper extends Monster implements PowerableMob {
        private final HCSwellGoal swellGoal = new HCSwellGoal(this);
        private static final EntityDataAccessor<Integer> DATA_SWELL_DIR = SynchedEntityData.defineId(HCCreeper.class, EntityDataSerializers.INT);
        private static final EntityDataAccessor<Boolean> DATA_IS_POWERED = SynchedEntityData.defineId(HCCreeper.class, EntityDataSerializers.BOOLEAN);
        private static final EntityDataAccessor<Boolean> DATA_IS_IGNITED = SynchedEntityData.defineId(HCCreeper.class, EntityDataSerializers.BOOLEAN);
        private int oldSwell;
        private int swell;
        private int maxSwell = 20;
        private int oldCoolTime;
        private int coolTime = 40;
        private int maxCoolTime = 40;
        private int explosionRadius = 6;
        private int droppedSkulls;

        public HCCreeper(EntityType<? extends HCCreeper> type, Level level) {
                super(type, level);
        }

        protected void registerGoals() {
                this.goalSelector.addGoal(1, new FloatGoal(this));
                this.goalSelector.addGoal(2, new HCSwellGoal(this));
                this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Ocelot.class, 6.0F, 1.0D, 1.2D));
                this.goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Cat.class, 6.0F, 1.0D, 1.2D));
                this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0D, false));
                this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
                this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
                this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
                this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
                this.targetSelector.addGoal(2, new HurtByTargetGoal(this));
        }

        public static AttributeSupplier.Builder createAttributes() {
                return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,30.0D )
                        .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                        .add(Attributes.FOLLOW_RANGE, 64.0D)
                        .add(Attributes.MOVEMENT_SPEED, 0.3D)
                        .add(Attributes.ARMOR, 5.0D);
        }

        @Override
        public int getMaxFallDistance() {
                return this.getTarget() == null ? 3 : 3 + (int)(this.getHealth() - 1.0F);
        }

        @Override
        public boolean causeFallDamage(float p_149687_, float p_149688_, DamageSource p_149689_) {
                boolean flag = super.causeFallDamage(p_149687_, p_149688_, p_149689_);
                this.swell += (int)(p_149687_ * 1.5F);

                if (this.swell > this.maxSwell - 5) {
                        this.swell = this.maxSwell - 5;
                }

                return flag;
        }

        protected void defineSynchedData() {
                super.defineSynchedData();
                this.entityData.define(DATA_SWELL_DIR, -1);
                this.entityData.define(DATA_IS_POWERED, false);
                this.entityData.define(DATA_IS_IGNITED, false);
        }

        public void addAdditionalSaveData(CompoundTag p_32304_) {
                super.addAdditionalSaveData(p_32304_);

                if (this.entityData.get(DATA_IS_POWERED)) {
                        p_32304_.putBoolean("powered", true);
                }

                p_32304_.putShort("Fuse", (short)this.maxSwell);
                p_32304_.putByte("ExplosionRadius", (byte)this.explosionRadius);
                p_32304_.putBoolean("ignited", this.isIgnited());
        }

        public void readAdditionalSaveData(CompoundTag p_32296_) {
                super.readAdditionalSaveData(p_32296_);
                this.entityData.set(DATA_IS_POWERED, p_32296_.getBoolean("powered"));

                if (p_32296_.contains("Fuse", 99)) {
                        this.maxSwell = p_32296_.getShort("Fuse");
                }
                if (p_32296_.contains("ExplosionRadius", 99)) {
                        this.explosionRadius = p_32296_.getByte("ExplosionRadius");
                }
                if (p_32296_.getBoolean("ignited")) {
                        this.ignite();
                }
        }

        public void tick() {
                if (this.isAlive()) {
                        this.oldSwell = this.swell;

                        if (this.coolTime >= this.maxCoolTime) {
                                //this.goalSelector.addGoal(2, swellGoal);

                                if (this.isIgnited()) {
                                        this.setSwellDir(1);
                                }

                                int i = this.getSwellDir();
                                if (i > 0 && this.swell == 0) {
                                        this.playSound(SoundEvents.CREEPER_PRIMED, 1.0F, 0.5F);
                                        this.gameEvent(GameEvent.PRIME_FUSE);
                                }

                                this.swell += i;
                                if (this.swell < 0) {
                                        this.swell = 0;
                                }

                                if (this.swell >= this.maxSwell) {
                                        //this.swell = this.maxSwell;
                                        this.swell = 0;
                                        this.coolTime = 0;
                                        this.setSwellDir(-1);
                                        this.explodeCreeper();
                                }
                        } else {
                                //this.goalSelector.removeGoal(swellGoal);

                                this.coolTime++;
                        }
                }

                super.tick();
        }

        public void setTarget(@Nullable LivingEntity p_149691_) {
                if (!(p_149691_ instanceof Goat)) {
                        super.setTarget(p_149691_);
                }
        }

        protected SoundEvent getHurtSound(DamageSource p_32309_) {
                return SoundEvents.CREEPER_HURT;
        }

        protected SoundEvent getDeathSound() {
                return SoundEvents.CREEPER_DEATH;
        }

        protected void dropCustomDeathLoot(DamageSource p_32292_, int p_32293_, boolean p_32294_) {
                super.dropCustomDeathLoot(p_32292_, p_32293_, p_32294_);
                Entity entity = p_32292_.getEntity();
                if (entity != this && entity instanceof Creeper creeper) {
                        if (creeper.canDropMobsSkull()) {
                                creeper.increaseDroppedSkulls();
                                this.spawnAtLocation(Items.CREEPER_HEAD);
                        }
                }

        }

        public boolean doHurtTarget(Entity p_32281_) {
                return true;
        }

        public boolean isPowered() {
                return this.entityData.get(DATA_IS_POWERED);
        }

        public float getSwelling(float p_32321_) {
                return Mth.lerp(p_32321_, (float)this.oldSwell, (float)this.swell) / (float)(this.maxSwell - 2);
        }

        public int getSwellDir() {
                return this.entityData.get(DATA_SWELL_DIR);
        }

        public void setSwellDir(int p_32284_) {
                this.entityData.set(DATA_SWELL_DIR, p_32284_);
        }

        public void thunderHit(ServerLevel p_32286_, LightningBolt p_32287_) {
                super.thunderHit(p_32286_, p_32287_);
                this.entityData.set(DATA_IS_POWERED, true);
        }

        protected InteractionResult mobInteract(Player p_32301_, InteractionHand p_32302_) {
                ItemStack itemstack = p_32301_.getItemInHand(p_32302_);
                if (itemstack.is(ItemTags.CREEPER_IGNITERS)) {
                        SoundEvent soundevent = itemstack.is(Items.FIRE_CHARGE) ? SoundEvents.FIRECHARGE_USE : SoundEvents.FLINTANDSTEEL_USE;
                        this.level().playSound(p_32301_, this.getX(), this.getY(), this.getZ(), soundevent, this.getSoundSource(), 1.0F, this.random.nextFloat() * 0.4F + 0.8F);
                        if (!this.level().isClientSide) {
                                this.ignite();
                                if (!itemstack.isDamageableItem()) {
                                        itemstack.shrink(1);
                                } else {
                                        itemstack.hurtAndBreak(1, p_32301_, (p_32290_) -> {
                                                p_32290_.broadcastBreakEvent(p_32302_);
                                        });
                                }
                        }

                        return InteractionResult.sidedSuccess(this.level().isClientSide);
                } else {
                        return super.mobInteract(p_32301_, p_32302_);
                }
        }

        private void explodeCreeper() {
                if (!this.level().isClientSide) {
                        float f = this.isPowered() ? 2.0F : 1.0F;
                        this.level().explode(this, this.getX(), this.getY(), this.getZ(), (float)this.explosionRadius * f, Level.ExplosionInteraction.MOB);
                        this.spawnLingeringCloud();
                        //this.setSwellDir(0);
                }
        }

        private void spawnLingeringCloud() {
                Collection<MobEffectInstance> collection = this.getActiveEffects();
                if (!collection.isEmpty()) {
                        AreaEffectCloud areaeffectcloud = new AreaEffectCloud(this.level(), this.getX(), this.getY(), this.getZ());
                        areaeffectcloud.setRadius(2.5F);
                        areaeffectcloud.setRadiusOnUse(-0.5F);
                        areaeffectcloud.setWaitTime(10);
                        areaeffectcloud.setDuration(areaeffectcloud.getDuration() / 2);
                        areaeffectcloud.setRadiusPerTick(-areaeffectcloud.getRadius() / (float)areaeffectcloud.getDuration());

                        for(MobEffectInstance mobeffectinstance : collection) {
                                areaeffectcloud.addEffect(new MobEffectInstance(mobeffectinstance));
                        }

                        this.level().addFreshEntity(areaeffectcloud);
                }

        }

        public boolean isIgnited() {
                return this.entityData.get(DATA_IS_IGNITED);
        }

        public void ignite() {
                this.entityData.set(DATA_IS_IGNITED, true);
        }

        public boolean canDropMobsSkull() {
                return this.isPowered() && this.droppedSkulls < 1;
        }

        public void increaseDroppedSkulls() {
                ++this.droppedSkulls;
        }
}
