package net.telepathicgrunt.bumblezone.entities;

import java.util.ArrayList;

import javax.annotation.Nullable;

import org.apache.logging.log4j.Level;

import com.google.common.primitives.Doubles;

import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EnderPearlEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.potion.EffectInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.server.TicketType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.telepathicgrunt.bumblezone.Bumblezone;
import net.telepathicgrunt.bumblezone.capabilities.IPlayerPosAndDim;
import net.telepathicgrunt.bumblezone.capabilities.PlayerPositionAndDimension;
import net.telepathicgrunt.bumblezone.dimension.BzDimensionRegistration;
import net.telepathicgrunt.bumblezone.modcompatibility.ModChecking;
import net.telepathicgrunt.bumblezone.modcompatibility.ProductiveBeesRedirection;
import net.telepathicgrunt.bumblezone.utils.BzPlacingUtils;


@Mod.EventBusSubscriber(modid = Bumblezone.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlayerTeleportationBehavior
{

	@CapabilityInject(IPlayerPosAndDim.class)
	public static Capability<IPlayerPosAndDim> PAST_POS_AND_DIM = null;

	@Mod.EventBusSubscriber(modid = Bumblezone.MODID)
	private static class ForgeEvents
	{

		@SubscribeEvent
		public static void ProjectileImpactEvent(net.minecraftforge.event.entity.ProjectileImpactEvent.Throwable event)
		{
			EnderPearlEntity pearlEntity; 

			if (event.getEntity() instanceof EnderPearlEntity)
			{
				pearlEntity = (EnderPearlEntity) event.getEntity(); // the thrown pearl
			}
			else
			{
				return; //not a pearl, exit event
			}

			World world = pearlEntity.world; // world we threw in

			//Make sure we are on server by checking if thrower is ServerPlayerEntity
			if (!world.isRemote && pearlEntity.getThrower() instanceof ServerPlayerEntity)
			{
				ServerPlayerEntity playerEntity = (ServerPlayerEntity) pearlEntity.getThrower(); // the thrower
				Vec3d hitBlockPos = event.getRayTraceResult().getHitVec(); //position of the collision
				BlockPos hivePos = null;
				boolean hitHive = false;
				
				//check with offset in all direction as the position of exact hit point could barely be outside the hive block
				//even through the pearl hit the block directly.
				for(double offset = -0.1D; offset <= 0.1D; offset += 0.1D) {
				    	BlockState block = world.getBlockState(new BlockPos(hitBlockPos.add(offset, 0, 0)));
					if(isValidBeeHive(block)) {
						hitHive = true;
						hivePos = new BlockPos(hitBlockPos.add(offset, 0, 0));
						break;
					}
					
					block = world.getBlockState(new BlockPos(hitBlockPos.add(0, offset, 0)));
					if(isValidBeeHive(block)) {
						hitHive = true;
						hivePos = new BlockPos(hitBlockPos.add(0, offset, 0));
						break;
					}
					
					block = world.getBlockState(new BlockPos(hitBlockPos.add(0, 0, offset)));
					if(isValidBeeHive(block)) {
						hitHive = true;
						hivePos = new BlockPos(hitBlockPos.add(0, 0, offset));
						break;
					}
				}
				
				//checks if block under hive is correct if config needs one
				boolean validBelowBlock = false;
				String requiredBlockString = Bumblezone.BzConfig.requiredBlockUnderHive.get().toLowerCase().trim();
				if(!requiredBlockString.isEmpty() && hivePos != null) 
				{
					if(requiredBlockString.matches("[a-z0-9/._-]+:[a-z0-9/._-]+") && ForgeRegistries.BLOCKS.containsKey(new ResourceLocation(requiredBlockString))) 
					{
						Block requiredBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(requiredBlockString));
						if(requiredBlock == world.getBlockState(hivePos.down()).getBlock()) 
						{
						    validBelowBlock = true;
						}
						else if(Bumblezone.BzConfig.warnPlayersOfWrongBlockUnderHive.get())
						{
							//failed. Block below isn't the required block
							String beeBlock = world.getBlockState(hivePos).getBlock().getRegistryName().toString();
							Bumblezone.LOGGER.log(Level.INFO, "Bumblezone: The block under the "+beeBlock+" is not the correct block to teleport to Bumblezone. The config enter says it needs "+requiredBlockString+" under "+beeBlock+".");
							ITextComponent message = new StringTextComponent("�eBumblezone:�f The block under the �6"+beeBlock+"�f is not the correct block to teleport to Bumblezone. The config enter says it needs �6"+requiredBlockString+"�f under �6"+beeBlock+"�f.");
							playerEntity.sendMessage(message);
							return;
						}
					}
					else 
					{
						//failed. the required block config entry is broken
						Bumblezone.LOGGER.log(Level.INFO, "Bumblezone: The required block under beenest config is broken. Please specify a resourcelocation to a real block or leave it blank so that players can teleport to Bumblezone dimension. Currently, the broken config has this in it: "+requiredBlockString);
						ITextComponent message = new StringTextComponent("�eBumblezone:�f The required block under beenest config is broken. Please specify a resourcelocation to a real block or leave it blank so that players can teleport to Bumblezone dimension. Currently, the broken config has this in it: �c"+requiredBlockString);
						playerEntity.sendMessage(message);
						return;
					}
				}
				else {
				    validBelowBlock = true;
				}
				

				//if the pearl hit a beehive and is not in our bee dimension, begin the teleportation.
				if (hitHive && validBelowBlock && playerEntity.dimension != BzDimensionRegistration.bumblezone())
				{
					//Store current dimension and position of hit 

					//grabs the capability attached to player for dimension hopping
					PlayerPositionAndDimension cap = (PlayerPositionAndDimension) playerEntity.getCapability(PAST_POS_AND_DIM).orElseThrow(RuntimeException::new);
					DimensionType destination = BzDimensionRegistration.bumblezone();


					//Store current dim, next dim, and tells player they are in teleporting phase now.
					//
					//We have to do the actual teleporting during the player tick event as if we try and teleport
					//in this event, the game will crash as it would be removing an entity during entity ticking.
					cap.setDestDim(destination);
					cap.setTeleporting(true);
					
					//canceled the original ender pearl's event so other mods don't do stuff.
					event.setCanceled(true);
					
					// remove enderpearl so it cannot teleport us
					pearlEntity.remove(); 
				}
			}
		}
		

		@SubscribeEvent
		public static void playerTick(PlayerTickEvent event)
		{
			//grabs the capability attached to player for dimension hopping
			PlayerEntity playerEntity = event.player;
			
			if(playerEntity.world instanceof ServerWorld)
			{
				PlayerPositionAndDimension cap = (PlayerPositionAndDimension) playerEntity.getCapability(PAST_POS_AND_DIM).orElseThrow(RuntimeException::new);
			
				//teleported by pearl to enter into bumblezone dimension
				if (cap.isTeleporting)
				{
					teleportByPearl(playerEntity, cap);
					reAddPotionEffect(playerEntity);
				}
				//teleported by going out of bounds to leave bumblezone dimension
				else if((playerEntity.getPosY() < -1 || playerEntity.getPosY() > 255) &&
						playerEntity.dimension == BzDimensionRegistration.bumblezone()) 
				{
					playerEntity.fallDistance = 0;
					teleportByOutOfBounds(playerEntity, cap, playerEntity.getPosY() < -1 ? true : false);
					reAddPotionEffect(playerEntity);
				}
			}
				
			//Makes it so player does not get killed for falling into the void
			if(playerEntity.getPosY() < -3 && playerEntity.dimension == BzDimensionRegistration.bumblezone())
			{
				playerEntity.fallDistance = 0;
				playerEntity.setPosition(playerEntity.getPosX(), -3D, playerEntity.getPosZ());
			}
		}
		


		// Fires just before the teleportation to new dimension begins
		@SubscribeEvent
		public static void entityTravelToDimensionEvent(EntityTravelToDimensionEvent event)
		{
			if(event.getEntity() instanceof PlayerEntity)
			{
				// Updates the non-BZ dimension that the player is leaving if going to BZ
				PlayerEntity playerEntity = (PlayerEntity) event.getEntity();
				PlayerPositionAndDimension cap = (PlayerPositionAndDimension) playerEntity.getCapability(PAST_POS_AND_DIM).orElseThrow(RuntimeException::new);
				if(playerEntity.dimension != BzDimensionRegistration.bumblezone())
				{
					cap.setNonBZDim(playerEntity.dimension);
					cap.setNonBZPos(playerEntity.getPositionVec());
					cap.setNonBZPitch(playerEntity.rotationPitch);
					cap.setNonBZYaw(playerEntity.rotationYaw);
				}
			}
		}
	}
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Effects
	
	/**
	 * Temporary fix until Mojang patches the bug that makes potion effect icons disappear when changing dimension.
	 * To fix it ourselves, we remove the effect and re-add it to the player.
	 */
	private static void reAddPotionEffect(PlayerEntity playerEntity) 
	{
		//re-adds potion effects so the icon remains instead of disappearing when changing dimensions due to a bug
		ArrayList<EffectInstance> effectInstanceList = new ArrayList<EffectInstance>(playerEntity.getActivePotionEffects());
		for(int i = effectInstanceList.size() - 1; i >= 0; i--)
		{
			EffectInstance effectInstance = effectInstanceList.get(i);
			if(effectInstance != null) 
			{
				playerEntity.removeActivePotionEffect(effectInstance.getPotion());
				playerEntity.addPotionEffect(
						new EffectInstance(
								effectInstance.getPotion(), 
								effectInstance.getDuration(), 
								effectInstance.getAmplifier(), 
								effectInstance.isAmbient(), 
								effectInstance.doesShowParticles(), 
								effectInstance.isShowIcon()));
			}
		}
	}
	
	
	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Teleporting
	
	
	private static void teleportByOutOfBounds(PlayerEntity playerEntity, PlayerPositionAndDimension cap, boolean checkingUpward)
	{
		//gets the world in the destination dimension
		MinecraftServer minecraftServer = playerEntity.getServer(); // the server itself
		ServerWorld destinationWorld;
		ServerWorld bumblezoneWorld = minecraftServer.getWorld(BzDimensionRegistration.bumblezone());
		
		//Error. This shouldn't be. We aren't leaving the bumblezone to go to the bumblezone. 
		//Go to Overworld instead as default or when config forces Overworld teleport
		if(cap.getNonBZDim() == BzDimensionRegistration.bumblezone() && Bumblezone.BzConfig.forceExitToOverworld.get())
		{
			destinationWorld = minecraftServer.getWorld(DimensionType.OVERWORLD); // go to Overworld
		}
		else 
		{
			destinationWorld = minecraftServer.getWorld(cap.getNonBZDim()); // gets the previous dimension user came from
		}
		
		BlockPos blockpos = new BlockPos(0,0,0);
		BlockPos validBlockPos = null;
		
		if(Bumblezone.BzConfig.teleportationMode.get() == 1 || Bumblezone.BzConfig.teleportationMode.get() == 3 || cap.nonBZPosition == null)
        		//converts the position to get the corresponding position in non-bumblezone dimension
        		blockpos = new BlockPos(
        			Doubles.constrainToRange(playerEntity.getPosition().getX() / destinationWorld.getDimension().getMovementFactor() * bumblezoneWorld.getDimension().getMovementFactor(), -29999936D, 29999936D), 
				playerEntity.getPosition().getY(), 
				Doubles.constrainToRange(playerEntity.getPosition().getZ() / destinationWorld.getDimension().getMovementFactor() * bumblezoneWorld.getDimension().getMovementFactor(), -29999936D, 29999936D));


		if(Bumblezone.BzConfig.teleportationMode.get() != 2)
		    validBlockPos = validPlayerSpawnLocationByBeehive(destinationWorld, blockpos, 48, checkingUpward);
        	
		
		//Gets valid space in other world
		//Won't ever be null
		if(Bumblezone.BzConfig.teleportationMode.get() == 2 || 
		   (Bumblezone.BzConfig.teleportationMode.get() == 3 && validBlockPos == null)) {
		    	//Use cap for position
		    	
		    	//extra null check
		    	if(cap.nonBZPosition == null)
		    	    validBlockPos = blockpos;
		    	else
		    	    validBlockPos = new BlockPos(cap.nonBZPosition);
		    	
		    	
		    	if(destinationWorld.getBlockState(validBlockPos.up()).isSolid()) {
		    	    destinationWorld.setBlockState(validBlockPos, Blocks.AIR.getDefaultState(), 3);
		    	    destinationWorld.setBlockState(validBlockPos.up(), Blocks.AIR.getDefaultState(), 3);
		    	}
		    
		    	//let game know we are gonna teleport player
			ChunkPos chunkpos = new ChunkPos(validBlockPos);
			destinationWorld.getChunkProvider().registerTicket(TicketType.POST_TELEPORT, chunkpos, 1, playerEntity.getEntityId());
			
			((ServerPlayerEntity)playerEntity).teleport(
				destinationWorld, 
				cap.nonBZPosition.getX(), 
				cap.nonBZPosition.getY(), 
				cap.nonBZPosition.getZ(), 
				cap.nonBZYaw, 
				cap.nonBZPitch);
		}
		else {
		    	//use found location
		    
			//let game know we are gonna teleport player
			ChunkPos chunkpos = new ChunkPos(validBlockPos);
			destinationWorld.getChunkProvider().registerTicket(TicketType.POST_TELEPORT, chunkpos, 1, playerEntity.getEntityId());
			
			((ServerPlayerEntity)playerEntity).teleport(
				destinationWorld, 
				validBlockPos.getX() + 0.5D, 
				validBlockPos.getY() + 1, 
				validBlockPos.getZ() + 0.5D, 
				playerEntity.rotationYaw, 
				playerEntity.rotationPitch);
		}
		


		
		//teleportation complete. 
		cap.setTeleporting(false);
	}
	
	private static void teleportByPearl(PlayerEntity playerEntity, PlayerPositionAndDimension cap)
	{
		//gets the world in the destination dimension
		MinecraftServer minecraftServer = playerEntity.getServer(); // the server itself
		ServerWorld originalWorld = minecraftServer.getWorld(playerEntity.dimension);
		ServerWorld bumblezoneWorld = minecraftServer.getWorld(BzDimensionRegistration.bumblezone());

		
		//converts the position to get the corresponding position in bumblezone dimension
		BlockPos blockpos = new BlockPos(
				playerEntity.getPosition().getX() / bumblezoneWorld.getDimension().getMovementFactor() * originalWorld.getDimension().getMovementFactor(), 
				playerEntity.getPosition().getY(), 
				playerEntity.getPosition().getZ() / bumblezoneWorld.getDimension().getMovementFactor() * originalWorld.getDimension().getMovementFactor());
		
		
		//gets valid space in other world
		BlockPos validBlockPos = validPlayerSpawnLocation(bumblezoneWorld, blockpos, 10);

		
		//No valid space found around destination. Begin secondary valid spot algorithms
		if (validBlockPos == null)
		{
			//go down to first solid land with air above.
			validBlockPos = new BlockPos(
					blockpos.getX(),
					BzPlacingUtils.topOfSurfaceBelowHeightThroughWater(bumblezoneWorld, blockpos.getY()+50, 0, blockpos) + 1,
					blockpos.getZ());

			//No solid land was found. Who digs out an entire chunk?!
			if(validBlockPos.getY() == 0)
			{
				validBlockPos = null;
			}
			//checks if spot is not two water blocks with air block able to be reached above
			else if(bumblezoneWorld.getBlockState(validBlockPos).getMaterial() == Material.WATER &&
					bumblezoneWorld.getBlockState(validBlockPos.up()).getMaterial() == Material.WATER)
			{
				BlockPos.Mutable mutable = new BlockPos.Mutable(validBlockPos);

				//moves upward looking for air block while not interrupted by a solid block
				while(mutable.getY() < 255 && !bumblezoneWorld.isAirBlock(mutable) || bumblezoneWorld.getBlockState(mutable).getMaterial() == Material.WATER){
					mutable.move(Direction.UP);
				}
				if(bumblezoneWorld.getBlockState(mutable).getMaterial() != Material.AIR){
					validBlockPos = null; // No air found. Let's not place player here where they could drown
				}
				else{
					validBlockPos = mutable; // Set player to top of water level
				}
			}
			//checks if spot is not a non-solid block with air block above
			else if((!bumblezoneWorld.isAirBlock(validBlockPos) && bumblezoneWorld.getBlockState(validBlockPos).getMaterial() != Material.WATER) &&
					bumblezoneWorld.getBlockState(validBlockPos.up()).getMaterial() != Material.AIR)
			{
				validBlockPos = null;
			}


			//still no valid position, time to force a valid location ourselves
			if(validBlockPos == null) 
			{
				//We are going to spawn player at exact spot of scaled coordinates by placing air at the spot with honeycomb bottom
				//and honeycomb walls to prevent drowning
				//This is the last resort
				bumblezoneWorld.setBlockState(blockpos, Blocks.AIR.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.up(), Blocks.AIR.getDefaultState());

				bumblezoneWorld.setBlockState(blockpos.down(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.up().up(), Blocks.HONEYCOMB_BLOCK.getDefaultState());

				bumblezoneWorld.setBlockState(blockpos.north(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.west(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.east(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.south(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.north().up(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.west().up(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.east().up(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				bumblezoneWorld.setBlockState(blockpos.south().up(), Blocks.HONEYCOMB_BLOCK.getDefaultState());
				validBlockPos = blockpos;
			}

		}
		

		//if player throws pearl at hive and then goes to sleep, they wake up
		if (playerEntity.isSleeping())
		{
			playerEntity.wakeUp();
		}


		//let game know we are gonna teleport player
		ChunkPos chunkpos = new ChunkPos(validBlockPos);
		bumblezoneWorld.getChunkProvider().registerTicket(TicketType.POST_TELEPORT, chunkpos, 1, playerEntity.getEntityId());
		
		((ServerPlayerEntity)playerEntity).teleport(
			bumblezoneWorld, 
			validBlockPos.getX() + 0.5D, 
			validBlockPos.getY(), 
			validBlockPos.getZ() + 0.5D, 
			playerEntity.rotationYaw, 
			playerEntity.rotationPitch);
		
		//teleportation complete. 
		cap.setTeleporting(false);
	}

	
	////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//Util
	
	
	private static BlockPos validPlayerSpawnLocationByBeehive(World world, BlockPos position, int maximumRange, boolean checkingUpward)
	{
		
		// Gets the height of highest block over the area so we aren't checking an 
		// excessive amount of area above that doesn't need checking.
		int maxHeight = 0;
		int halfRange = maximumRange/2;
		BlockPos.Mutable mutableBlockPos = new BlockPos.Mutable(); 
		for(int x = -halfRange; x < halfRange; x++)
		{
			for(int z = -halfRange; z < halfRange; z++)
			{	
				mutableBlockPos.setPos(position.getX() + x, 0, position.getZ() + z);
				if(!world.chunkExists(mutableBlockPos.getX() >> 4, mutableBlockPos.getZ() >> 4))
				{
					//make game generate chunk so we can get max height of blocks in it
					world.getChunk(mutableBlockPos);
				}
				maxHeight = Math.max(maxHeight, world.getHeight(Heightmap.Type.MOTION_BLOCKING, mutableBlockPos.getX(), mutableBlockPos.getZ()));
			}
		}
		maxHeight = Math.min(maxHeight, world.getActualHeight()-1); //cannot place user at roof of other dimension
		
		//snaps the coordinates to chunk origin and then sets height to minimum or maximum based on search direction
		mutableBlockPos.setPos(position.getX(), checkingUpward ? 0 : maxHeight, position.getZ()); 
		

		// scans range from y = 0 to dimension max height for a bee_nest
		// Does it by checking each y layer at a time
		for (; mutableBlockPos.getY() >= 0 && mutableBlockPos.getY() <= maxHeight;) {
		    // only false if config requires us to be above sealevel and we are not.
		    if (!Bumblezone.BzConfig.seaLevelOrHigherExitTeleporting.get() || 
			    mutableBlockPos.getY() > world.getDimension().getSeaLevel()) {
			
			for (int range = 0; range < maximumRange; range++) {
			   
			    int radius = range * range;
			    int nextRadius = (range + 1) * (range + 1);
			    for (int x = 0; x <= range * 2; x++) {
				int x2 = x > range ? -(x - range) : x;

				for (int z = 0; z <= range * 2; z++) {
				    int z2 = z > range ? -(z - range) : x;

				    // checks within the circular ring and not check the same positions multiple times
				    if (x2 * x2 + z2 * z2 >= radius && x2 * x2 + z2 * z2 < nextRadius) {
					mutableBlockPos.setPos(position.getX() + x2, mutableBlockPos.getY(), position.getZ() + z2);

					if (isValidBeeHive(world.getBlockState(mutableBlockPos))) {
					    // A Hive was found, try to find a valid spot next to it
					    BlockPos validSpot = validPlayerSpawnLocation(world, mutableBlockPos, 4);
					    if (validSpot != null) {
						return validSpot;
					    }
					}
				    }
				}
			    }
			}
		    }

		    // move the block pos in the direction it needs to go
		    if (checkingUpward) {
			mutableBlockPos.move(Direction.UP);
		    }
		    else {
			mutableBlockPos.move(Direction.DOWN);
		    }
		}
		
		
		//this mode will not generate a beenest automatically.
		if(Bumblezone.BzConfig.teleportationMode.get() == 3) return null;

		
		// no valid spot was found, generate a hive and spawn us on the highest land
		// This if statement is so we dont get placed on roof of other roofed dimension
		if (maxHeight + 1 < world.getActualHeight()) {
		    maxHeight += 1;
		}
		mutableBlockPos.setPos(position.getX(), 
					BzPlacingUtils.topOfSurfaceBelowHeight(world, maxHeight, -1, position), 
					position.getZ());
		
		if(mutableBlockPos.getY() > 0)
		{
		    	if(Bumblezone.BzConfig.generateBeenest.get())
		    	    world.setBlockState(mutableBlockPos, Blocks.BEE_NEST.getDefaultState());
		    	else if(world.getBlockState(mutableBlockPos).getMaterial() == Material.AIR ||
		    		(!world.getBlockState(mutableBlockPos).getFluidState().isEmpty() &&
			    	 !world.getBlockState(mutableBlockPos).getFluidState().isTagged(FluidTags.WATER)))
		    	    world.setBlockState(mutableBlockPos, Blocks.HONEYCOMB_BLOCK.getDefaultState());
		    	
			world.setBlockState(mutableBlockPos.up(), Blocks.AIR.getDefaultState());
			return mutableBlockPos;
		}
		else
		{
			//No valid spot was found. Just place character on a generate hive at center of height of coordinate 
			//Basically just f*** it at this point lol
			mutableBlockPos.setPos(position.getX(), 
						world.getDimension().getActualHeight()/2, 
						position.getZ());

		    	if(Bumblezone.BzConfig.generateBeenest.get())
		    	    world.setBlockState(mutableBlockPos, Blocks.BEE_NEST.getDefaultState());
		    	else if(world.getBlockState(mutableBlockPos).getMaterial() == Material.AIR ||
		    		(!world.getBlockState(mutableBlockPos).getFluidState().isEmpty() &&
			    	 !world.getBlockState(mutableBlockPos).getFluidState().isTagged(FluidTags.WATER)))
		    	    world.setBlockState(mutableBlockPos, Blocks.HONEYCOMB_BLOCK.getDefaultState());
		    	
			world.setBlockState(mutableBlockPos.up(), Blocks.AIR.getDefaultState());
			return mutableBlockPos;
		}
	}
	
	@Nullable
	private static BlockPos validPlayerSpawnLocation(World world, BlockPos position, int maximumRange)
	{
		//Try to find 2 non-solid spaces around it that the player can spawn at
		int radius = 0;
		int outterRadius = 0;
		int distanceSq = 0;
		BlockPos.Mutable currentPos = new BlockPos.Mutable(position);

		//checks for 2 non-solid blocks with solid block below feet
		//checks outward from center position in both x, y, and z.
		//The x2, y2, and z2 is so it checks at center of the range box instead of the corner.
		for (int range = 0; range < maximumRange; range++){
			radius = range * range;
			outterRadius = (range + 1) * (range + 1);

			for (int y = 0; y <= range * 2; y++){
				int y2 = y > range ? -(y - range) : y;
				
				
				for (int x = 0; x <= range * 2; x++){
					int x2 = x > range ? -(x - range) : x;
					
					
					for (int z = 0; z <= range * 2; z++){
						int z2 = z > range ? -(z - range) : z;
				
						distanceSq = x2 * x2 + z2 * z2 + y2 * y2;
						if (distanceSq >= radius && distanceSq < outterRadius)
						{
							currentPos.setPos(position.add(x2, y2, z2));
							if (world.getBlockState(currentPos.down()).isSolid() && 
								world.getBlockState(currentPos).getMaterial() == Material.AIR && 
								world.getBlockState(currentPos.up()).getMaterial() == Material.AIR)
							{
								//valid space for player is found
								return currentPos;
							}
						}
					}
				}
			}
		}
		
		return null;
	}
	
	private static boolean isValidBeeHive(BlockState block) {
		if(block.getBlock() instanceof BeehiveBlock) {
			if(Bumblezone.BzConfig.allowTeleportationWithModdedBeehives.get() || block.getBlock().getRegistryName().getNamespace().equals("minecraft")) {
				return true;
			}
		}
		
		if(ModChecking.productiveBeesPresent && Bumblezone.BzConfig.allowTeleportationWithModdedBeehives.get()) {
		    if(ProductiveBeesRedirection.PBIsAdvancedBeehiveAbstractBlock(block))
			return true;
		}
		
		return false;
	}
}
