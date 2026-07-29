package com.example.examplemod.ai;
import java.util.EnumSet;
import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.raid.Raider;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.phys.AABB;

public class MilitiaReturnToGuardGoal extends Goal {
    private final VillageMilitiaEntity mob;
    private final double speed;

    public MilitiaReturnToGuardGoal(VillageMilitiaEntity mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        // 只有在 GUARD 模式、沒有敵對目標且遠離守衛點 2 格以上時啟用
        if (this.mob.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.GUARD) return false;
        if (this.mob.getTarget() != null && this.mob.getTarget().isAlive()) return false;
        
        BlockPos guardPos = this.mob.getGuardPos();
        return !guardPos.closerThan(this.mob.blockPosition(), 2.0D);
    }

    @Override
    public void start() {
        BlockPos pos = this.mob.getGuardPos();
        this.mob.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos guardPos = this.mob.getGuardPos();
        return !this.mob.getNavigation().isDone() 
            && !guardPos.closerThan(this.mob.blockPosition(), 1.5D)
            && (this.mob.getTarget() == null || !this.mob.getTarget().isAlive());
    }
}
