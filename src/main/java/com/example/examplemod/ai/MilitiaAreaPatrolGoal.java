package com.example.examplemod.ai;
import com.example.examplemod.VillageMilitiaEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.util.LandRandomPos;


public class MilitiaAreaPatrolGoal extends WaterAvoidingRandomStrollGoal {
    private final VillageMilitiaEntity mob;

    public MilitiaAreaPatrolGoal(VillageMilitiaEntity mob, double speed) {
        super(mob, speed);
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (this.mob.getMilitiaMode() != VillageMilitiaEntity.MilitiaMode.PATROL) return false;
        return super.canUse();
    }

    @Override
    protected Vec3 getPosition() {
        // 以 GuardPos（或當前座標）為中心，在 12x6x12 的範圍內隨機尋找目標位置
        BlockPos center = this.mob.getGuardPos();
        Vec3 centerVec = Vec3.atBottomCenterOf(center);
        return LandRandomPos.getPosTowards(this.mob, 12, 6, centerVec);
    }
}