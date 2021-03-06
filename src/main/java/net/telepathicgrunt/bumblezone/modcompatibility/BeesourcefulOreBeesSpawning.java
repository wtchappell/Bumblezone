package net.telepathicgrunt.bumblezone.modcompatibility;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MobEntity;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.telepathicgrunt.bumblezone.Bumblezone;
import net.telepathicgrunt.bumblezone.dimension.BzDimensionRegistration;

@Mod.EventBusSubscriber(modid = Bumblezone.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class BeesourcefulOreBeesSpawning
{
	@Mod.EventBusSubscriber(modid = Bumblezone.MODID)
	private static class ForgeEvents
	{
		/*
		 * Manual spawning of Beesourceful Bees so it can be disabled real time by config.
		 * works by making 1/15th of bees spawning also spawn beesourceful bees
		 */
		@SubscribeEvent
		public static void MobSpawnEvent(LivingSpawnEvent.CheckSpawn event)
		{
			if(ModChecking.beesourcefulPresent) 
			{
				MobEntity entity = (MobEntity)event.getEntity();
				if(Bumblezone.BzConfig.spawnBeesourcefulBeesMob.get() && 
					entity.dimension == BzDimensionRegistration.bumblezone() && 
					entity.world.getRandom().nextInt(15) == 0 &&
					entity.getType() == EntityType.BEE) 
				{
					BeesourcefulRedirection.BSMobSpawnEvent(event);
				}
			}
		}
	}
}
