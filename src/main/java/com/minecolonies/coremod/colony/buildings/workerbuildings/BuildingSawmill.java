package com.minecolonies.coremod.colony.buildings.workerbuildings;

import com.ldtteam.blockout.views.Window;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.IColonyView;
import com.minecolonies.api.colony.buildings.ModBuildings;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.api.crafting.IRecipeStorage;
import com.minecolonies.api.crafting.ItemStorage;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.ItemStackUtils;
import com.minecolonies.coremod.client.gui.huts.WindowHutWorkerModulePlaceholder;
import com.minecolonies.coremod.colony.buildings.AbstractBuildingCrafter;
import com.minecolonies.coremod.colony.jobs.JobSawmill;
import net.minecraft.item.ItemStack;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

import static com.minecolonies.api.util.constant.BuildingConstants.CONST_DEFAULT_MAX_BUILDING_LEVEL;

/**
 * Class of the sawmill building.
 */
public class BuildingSawmill extends AbstractBuildingCrafter
{
    /**
     * Description string of the building.
     */
    private static final String SAWMILL = "sawmill";

    /**
     * The min percentage something has to have out of wood to be craftable by this worker.
     */
    private static final double MIN_PERCENTAGE_TO_CRAFT = 0.75;

    /**
     * Instantiates a new sawmill building.
     *
     * @param c the colony.
     * @param l the location
     */
    public BuildingSawmill(final IColony c, final BlockPos l)
    {
        super(c, l);
    }

    @NotNull
    @Override
    public String getSchematicName()
    {
        return SAWMILL;
    }

    @Override
    public int getMaxBuildingLevel()
    {
        return CONST_DEFAULT_MAX_BUILDING_LEVEL;
    }

    @NotNull
    @Override
    public IJob<?> createJob(final ICitizenData citizen)
    {
        return new JobSawmill(citizen);
    }

    @NotNull
    @Override
    public String getJobName()
    {
        return SAWMILL;
    }

    @NotNull
    @Override
    public Skill getPrimarySkill()
    {
        return Skill.Knowledge;
    }

    @NotNull
    @Override
    public Skill getSecondarySkill()
    {
        return Skill.Dexterity;
    }

    @Override
    public boolean canRecipeBeAdded(final IToken<?> token)
    {
        Optional<Boolean> isRecipeAllowed;

        if (!super.canRecipeBeAdded(token))
        {
            return false;
        }

        isRecipeAllowed = super.canRecipeBeAddedBasedOnTags(token);
        if (isRecipeAllowed.isPresent())
        {
            return isRecipeAllowed.get();
        }
        else
        {
            // Additional recipe rules

            final IRecipeStorage storage = IColonyManager.getInstance().getRecipeManager().getRecipes().get(token);

            double amountOfValidBlocks = 0;
            double blocks = 0;
            for (final ItemStorage inputItem : storage.getInput())
            {
                if (!inputItem.isEmpty())
                {
                    if (inputItem.getItem().isIn(ItemTags.PLANKS) || inputItem.getItem().isIn(ItemTags.LOGS))
                    {
                        amountOfValidBlocks++;
                        continue;
                    }
                    for (final ResourceLocation tag : inputItem.getItem().getTags())
                    {
                        if (tag.getPath().contains("wood"))
                        {
                            amountOfValidBlocks++;
                            break;
                        }
                    }
                    blocks++;
                }
            }

            return amountOfValidBlocks > 0 && amountOfValidBlocks / blocks > MIN_PERCENTAGE_TO_CRAFT;

            // End Additional recipe rules
        }
    }

    @Override
    public BuildingEntry getBuildingRegistryEntry()
    {
        return ModBuildings.sawmill;
    }

    /**
     * Sawmill View.
     */
    public static class View extends AbstractBuildingCrafter.View
    {

        /**
         * Instantiate the sawmill view.
         *
         * @param c the colonyview to put it in
         * @param l the positon
         */
        public View(final IColonyView c, final BlockPos l)
        {
            super(c, l);
        }

        @NotNull
        @Override
        public Window getWindow()
        {
            return new WindowHutWorkerModulePlaceholder<>(this, SAWMILL);
        }
    }
}
