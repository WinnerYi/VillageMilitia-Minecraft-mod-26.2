package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;

public class MilitiaProtectOwnerGoal extends TargetGoal {
    private static final int RETALIATION_DURATION_TICKS = 100;
    private final VillageMilitiaEntity militia;
    private LivingEntity attackerOrTarget;
    private int lastHurtByTimestamp;
    private int retaliationTicks;

    public MilitiaProtectOwnerGoal(VillageMilitiaEntity militia) {
        super(militia, false);
        this.militia = militia;
    }

    @Override
    public boolean canUse() {
        if (this.militia.getMilitiaMode() == VillageMilitiaEntity.MilitiaMode.IDLE) {
            return false;
        }
        Player owner = this.militia.getOwner();
        if (owner == null) {
            return false;
        }

        LivingEntity candidate = owner.getLastHurtByMob();
        int currentTimestamp = owner.getLastHurtByMobTimestamp();

        if (candidate == null || currentTimestamp == this.lastHurtByTimestamp) {
            candidate = owner.getLastHurtMob();
            currentTimestamp = owner.getLastHurtMobTimestamp();
        }
        if (candidate == null || currentTimestamp == this.lastHurtByTimestamp || !candidate.isAlive()) {
            return false;
        }

        if (this.militia.distanceToSqr(candidate) > 256.0D || owner.distanceToSqr(candidate) > 256.0D) {
            return false;
        }

        if (isFriendlyFire(candidate, owner)) {
            return false;
        }

        if (!candidate.isAttackable() || candidate.isSpectator()) {
            return false;
        }

        this.attackerOrTarget = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.mob.getTarget();
        Player owner = this.militia.getOwner();
        return this.retaliationTicks-- > 0
            && target != null
            && target.isAlive()
            && owner != null
            && !this.isFriendlyFire(target, owner);
    }

    private boolean isFriendlyFire(LivingEntity target, Player owner) {
        return this.militia.isFriendlyTarget(target)
            || target instanceof Player targetPlayer && !owner.canHarmPlayer(targetPlayer);
    }

    @Override
    public void start() {
        this.mob.setTarget(this.attackerOrTarget);
        this.retaliationTicks = RETALIATION_DURATION_TICKS;

        Player owner = this.militia.getOwner();
        if (owner != null) {
            this.lastHurtByTimestamp = Math.max(owner.getLastHurtByMobTimestamp(), owner.getLastHurtMobTimestamp());
        }

        super.start();
    }
}