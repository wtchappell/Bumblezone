package net.telepathicgrunt.bumblezone.modcompatibility;

import net.minecraft.entity.MobEntity;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.telepathicgrunt.bumblezone.Bumblezone;
import net.telepathicgrunt.bumblezone.dimension.BzDimensionRegistration;

@Mod.EventBusSubscriber(modid = Bumblezone.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BuzzierBeesHoneySlimeSpawning
{
	@Mod.EventBusSubscriber(modid = Bumblezone.MODID)
	private static class ForgeEvents
	{
		/*
		 * Manual spawning on honey slime to bypass their heightmap checks and light checks.
		 * works by making 1/10th of bees spawning also spawn honey slime
		 */
		@SubscribeEvent
		public static void MobSpawnEvent(LivingSpawnEvent.CheckSpawn event)
		{
			if(ModChecking.buzzierBeesPresent) 
			{
				MobEntity entity = (MobEntity)event.getEntity();
				if(Bumblezone.BzConfig.spawnHoneySlimeMob.get() && 
					entity.dimension == BzDimensionRegistration.bumblezone() && 
					entity.world.getRandom().nextInt(10) == 0) 
				{
					BuzzierBeesRedirection.BBMobSpawnEvent(event);
				}
			}
		}
	}
}
