# Waypoint Guide（标记引导）

一个 Minecraft 1.20.1 Forge 模组，用于在地图上添加自定义标记点，并在玩家屏幕上显示引导。

## 功能

- 通过命令添加彩色标记点，支持按距离限制显示
- 屏幕内渲染菱形标记（名称、距离、浮动动画），靠近时放大
- 屏外标记显示边缘箭头指示方向
- 带距离限制的标记在进入/离开可视距离时以 1.4 秒动画淡入淡出
- 准星对准标记时右键可将其移除，带消除动画和粒子效果；右键走专用数据包并由服务端校验
- 标记数据按玩家保存到世界目录的 `waypoints.json`
- 提供 `WaypointDismissEvent`（Forge 事件，服务端触发），可被 KubeJS 服务端脚本监听

## 命令

所有子命令都通过 `<player>` 指定操作对象：给自己操作无需权限，操作其他玩家需要 OP（2 级）。

- `/waypoint add <player> <id> <x> <y> <z> <color> <name>` — 添加标记（无距离限制）
- `/waypoint addnear <player> <id> <x> <y> <z> <color> <distance> <name>` — 添加标记，玩家距其超过 `distance` 格时不可见
- `/waypoint addlock <player> <id> <x> <y> <z> <color> <name>` — 添加不可右键移除的锁定标记
- `/waypoint addlocknear <player> <id> <x> <y> <z> <color> <distance> <name>` — 添加带距离限制且不可右键移除的锁定标记
- `/waypoint remove <player> <id>` — 移除标记
- `/waypoint clear <player>` — 清除该玩家的全部标记
- `/wp` — `/waypoint` 的别名

`<color>` 支持 `red`、`green`、`blue`、`yellow`、`cyan`、`magenta`、`white`、`black`、`orange`、`pink`、`gray`、`purple`，以及 `0xRRGGBB` / `#RRGGBB` 等十六进制写法。

## KubeJS

右键消除标记时，服务端会触发 `WaypointDismissEvent`，可在服务端脚本中监听：

```js
const $WayPoint = Java.loadClass('com.dreamdawn.waypointguide.forge.WaypointDismissEvent')
ForgeEvents.onEvent($WayPoint, event => {
  // event.waypointId / event.player 等字段可用
})
// 建议添加的方式为下面这样 方便其他地方监听调用
const $WayPoint = Java.loadClass('com.dreamdawn.waypointguide.forge.WaypointDismissEvent')
global.WaypointRightClick = (waypointId, callback) => {
  ForgeEvents.onEvent($WayPoint, event => {
    if (event.waypointId === waypointId) {
      callback(event)
    }
  })
}
// 调用示例
WaypointRightClick('123', event => {
  console.log(event)
})
```

## 许可证

本项目采用 [GNU General Public License v3.0](LICENSE) 开源协议。
