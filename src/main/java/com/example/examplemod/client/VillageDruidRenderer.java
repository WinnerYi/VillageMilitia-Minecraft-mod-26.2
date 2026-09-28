package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.entity.VillageDruidEntity;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class VillageDruidRenderer extends HumanoidMobRenderer<
    VillageDruidEntity,
    VillageDruidRenderer.MyRenderState,
    VillageDruidModel
> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
        ExampleMod.MODID,
        "textures/entity/druid_man.png"
    );

    public VillageDruidRenderer(EntityRendererProvider.Context context) {
        super(
            context,
            new VillageDruidModel(context.bakeLayer(ModelLayers.PLAYER)),
            0.5F
        );
    }

    public static class MyRenderState extends HumanoidRenderState {
        public boolean isCasting;
        public float castProgress;
        public int spellType;
    }

    @Override
    public MyRenderState createRenderState() {
        return new MyRenderState();
    }

    @Override
    public void extractRenderState(
        VillageDruidEntity entity,
        MyRenderState state,
        float partialTick
    ) {
        super.extractRenderState(entity, state, partialTick);
        state.isCasting = entity.isCasting();
        state.castProgress = entity.getCastProgress(partialTick);
        state.spellType = entity.getSpellType();
    }

    @Override
    public Identifier getTextureLocation(MyRenderState state) {
        return TEXTURE;
    }
}