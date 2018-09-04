package fudge.spatialcrafting.common.command;

import com.google.common.collect.ImmutableList;
import fudge.spatialcrafting.SpatialCrafting;
import fudge.spatialcrafting.common.MCConstants;
import fudge.spatialcrafting.common.crafting.RecipeAddition;
import fudge.spatialcrafting.common.crafting.SpatialRecipe;
import fudge.spatialcrafting.common.tile.TileCrafter;
import fudge.spatialcrafting.common.util.CrafterUtil;
import fudge.spatialcrafting.common.util.Util;
import fudge.spatialcrafting.compat.crafttweaker.CraftTweakerIntegration;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;

public class CommandAddSRecipe extends SCCommand {

    public static final String RECIPES_FILE_NAME = "___generated_spatial_recipes___.zs";
    private static final List<String> ALIASES = ImmutableList.of("addrecipe", "ar");

    private static void addCrTScript(String command) {
        try {
            final String SCRIPTS_DIR_NAME = "scripts";
            final String SC_DIR_NAME = "spatialcrafting";
            final String GENERATED_MESSAGE = "//***** This file was automatically generated by a Spatial Crafting command.  *****\n";

            // Go the the scripts folder
            File scriptsDir = new File(SCRIPTS_DIR_NAME);
            if (scriptsDir.isDirectory()) {
                // Go to the spatialcrafting folder(if it doesn't exist create a new one)
                File scDir = new File(scriptsDir + "/" + SC_DIR_NAME);
                if (!scDir.exists()) {
                    scDir.mkdir();
                }

                // If the file doesn't exist, create a new one.
                Path path = Paths.get(SCRIPTS_DIR_NAME + "/" + SC_DIR_NAME + "/" + RECIPES_FILE_NAME);
                List<String> lines = Collections.singletonList(GENERATED_MESSAGE);
                if (!path.toFile().exists()) {
                    Files.write(path, lines, Charset.forName("UTF-8"));
                }

                writeCommandToFile(command, path);


            } else {
                SpatialCrafting.LOGGER.error("Could not find scripts directory!");
            }

        } catch (IOException e) {
            SpatialCrafting.LOGGER.error("Unexpected error trying to write zenscript script for a spatialcrafting recipe!", e);
        }
    }

    private static void writeCommandToFile(String command, Path path) {
        try {
            Files.write(path, command.getBytes(), StandardOpenOption.APPEND);
        } catch (IOException e) {
            SpatialCrafting.LOGGER.error("Unexpected error trying to add text to a zenscript script for a spatialcrafting recipe via acommand!",
                    e);
        }
    }

    private static boolean isValidRecipe(ItemStack[][][] input, ItemStack output, @Nonnull EntityPlayerMP player) {

        // If the input is just air
        if (Util.arrEqualsObj(input, Items.AIR, (itemStack, air) -> itemStack.getItem().equals(air))) {
            player.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.empty_crafter", 0));
            return false;
        }

        // If the player is holding nothing, and the output is just air
        if (output.getItem().equals(Items.AIR)) {
            player.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.empty_hand", 0));
            return false;
        }

        return true;


    }

    @Override
    @Nonnull
    public List<String> getAliases() {
        return ALIASES;
    }

    @Override
    @Nonnull
    public String getName() {
        return "Add Spatial Recipe";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/sc addrecipe [exact/wildcard/oredict]";
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String[] words) {

        // Only works in single player
        if (server.isDedicatedServer()) {
            sender.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.dedis_only", 0));
            return;
        }

        TileCrafter crafter = CrafterUtil.getClosestMasterBlock(sender.getEntityWorld(), sender.getPosition());

        try {
            // Get output from player's hand
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            ItemStack output = player.getHeldItem(EnumHand.MAIN_HAND);

            if (crafter != null) {

                ItemStack[][][] input = crafter.getHologramInvArr();

                if (isValidRecipe(input, output, player)) {

                    // Default
                    RecipeAddition recipeAdditionType = RecipeAddition.WILDCARD;

                    // Do different recipe additions depending on the input
                    if (words.length >= 2) {
                        switch (words[1].toLowerCase()) {
                            case "exact":
                            case "ex":
                                recipeAdditionType = RecipeAddition.EXACT;
                                break;
                            case "wildcard":
                            case "*":
                                recipeAdditionType = RecipeAddition.WILDCARD;
                                break;
                            case "oredict":
                            case "od":
                                recipeAdditionType = RecipeAddition.OREDICT;
                                break;
                            default:
                                sender.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.invalid_argument", 0));
                                return;

                        }
                    }

                    SpatialRecipe recipe = SpatialRecipe.getRecipeFromItemStacks(input, output, recipeAdditionType);

                    // If the user did oredict and there are too many oredicts we face a problem (recipe will be null)
                    if (recipeAdditionType == RecipeAddition.OREDICT && recipe == null) {
                        sender.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.too_many_oredicts", 0));
                        return;
                    }

                    // Writes some code in ZS that adds the corresponding recipe. Who needs programmers in our day and age?
                    String command = "mods.spatialcrafting.addRecipe(" + recipe.toFormattedString() + ",\t" + CraftTweakerIntegration.itemStackToCTString(
                            output) + ");\n\n";

                    if (SpatialRecipe.noRecipeConflict(recipe, sender)) {
                        addCrTScript(command);
                        SpatialRecipe.addRecipe(recipe);


                        if (output.getCount() == 1) {
                            sender.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.success",
                                    output.getDisplayName()));
                        } else {
                            sender.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.success_num",
                                    output.getCount(),
                                    output.getDisplayName()));
                        }
                    }
                }


            } else {
                sender.sendMessage(new TextComponentTranslation("commands.spatialcrafting.add_recipe.no_crafters", 0));
            }


        } catch (PlayerNotFoundException e) {
            SpatialCrafting.LOGGER.error("You're not supposed to use this with a command block!", e);
        }


    }

    @Override
    public int getRequiredPermissionLevel() {
        return MCConstants.HIGHEST;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return sender.canUseCommand(this.getRequiredPermissionLevel(), this.getName());
    }


    @Override
    public String description() {
        return "commands.spatialcrafting.add_recipe.description";
    }

    @Override
    int minArgs() {
        return 0;
    }

    @Override
    int maxArgs() {
        return 1;
    }
}
