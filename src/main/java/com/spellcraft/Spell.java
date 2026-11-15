package com.spellcraft;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;

public enum Spell {
    //        id              r     g     b    cooldown(ticks) beam   effect
    STUPEFY("stupefy",       1.0f, 0.1f, 0.2f, 20,  true,  Spell::stupefy),
    LEVIOSA("leviosa",       0.8f, 0.95f, 1.0f, 30, true,  Spell::leviosa),
    GLACIUS("glacius",       0.4f, 0.8f, 1.0f, 40,  true,  Spell::glacius),
    INCENDIO("incendio",     1.0f, 0.5f, 0.1f, 40,  true,  Spell::incendio),
    BOMBARDA("bombarda",     0.6f, 0.9f, 1.0f, 60,  true,  Spell::bombarda),
    EXPELLIARMUS("expelliarmus", 0.9f, 0.2f, 0.9f, 30, true, Spell::expelliarmus),
    PROTEGO("protego",       0.3f, 0.5f, 1.0f, 120, false, Spell::protego),
    ACCIO("accio",           1.0f, 0.9f, 0.2f, 30,  true,  Spell::accio),
    LUMOS("lumos",           1.0f, 1.0f, 0.7f, 200, false, Spell::lumos);

    public static final double RANGE = 40.0;
    private static final Spell[] VALUES = values();

    public record Hit(Vec3d pos, @Nullable Entity entity, @Nullable BlockHitResult block) {}

    @FunctionalInterface
    interface Effect {
        void apply(Spell spell, ServerWorld w, ServerPlayerEntity p, @Nullable Hit hit);
    }

    public final String id;
    public final float r, g, b;
    public final int cooldown;
    public final boolean beam;
    private final Effect effect;

    Spell(String id, float r, float g, float b, int cooldown, boolean beam, Effect effect) {
        this.id = id; this.r = r; this.g = g; this.b = b;
        this.cooldown = cooldown; this.beam = beam; this.effect = effect;
    }

    public String translationKey() { return "spell.spellcraft." + id; }
    public Spell next() { return VALUES[(ordinal() + 1) % VALUES.length]; }
    public static Spell byIndex(int i) { return VALUES[Math.floorMod(i, VALUES.length)]; }

    private DustParticleEffect dust(float size) {
        return new DustParticleEffect(new Vector3f(r, g, b), size);
    }

    // ---------------------------------------------------------------- casting

    public void cast(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1.0f);
        Hit hit = null;

        if (beam) {
            hit = trace(w, p, eye, look);
            drawBeam(w, eye, look, hit.pos());
            w.spawnParticles(dust(1.6f), hit.pos().x, hit.pos().y, hit.pos().z, 30, 0.35, 0.35, 0.35, 0.02);
            w.spawnParticles(ParticleTypes.END_ROD, hit.pos().x, hit.pos().y, hit.pos().z, 8, 0.25, 0.25, 0.25, 0.04);
        }

        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL,
                SoundCategory.PLAYERS, 1.0f, 0.9f + 0.1f * ordinal());
        effect.apply(this, w, p, hit);
    }

    private static Hit trace(ServerWorld w, ServerPlayerEntity p, Vec3d eye, Vec3d look) {
        Vec3d end = eye.add(look.multiply(RANGE));
        BlockHitResult bh = w.raycast(new RaycastContext(eye, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, p));
        boolean blockHit = bh.getType() != HitResult.Type.MISS;
        double dist = blockHit ? bh.getPos().distanceTo(eye) : RANGE;
        Vec3d stop = eye.add(look.multiply(dist));
        Box box = p.getBoundingBox().stretch(look.multiply(dist)).expand(1.0);
        EntityHitResult eh = ProjectileUtil.raycast(p, eye, stop, box,
                e -> !e.isSpectator() && e.canHit(), dist * dist);
        if (eh != null) return new Hit(eh.getPos(), eh.getEntity(), null);
        if (blockHit) return new Hit(bh.getPos(), null, bh);
        return new Hit(end, null, null);
    }

    private void drawBeam(ServerWorld w, Vec3d eye, Vec3d look, Vec3d to) {
        Vec3d start = eye.add(look.multiply(0.8)).add(0, -0.25, 0);
        double len = to.distanceTo(start);
        DustParticleEffect d = dust(0.9f);
        for (double t = 0; t < len; t += 0.35) {
            Vec3d pt = start.add(look.multiply(t));
            w.spawnParticles(d, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0);
        }
    }

    private static void push(LivingEntity t, ServerPlayerEntity p, double strength) {
        Vec3d look = p.getRotationVec(1.0f);
        t.takeKnockback(strength, -look.x, -look.z);
    }

    // ---------------------------------------------------------------- spells

    private static void stupefy(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        if (h.entity() instanceof LivingEntity t) {
            t.damage(w.getDamageSources().indirectMagic(p, p), 6.0f);
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 6));
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 80, 1));
            push(t, p, 0.7);
        }
    }

    private static void leviosa(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        Entity e = h.entity();
        if (e instanceof LivingEntity t) {
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 100, 1));
        } else if (e != null) {
            e.addVelocity(0, 0.9, 0);
            e.velocityModified = true;
        }
    }

    private static void glacius(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        if (h.entity() instanceof LivingEntity t) {
            t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 3));
            t.setFrozenTicks(t.getMinFreezeDamageTicks() + 120);
            w.spawnParticles(ParticleTypes.SNOWFLAKE, t.getX(), t.getBodyY(0.5), t.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
        }
        // freeze water around the impact point
        BlockPos c = BlockPos.ofFloored(h.pos());
        for (BlockPos b : BlockPos.iterateOutwards(c, 3, 3, 3)) {
            if (w.getBlockState(b).isOf(Blocks.WATER)) {
                w.setBlockState(b.toImmutable(), Blocks.ICE.getDefaultState());
            }
        }
        w.spawnParticles(ParticleTypes.SNOWFLAKE, h.pos().x, h.pos().y, h.pos().z, 30, 1.0, 0.8, 1.0, 0.03);
    }

    private static void incendio(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        if (h.entity() != null) {
            h.entity().setOnFireFor(8);
        }
        if (h.block() != null) {
            BlockPos pos = h.block().getBlockPos().offset(h.block().getSide());
            if (w.getBlockState(pos).isAir() && Blocks.FIRE.getDefaultState().canPlaceAt(w, pos)) {
                w.setBlockState(pos, AbstractFireBlock.getState(w, pos));
            }
        }
        w.spawnParticles(ParticleTypes.FLAME, h.pos().x, h.pos().y, h.pos().z, 30, 0.4, 0.4, 0.4, 0.05);
    }

    private static void bombarda(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        Vec3d c = h.pos();
        double radius = 3.2;
        boolean grief = w.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);

        // explosion that never hurts the caster
        if (grief) {
            BlockPos center = BlockPos.ofFloored(c);
            int r = (int) Math.ceil(radius);
            for (BlockPos b : BlockPos.iterateOutwards(center, r, r, r)) {
                if (b.getSquaredDistance(c) > radius * radius) continue;
                BlockState st = w.getBlockState(b);
                if (st.isAir()) continue;
                float hardness = st.getHardness(w, b);
                if (hardness < 0 || hardness > 20f) continue; // bedrock, obsidian...
                w.breakBlock(b.toImmutable(), w.random.nextFloat() < 0.3f, p);
            }
        }
        List<LivingEntity> victims = w.getEntitiesByClass(LivingEntity.class,
                Box.of(c, radius * 2.5, radius * 2.5, radius * 2.5), e -> e != p && e.isAlive());
        for (LivingEntity e : victims) {
            e.damage(w.getDamageSources().indirectMagic(p, p), 10.0f);
            Vec3d dir = e.getPos().subtract(c).normalize();
            e.takeKnockback(1.3, -dir.x, -dir.z);
        }
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        w.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_DRAGON_FIREBALL_EXPLODE,
                SoundCategory.PLAYERS, 2.0f, 0.8f);
    }

    private static void expelliarmus(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        if (h.entity() instanceof LivingEntity t) {
            ItemStack held = t.getMainHandStack();
            if (!held.isEmpty()) {
                ItemEntity dropped = t.dropStack(held.copy());
                if (dropped != null) dropped.setPickupDelay(40);
                t.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            }
            push(t, p, 1.4);
        }
    }

    private static void protego(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 140, 2));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 140, 1));
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 16.0;
            double y = (i / 16) * 0.55;
            w.spawnParticles(s.dust(1.3f), p.getX() + Math.cos(a) * 1.6, p.getY() + y, p.getZ() + Math.sin(a) * 1.6,
                    1, 0, 0, 0, 0);
        }
        // shockwave pushes enemies away
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(3.5), x -> x != p)) {
            Vec3d dir = e.getPos().subtract(p.getPos()).normalize();
            e.takeKnockback(1.0, -dir.x, -dir.z);
        }
    }

    private static void accio(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        Vec3d to = p.getEyePos();
        // pull whatever was hit
        if (h.entity() != null) pull(h.entity(), to);
        // pull all loose items and XP orbs near the impact point
        Box area = Box.of(h.pos(), 16, 16, 16);
        for (Entity e : w.getEntitiesByClass(Entity.class, area,
                x -> x != p && (x instanceof ItemEntity || x instanceof ExperienceOrbEntity))) {
            pull(e, to);
        }
    }

    private static void pull(Entity e, Vec3d to) {
        Vec3d v = to.subtract(e.getPos()).normalize().multiply(1.4).add(0, 0.25, 0);
        e.setVelocity(v);
        e.velocityModified = true;
    }

    private static void lumos(Spell s, ServerWorld w, ServerPlayerEntity p, Hit h) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 600, 0));
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(24), x -> x != p)) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 200, 0));
        }
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getBodyY(0.5), p.getZ(), 40, 1.5, 1.0, 1.5, 0.05);
    }
}
