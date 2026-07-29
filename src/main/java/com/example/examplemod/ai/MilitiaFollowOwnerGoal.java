package com.example.examplemod.ai;

import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.PathType;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;

public class MilitiaFollowOwnerGoal extends Goal {
    private final VillageMilitiaEntity militia;
    private @Nullable LivingEntity owner;
    private final double speedModifier;
    private final PathNavigation navigation;
    private int timeToRecalcPath;
    private final float stopDistance;
    private final float startDistance;
    private float oldWaterCost;

    public MilitiaFollowOwnerGoal(VillageMilitiaEntity militia, double speedModifier, float startDistance, float stopDistance) {
        this.militia = militia;
        this.speedModifier = speedModifier;
        this.navigation = militia.getNavigation();
        this.startDistance = startDistance;
        this.stopDistance = stopDistance;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        
        if (!(militia.getNavigation() instanceof GroundPathNavigation) && !(militia.getNavigation() instanceof FlyingPathNavigation)) {
            throw new IllegalArgumentException("Unsupported mob type for MilitiaFollowOwnerGoal");
        }
    }

    @Override
    public boolean canUse() {
        if (this.militia.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.FOLLOW) {
            return false;
        }

        if (this.militia.getTarget() != null && this.militia.getTarget().isAlive()) {
            return false;
        }

        LivingEntity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            return false;
        }

        float effectiveStartDistance = this.militia.isPassenger() ? Math.max(this.startDistance, 4.0F) : this.startDistance;
        if (this.militia.distanceToSqr(owner) < (double)(effectiveStartDistance * effectiveStartDistance)) {
            return false;
        }

        this.owner = owner;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (!this.militia.isPassenger() && this.navigation.isDone()) {
            return false;
        }
        if (this.militia.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.FOLLOW) {
            return false;
        }
        if (this.militia.getTarget() != null && this.militia.getTarget().isAlive()) {
            return false;
        }
        if (this.owner == null || !this.owner.isAlive()) {
            return false;
        }
        
        float effectiveStopDistance = this.militia.isPassenger() ? this.stopDistance + 2.0F : this.stopDistance;
        return !(this.militia.distanceToSqr(this.owner) <= (double)(effectiveStopDistance * effectiveStopDistance));
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.oldWaterCost = this.militia.getPathfindingMalus(PathType.WATER);
        this.militia.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override
    public void stop() {
        this.owner = null;
        this.navigation.stop();
        
        if (this.militia.isPassenger() && this.militia.getVehicle() instanceof Mob vehicle) {
            vehicle.getNavigation().stop();
        }
        
        this.militia.setPathfindingMalus(PathType.WATER, this.oldWaterCost);
    }

    @Override
    public void tick() {
        if (this.owner == null) return;

        this.militia.getLookControl().setLookAt(this.owner, 10.0F, (float)this.militia.getMaxHeadXRot());

        if (--this.timeToRecalcPath <= 0) {
            this.timeToRecalcPath = this.adjustedTickDelay(10);
            
            // 🎯 移除傳送判斷，單純靠 Navigation 讓民兵走/騎過去
            double currentSpeed = this.militia.isPassenger() ? this.speedModifier * 2.4D : this.speedModifier;
            this.moveMilitiaOrVehicle(currentSpeed);
        }
    }

    private void moveMilitiaOrVehicle(double speed) {
        if (this.owner == null) return;

        if (this.militia.isPassenger() && this.militia.getVehicle() instanceof Mob vehicle) {
            vehicle.getNavigation().moveTo(this.owner, speed);
        } else {
            this.navigation.moveTo(this.owner, speed);
        }
    }

    private LivingEntity getOwner() {
        // 💡 如果 owner 距離超過 15 格，getNearestPlayer 就會回傳 null。
        // 如果希望離很遠（例如 30 格外）民兵還是能感應到並走過去，可以把這裡的 15.0D 提高（例如 32.0D）
        return this.militia.level().getNearestPlayer(this.militia, 32.0D);
    }

    // 已完全移除 teleportToOwner() 邏輯
}