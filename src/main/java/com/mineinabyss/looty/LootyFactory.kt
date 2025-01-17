package com.mineinabyss.looty

import com.mineinabyss.geary.ecs.api.engine.Engine
import com.mineinabyss.geary.ecs.api.engine.entity
import com.mineinabyss.geary.ecs.api.entities.GearyEntity
import com.mineinabyss.geary.ecs.api.entities.geary
import com.mineinabyss.geary.ecs.entities.addParent
import com.mineinabyss.geary.ecs.entities.addPrefab
import com.mineinabyss.geary.ecs.entities.children
import com.mineinabyss.geary.ecs.prefab.PrefabKey
import com.mineinabyss.geary.minecraft.access.BukkitAssociations
import com.mineinabyss.geary.minecraft.access.gearyOrNull
import com.mineinabyss.geary.minecraft.store.decodeComponents
import com.mineinabyss.geary.minecraft.store.decodeComponentsFrom
import com.mineinabyss.geary.minecraft.store.encodeComponentsTo
import com.mineinabyss.geary.minecraft.store.encodePrefabs
import com.mineinabyss.idofront.items.editItemMeta
import com.mineinabyss.looty.ecs.components.LootyType
import com.mineinabyss.looty.ecs.components.PlayerInstancedItem
import com.mineinabyss.looty.ecs.components.PlayerSingletonItems
import com.mineinabyss.looty.ecs.components.inventory.SlotType
import com.mineinabyss.looty.ecs.components.itemcontexts.PlayerInventoryContext
import com.mineinabyss.looty.ecs.components.itemcontexts.PlayerSingletonContext
import com.mineinabyss.looty.tracking.gearyOrNull
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.*

object LootyFactory {
    fun createFromPrefab(
        prefabKey: PrefabKey,
    ): ItemStack? {
        val prefab = prefabKey.toEntity() ?: return null
        val item = prefab.get<LootyType>()?.createItem() ?: ItemStack(Material.STICK)
        item.editItemMeta {
            persistentDataContainer.encodePrefabs(listOf(prefabKey))
        }
        return item
    }

    fun loadFromPlayerInventory(
        context: PlayerInventoryContext,
        item: ItemStack? = context.item,
    ): GearyEntity? {
        if (item == null) return null
        if (item.type == Material.AIR) return null
        val gearyPlayer = gearyOrNull(context.holder) ?: return null

        val decoded = item.itemMeta.persistentDataContainer.decodeComponents()

        if (decoded.type.size == 1) {
            val prefab = geary(decoded.type.first())
            if (prefab.has<PlayerInstancedItem>()) {
                return gearyPlayer.getOrSet { PlayerSingletonItems() }
                    .load(prefab.get() ?: error("Prefab has no key"), gearyPlayer) //TODO shouldnt need to pass parent
                    .apply {
                        val added = getOrSet { PlayerSingletonContext(context.holder) }.itemSlots.add(context.slot)
                        if (added) with<LootyType> {
                            //Update the loaded item to match the item defined in LootyType
                            it.item.toItemStack(item)
                        }
                    }
            }
        }

        if (gearyOrNull(item)?.get<PlayerInventoryContext>()?.slot == context.slot) return null

        return Engine.entity {
            addParent(gearyPlayer)
            decodeComponentsFrom(decoded)
            set(context)
            set(item)
            addSlotTypeComponent(this, context)
            // Ensure a UUID is set and actually unique
            val uuid = get<UUID>()?.takeIf { it !in BukkitAssociations } ?: setPersisting(UUID.randomUUID())

            debug("Creating item in slot ${context.slot} and uuid $uuid")
            BukkitAssociations.register(uuid, this)
            encodeComponentsTo(item)
        }
    }

    fun addSlotTypeComponent(entity: GearyEntity, context: PlayerInventoryContext) {
        entity.apply {
            remove<SlotType.Equipped>()
            remove<SlotType.Offhand>()
            remove<SlotType.Held>()

            when (context.slot) {
                in 36..39 -> add<SlotType.Equipped>()
                40 -> add<SlotType.Offhand>()
            }
            if (context.slot == context.inventory.heldItemSlot) add<SlotType.Held>()
        }
    }
}
