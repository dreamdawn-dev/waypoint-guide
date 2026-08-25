package com.dreamdawn.waypointguide;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 指令注册：/waypoint add/addnear/addlock/remove/clear
 * 所有子命令都通过 <player> 参数指定操作对象；
 * 给自己操作无需权限，操作其他玩家需要 OP（2 级）
 */
public final class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> ID_SUGGESTIONS = (ctx, builder) -> {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        List<Waypoint> list = WaypointManager.getWaypoints(player.getUUID());
        synchronized (list) {
            for (Waypoint wp : list) {
                builder.suggest(wp.getId());
            }
        }
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> COLOR_SUGGESTIONS = (ctx, builder) ->
            SharedSuggestionProvider.suggest(ColorParser.getColorNames(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("waypoint")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("color", StringArgumentType.word())
                                                                                .suggests(COLOR_SUGGESTIONS)
                                                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                                        .executes(ctx -> addWaypoint(
                                                                                                ctx.getSource(),
                                                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                                                StringArgumentType.getString(ctx, "id"),
                                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                                                                DoubleArgumentType.getDouble(ctx, "z"),
                                                                                                StringArgumentType.getString(ctx, "color")
                                                                                        ))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        )
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .suggests(ID_SUGGESTIONS)
                                                .executes(ctx -> removeWaypoint(
                                                        ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "id")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("clear")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> clearWaypoints(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")
                                        ))
                                )
                        )
                        .then(Commands.literal("addlock")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("color", StringArgumentType.word())
                                                                                .suggests(COLOR_SUGGESTIONS)
                                                                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                                        .executes(ctx -> addLockWaypoint(
                                                                                                ctx.getSource(),
                                                                                                EntityArgument.getPlayer(ctx, "player"),
                                                                                                StringArgumentType.getString(ctx, "id"),
                                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                                                                DoubleArgumentType.getDouble(ctx, "z"),
                                                                                                StringArgumentType.getString(ctx, "color")
                                                                                        ))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        )
                        .then(Commands.literal("addnear")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("color", StringArgumentType.word())
                                                                                .suggests(COLOR_SUGGESTIONS)
                                                                                .then(Commands.argument("distance", DoubleArgumentType.doubleArg())
                                                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                                                .executes(ctx -> addNearWaypoint(
                                                                                                        ctx.getSource(),
                                                                                                        EntityArgument.getPlayer(ctx, "player"),
                                                                                                        StringArgumentType.getString(ctx, "id"),
                                                                                                        StringArgumentType.getString(ctx, "name"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "z"),
                                                                                                        StringArgumentType.getString(ctx, "color"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "distance")
                                                                                                ))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        )
                        )
                        .then(Commands.literal("addlocknear")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                                                        .then(Commands.argument("color", StringArgumentType.word())
                                                                                .suggests(COLOR_SUGGESTIONS)
                                                                                .then(Commands.argument("distance", DoubleArgumentType.doubleArg())
                                                                                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                                                                                .executes(ctx -> addLockNearWaypoint(
                                                                                                        ctx.getSource(),
                                                                                                        EntityArgument.getPlayer(ctx, "player"),
                                                                                                        StringArgumentType.getString(ctx, "id"),
                                                                                                        StringArgumentType.getString(ctx, "name"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "x"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "y"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "z"),
                                                                                                        StringArgumentType.getString(ctx, "color"),
                                                                                                        DoubleArgumentType.getDouble(ctx, "distance")
                                                                                                ))
                                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                )
                        )
                        )
        );

        dispatcher.register(
                Commands.literal("wp")
                        .redirect(dispatcher.getRoot().getChild("waypoint"))
        );
    }

    /** 给自己操作无需权限；给其他玩家操作需要 OP（2 级） */
    private static boolean checkTargetPermission(CommandSourceStack source, ServerPlayer target) {
        if (source.hasPermission(2)) return true;
        ServerPlayer executor = source.getPlayer();
        return executor != null && executor.getUUID().equals(target.getUUID());
    }

    private static int addWaypoint(CommandSourceStack source, ServerPlayer target, String id, String name,
                                   double x, double y, double z, String colorStr) {
        if (!checkTargetPermission(source, target)) {
            source.sendFailure(Component.literal("你没有权限为其他玩家添加标记"));
            return 0;
        }

        Integer color = ColorParser.parse(colorStr);
        if (color == null) {
            source.sendFailure(Component.literal("无效的颜色: " + colorStr));
            return 0;
        }

        String dimension = target.level().dimension().location().toString();
        Waypoint wp = new Waypoint(id, name, x, y, z, color, 0xFFFFFFFF, dimension);

        if (WaypointManager.addWaypoint(target, wp)) {
            ServerPlayer executor = source.getPlayer();
            if (executor != null && executor.isCreative()) {
                source.sendSuccess(() -> Component.literal("已为 " + target.getName().getString() + " 添加标记: " + name + " (" + id + ")"), false);
            }
            return 1;
        } else {
            source.sendFailure(Component.literal("标记ID已存在: " + id));
            return 0;
        }
    }

    private static int addLockWaypoint(CommandSourceStack source, ServerPlayer target, String id, String name,
                                       double x, double y, double z, String colorStr) {
        if (!checkTargetPermission(source, target)) {
            source.sendFailure(Component.literal("你没有权限为其他玩家添加标记"));
            return 0;
        }

        Integer color = ColorParser.parse(colorStr);
        if (color == null) {
            source.sendFailure(Component.literal("无效的颜色: " + colorStr));
            return 0;
        }

        String dimension = target.level().dimension().location().toString();
        Waypoint wp = new Waypoint(id, name, x, y, z, color, 0xFFFFFFFF, dimension, false);

        if (WaypointManager.addWaypoint(target, wp)) {
            ServerPlayer executor = source.getPlayer();
            if (executor != null && executor.isCreative()) {
                source.sendSuccess(() -> Component.literal("已为 " + target.getName().getString() + " 添加锁定标记: " + name + " (" + id + ")"), false);
            }
            return 1;
        } else {
            source.sendFailure(Component.literal("标记ID已存在: " + id));
            return 0;
        }
    }

    private static int addNearWaypoint(CommandSourceStack source, ServerPlayer target, String id, String name,
                                       double x, double y, double z, String colorStr, double distance) {
        if (!checkTargetPermission(source, target)) {
            source.sendFailure(Component.literal("你没有权限为其他玩家添加标记"));
            return 0;
        }

        if (distance <= 0) {
            source.sendFailure(Component.literal("可见距离必须大于 0"));
            return 0;
        }

        Integer color = ColorParser.parse(colorStr);
        if (color == null) {
            source.sendFailure(Component.literal("无效的颜色: " + colorStr));
            return 0;
        }

        String dimension = target.level().dimension().location().toString();
        Waypoint wp = new Waypoint(id, name, x, y, z, color, 0xFFFFFFFF, dimension, true, distance);

        if (WaypointManager.addWaypoint(target, wp)) {
            ServerPlayer executor = source.getPlayer();
            if (executor != null && executor.isCreative()) {
                source.sendSuccess(() -> Component.literal("已为 " + target.getName().getString() + " 添加附近标记: " + name + " (" + id + ")，可见距离 " + String.format("%.0f", distance) + " 格"), false);
            }
            return 1;
        } else {
            source.sendFailure(Component.literal("标记ID已存在: " + id));
            return 0;
        }
    }

    private static int addLockNearWaypoint(CommandSourceStack source, ServerPlayer target, String id, String name,
                                           double x, double y, double z, String colorStr, double distance) {
        if (!checkTargetPermission(source, target)) {
            source.sendFailure(Component.literal("你没有权限为其他玩家添加标记"));
            return 0;
        }

        if (distance <= 0) {
            source.sendFailure(Component.literal("可见距离必须大于 0"));
            return 0;
        }

        Integer color = ColorParser.parse(colorStr);
        if (color == null) {
            source.sendFailure(Component.literal("无效的颜色: " + colorStr));
            return 0;
        }

        String dimension = target.level().dimension().location().toString();
        Waypoint wp = new Waypoint(id, name, x, y, z, color, 0xFFFFFFFF, dimension, false, distance);

        if (WaypointManager.addWaypoint(target, wp)) {
            ServerPlayer executor = source.getPlayer();
            if (executor != null && executor.isCreative()) {
                source.sendSuccess(() -> Component.literal("已为 " + target.getName().getString() + " 添加锁定附近标记: " + name + " (" + id + ")，可见距离 " + String.format("%.0f", distance) + " 格"), false);
            }
            return 1;
        } else {
            source.sendFailure(Component.literal("标记ID已存在: " + id));
            return 0;
        }
    }

    private static int removeWaypoint(CommandSourceStack source, ServerPlayer target, String id) {
        if (!checkTargetPermission(source, target)) {
            source.sendFailure(Component.literal("你没有权限操作其他玩家的标记"));
            return 0;
        }

        if (WaypointManager.removeWaypoint(target, id)) {
            ServerPlayer executor = source.getPlayer();
            if (executor != null && executor.isCreative()) {
                source.sendSuccess(() -> Component.literal("已移除 " + target.getName().getString() + " 的标记: " + id), false);
            }
            return 1;
        } else {
            source.sendFailure(Component.literal("未找到标记: " + id));
            return 0;
        }
    }

    private static int clearWaypoints(CommandSourceStack source, ServerPlayer target) {
        if (!checkTargetPermission(source, target)) {
            source.sendFailure(Component.literal("你没有权限操作其他玩家的标记"));
            return 0;
        }

        WaypointManager.clearWaypoints(target);
        ServerPlayer executor = source.getPlayer();
        if (executor != null && executor.isCreative()) {
            source.sendSuccess(() -> Component.literal("已清除 " + target.getName().getString() + " 的所有标记"), false);
        }
        return 1;
    }
}
