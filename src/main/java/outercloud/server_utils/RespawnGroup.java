package outercloud.server_utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

import java.util.ArrayList;
import java.util.Random;

public class RespawnGroup {
    private String tag;
    private float delay;
    private int amount;

    private int timer;

    private ArrayList<RegistryKey<World>> worlds = new ArrayList<>();
    private ArrayList<Vec3d> positions = new ArrayList<>();
    private ArrayList<NbtCompound> nbts = new ArrayList<>();

    private ArrayList<Entity> spawnedEntities = new ArrayList<>();

    public RespawnGroup(String tag, float delay, int amount, MinecraftServer server) {
        this.tag = tag;
        this.delay = delay;
        this.amount = amount;
        this.timer = MathHelper.floor(delay * 20);

        for(ServerWorld world : server.getWorlds()) {
            for(Entity entity : world.iterateEntities()) {
                if(!entity.getCommandTags().contains(this.tag)) continue;

                ServerUtils.deselect(entity);

                worlds.add(world.getRegistryKey());
                positions.add(entity.getPos());

                NbtCompound nbt = new NbtCompound();
                nbt.putString("id", Registries.ENTITY_TYPE.getId(entity.getType()).toString());
                entity.writeNbt(nbt);
                nbt.remove("UUID");

                nbts.add(nbt);

                spawnedEntities.add(entity);
            }
        }

        if(nbts.isEmpty()) return;

        while(spawnedEntities.size() < amount) {
            spawnedEntities.add(null);
        }
    }

    public RespawnGroup(String tag, NbtCompound nbt) {
        delay = nbt.getFloat("delay");
        amount = nbt.getInt("amount");

        ServerUtils.LOGGER.info(String.valueOf(delay));
        ServerUtils.LOGGER.info(String.valueOf(amount));

        for(NbtElement element : nbt.getList("datas", NbtElement.COMPOUND_TYPE)) {
            NbtCompound spawnDataNbt = (NbtCompound) element;

            positions.add(new Vec3d(spawnDataNbt.getFloat("x"), spawnDataNbt.getFloat("y"), spawnDataNbt.getFloat("z")));
            worlds.add(RegistryKey.of(RegistryKeys.WORLD, new Identifier(spawnDataNbt.getString("world"))));
            nbts.add((NbtCompound) spawnDataNbt.get("data"));

            ServerUtils.LOGGER.info(String.valueOf(new Vec3d(spawnDataNbt.getFloat("x"), spawnDataNbt.getFloat("y"), spawnDataNbt.getFloat("z"))));
            ServerUtils.LOGGER.info(String.valueOf(RegistryKey.of(RegistryKeys.WORLD, new Identifier(spawnDataNbt.getString("world")))));
            ServerUtils.LOGGER.info(String.valueOf(spawnDataNbt.get("data")));
        }
    }

    public void writeNbt(NbtCompound nbt) {
        NbtCompound data = new NbtCompound();

        data.putFloat("delay", delay);
        data.putInt("amount", amount);

        NbtList spawnDatas = new NbtList();

        for(int index = 0; index < nbts.size(); index++){
            NbtCompound spawnData = new NbtCompound();
            spawnData.putFloat("x", (float) positions.get(index).x);
            spawnData.putFloat("y", (float) positions.get(index).y);
            spawnData.putFloat("z", (float) positions.get(index).z);

            spawnData.putString("world", worlds.get(index).getValue().toString());

            spawnData.put("data", nbts.get(index));

            spawnDatas.add(spawnData);
        }

        data.put("datas", spawnDatas);

        nbt.put(tag, data);
    }

    public void tick(MinecraftServer server) {
        for(int index = 0; index < spawnedEntities.size(); index++) {
            Entity entity = spawnedEntities.get(index);

            if(entity != null && entity.isAlive()) continue;

            timer--;

            if(timer > 0) break;

            Random random = new Random();

            int randomSpawnDataIndex = random.nextInt(nbts.size());

            ServerWorld world = server.getWorld(worlds.get(randomSpawnDataIndex));
            Vec3d position = positions.get(randomSpawnDataIndex);
            NbtCompound nbt = nbts.get(randomSpawnDataIndex);

            Entity newEntity = EntityType.loadEntityWithPassengers(nbt, world, (createdEntity) -> {
                createdEntity.refreshPositionAndAngles(position.x, position.y, position.z, createdEntity.getYaw(), createdEntity.getPitch());

                return createdEntity;
            });

            world.spawnNewEntityAndPassengers(newEntity);

            spawnedEntities.set(index, newEntity);
        }

        if(timer <= 0) timer = MathHelper.floor(delay * 20);
    }

    public void reset(MinecraftServer server) {
        for(int index = 0; index < spawnedEntities.size(); index++) {
            Entity entity = spawnedEntities.get(index);

            if(entity != null && entity.isAlive()) entity.kill();

            Random random = new Random();

            int randomSpawnDataIndex = random.nextInt(nbts.size());

            ServerWorld world = server.getWorld(worlds.get(randomSpawnDataIndex));
            Vec3d position = positions.get(randomSpawnDataIndex);
            NbtCompound nbt = nbts.get(randomSpawnDataIndex);

            Entity newEntity = EntityType.loadEntityWithPassengers(nbt, world, (createdEntity) -> {
                createdEntity.refreshPositionAndAngles(position.x, position.y, position.z, createdEntity.getYaw(), createdEntity.getPitch());

                return createdEntity;
            });

            world.spawnNewEntityAndPassengers(newEntity);

            spawnedEntities.set(index, newEntity);
        }

        timer = MathHelper.floor(delay * 20);
    }

    public String getTag() {
        return tag;
    }
}