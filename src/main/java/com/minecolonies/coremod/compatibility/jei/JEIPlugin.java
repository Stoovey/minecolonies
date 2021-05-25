package com.minecolonies.coremod.compatibility.jei;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.buildings.modules.ICraftingBuildingModule;
import com.minecolonies.api.colony.buildings.registry.BuildingEntry;
import com.minecolonies.api.colony.jobs.IJob;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.crafting.CompostRecipe;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.Constants;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.coremod.colony.crafting.CustomRecipesReloadedEvent;
import com.minecolonies.coremod.compatibility.jei.transfer.CraftingGuiHandler;
import com.minecolonies.coremod.compatibility.jei.transfer.FurnaceCraftingGuiHandler;
import com.minecolonies.coremod.compatibility.jei.transfer.PrivateCraftingTeachingTransferHandler;
import com.minecolonies.coremod.compatibility.jei.transfer.PrivateSmeltingTeachingTransferHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.constants.VanillaRecipeCategoryUid;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.*;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

@mezz.jei.api.JeiPlugin
public class JEIPlugin implements IModPlugin
{
    @NotNull
    @Override
    public ResourceLocation getPluginUid()
    {
        return new ResourceLocation(Constants.MOD_ID);
    }

    private final List<GenericRecipeCategory> categories = new ArrayList<>();
    private boolean recipesLoaded;

    @Override
    public void registerCategories(@NotNull final IRecipeCategoryRegistration registration)
    {
        final IJeiHelpers jeiHelpers = registration.getJeiHelpers();
        final IGuiHelper guiHelper = jeiHelpers.getGuiHelper();

        registration.addRecipeCategories(new CompostRecipeCategory(guiHelper));

        categories.clear();
        for (final BuildingEntry building : IMinecoloniesAPI.getInstance().getBuildingRegistry())
        {
            building.getModuleProducers().stream()
                    .map(Supplier::get)
                    .filter(m -> m instanceof ICraftingBuildingModule)
                    .map(m -> (ICraftingBuildingModule) m)
                    .forEach(crafting ->
                    {
                        final IJob<?> job = crafting.getCraftingJob();
                        if (job != null)
                        {
                            final GenericRecipeCategory category = new GenericRecipeCategory(building, job, crafting, guiHelper);
                            categories.add(category);
                            registration.addRecipeCategories(category);
                        }
                    });
        }
    }

    @Override
    public void registerRecipes(@NotNull final IRecipeRegistration registration)
    {
        registration.addIngredientInfo(new ItemStack(ModBlocks.blockHutComposter.asItem()), VanillaTypes.ITEM, TranslationConstants.COM_MINECOLONIES_JEI_PREFIX + ModJobs.COMPOSTER_ID.getPath());

        if (!Minecraft.getInstance().isIntegratedServerRunning())
        {
            // if we're not on an integrated server, we're on a dedicated server, and that
            // means that the CustomRecipes are not loaded yet, so we need to wait until
            // later before we can populate the recipes.
            //
            // TODO this whole drama could probably go away if we loaded the CustomRecipes into
            //      the vanilla RecipeManager instead (and then they'd get automatically synced
            //      too) -- but that will probably have to wait for 1.17 since it would break all
            //      the datapacks.
            recipesLoaded = false;
            return;
        }

        populateRecipes(registration::addRecipes);
        recipesLoaded = true;
    }

    private void populateRecipes(@NotNull final BiConsumer<Collection<?>, ResourceLocation> registrar)
    {
        registrar.accept(CompostRecipeCategory.findRecipes(), CompostRecipe.ID);

        for (final GenericRecipeCategory category : this.categories)
        {
            try
            {
                registrar.accept(category.findRecipes(), category.getUid());
            }
            catch (Exception e)
            {
                Log.getLogger().error("Failed to process recipes for " + category.getTitle(), e);
            }
        }
    }

    @Override
    public void registerRecipeCatalysts(@NotNull final IRecipeCatalystRegistration registration)
    {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.blockBarrel), CompostRecipe.ID);
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.blockHutComposter), CompostRecipe.ID);

        for (final GenericRecipeCategory category : this.categories)
        {
            registration.addRecipeCatalyst(category.getCatalyst(), category.getUid());
        }
    }

    @Override
    public void registerRecipeTransferHandlers(@NotNull final IRecipeTransferRegistration registration)
    {
        registration.addRecipeTransferHandler(new PrivateCraftingTeachingTransferHandler(registration.getTransferHelper()), VanillaRecipeCategoryUid.CRAFTING);
        registration.addRecipeTransferHandler(new PrivateSmeltingTeachingTransferHandler(registration.getTransferHelper()), VanillaRecipeCategoryUid.FURNACE);
    }

    @Override
    public void registerGuiHandlers(@NotNull final IGuiHandlerRegistration registration)
    {
        new CraftingGuiHandler(this.categories).register(registration);
        new FurnaceCraftingGuiHandler(this.categories).register(registration);
    }

    @Override
    public void onRuntimeAvailable(@NotNull final IJeiRuntime jeiRuntime)
    {
        if (!recipesLoaded)
        {
            final WeakReference<JEIPlugin> weakPlugin = new WeakReference<>(this);
            final WeakReference<IJeiRuntime> weakRuntime = new WeakReference<>(jeiRuntime);

            MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
                    CustomRecipesReloadedEvent.class, event ->
            {
                // if the recipes are still not loaded at this point, it means we're on a
                // dedicated server, and we had to wait for the custom recipes to be populated
                // before we could load the JEI recipes properly.  this uses a deprecated API
                // in JEI but it seems like the only way to get things to actually work.
                if (!recipesLoaded)
                {
                    final JEIPlugin self = weakPlugin.get();
                    final IJeiRuntime runtime = weakRuntime.get();
                    if (self != null && runtime != null)
                    {
                        final IRecipeManager jeiManager = runtime.getRecipeManager();
                        self.populateRecipes((list, uid) ->
                        {
                            for (final Object recipe : list)
                            {
                                //noinspection deprecation
                                jeiManager.addRecipe(recipe, uid);
                            }
                        });
                    }
                    recipesLoaded = true;
                }
            });
        }
    }
}
