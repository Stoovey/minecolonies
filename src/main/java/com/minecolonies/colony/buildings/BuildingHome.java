package com.minecolonies.colony.buildings;

import com.minecolonies.achievements.ModAchievements;
import com.minecolonies.client.gui.WindowHomeBuilding;
import com.minecolonies.colony.CitizenData;
import com.minecolonies.colony.Colony;
import com.minecolonies.colony.ColonyView;
import com.minecolonies.util.ServerUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BuildingHome extends AbstractBuildingHut
{
    private static final String            TAG_RESIDENTS = "residents";
    private static final String            CITIZEN       = "Citizen";
    @Nonnull
    private              List<CitizenData> residents     = new ArrayList<>();

    public BuildingHome(Colony c, BlockPos l)
    {
        super(c, l);
    }

    @Nonnull
    @Override
    public String getSchematicName()
    {
        return CITIZEN;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return 4;
    }

    @Override
    public void readFromNBT(@Nonnull NBTTagCompound compound)
    {
        super.readFromNBT(compound);

        residents.clear();

        int[] residentIds = compound.getIntArray(TAG_RESIDENTS);
        for (int citizenId : residentIds)
        {
            CitizenData citizen = getColony().getCitizen(citizenId);
            if (citizen != null)
            {
                // Bypass addResident (which marks dirty)
                residents.add(citizen);
                citizen.setHomeBuilding(this);
            }
        }
    }

    @Override
    public void writeToNBT(@Nonnull NBTTagCompound compound)
    {
        super.writeToNBT(compound);

        if (!residents.isEmpty())
        {
            @Nonnull int[] residentIds = new int[residents.size()];
            for (int i = 0; i < residents.size(); ++i)
            {
                residentIds[i] = residents.get(i).getId();
            }
            compound.setIntArray(TAG_RESIDENTS, residentIds);
        }
    }

    @Override
    public void setBuildingLevel(int level)
    {
        super.setBuildingLevel(level);
        getColony().calculateMaxCitizens();
    }

    @Override
    public void onDestroyed()
    {
        residents.stream().filter(citizen -> citizen != null).forEach(citizen -> citizen.setHomeBuilding(null));

        super.onDestroyed();
    }

    @Override
    public void removeCitizen(@Nonnull CitizenData citizen)
    {
        if (residents.contains(citizen))
        {
            citizen.setHomeBuilding(null);
            residents.remove(citizen);
        }
    }

    @Override
    public void onWorldTick(@Nonnull TickEvent.WorldTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }

        if (residents.size() < getMaxInhabitants())
        {
            // 'Capture' as many citizens into this house as possible
            addHomelessCitizens();
        }
    }

    @Override
    public int getMaxInhabitants()
    {
        return 2;
    }

    /**
     * Looks for a homeless citizen to add to the current building. Calls
     * {@link #addResident(CitizenData)}
     */
    public void addHomelessCitizens()
    {
        for (@Nonnull CitizenData citizen : getColony().getCitizens().values())
        {
            if (citizen.getHomeBuilding() == null)
            {
                addResident(citizen);

                if (residents.size() >= getMaxInhabitants())
                {
                    break;
                }
            }
        }
    }

    /**
     * Adds the citizen to the building
     *
     * @param citizen Citizen to add
     */
    private void addResident(@Nonnull CitizenData citizen)
    {
        residents.add(citizen);
        citizen.setHomeBuilding(this);

        markDirty();
    }

    @Override
    public void onUpgradeComplete(final int newLevel)
    {
        super.onUpgradeComplete(newLevel);

        @Nullable final EntityPlayer owner = ServerUtils.getPlayerFromUUID(getColony().getPermissions().getOwner());

        if (newLevel == 1)
        {
            owner.addStat(ModAchievements.achievementBuildingColonist);
        }
        if (newLevel >= this.getMaxBuildingLevel())
        {
            owner.addStat(ModAchievements.achievementUpgradeColonistMax);
        }
    }

    @Override
    public void serializeToView(@Nonnull ByteBuf buf)
    {
        super.serializeToView(buf);

        buf.writeInt(residents.size());
        for (@Nonnull CitizenData citizen : residents)
        {
            buf.writeInt(citizen.getId());
        }
    }

    /**
     * Returns whether the citizen has this as home or not
     *
     * @param citizen Citizen to check
     * @return True if citizen lives here, otherwise false
     */
    public boolean hasResident(CitizenData citizen)
    {
        return residents.contains(citizen);
    }

    public static class View extends AbstractBuildingHut.View
    {
        @Nonnull
        private List<Integer> residents = new ArrayList<>();

        public View(ColonyView c, BlockPos l)
        {
            super(c, l);
        }

        @Nonnull
        public List<Integer> getResidents()
        {
            return Collections.unmodifiableList(residents);
        }

        @Nonnull
        public com.blockout.views.Window getWindow()
        {
            return new WindowHomeBuilding(this);
        }

        @Override
        public void deserialize(@Nonnull ByteBuf buf)
        {
            super.deserialize(buf);

            int numResidents = buf.readInt();
            for (int i = 0; i < numResidents; ++i)
            {
                residents.add(buf.readInt());
            }
        }
    }
}
