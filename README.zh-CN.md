# GAxnbt

[English](README.md)

创作者：Eric_SXR

在游戏内编辑物品、实体、玩家以及 Raw NBT 数据。

## 功能

- 使用 `/xnbt` 调用编辑命令。
- 在 YAML 中配置消息、属性、附魔和槽位。

## 兼容环境

- 插件版本：`1.0.0`（以 `plugin.yml` 为准）。
- 目标服务端：Paper/Purpur 1.20.1，API `1.20`。
- 编译 JDK：17。
- 必需插件：未声明。
- 可选插件：未声明。

## 编译与安装

1. 使用 JDK 17，在仓库根目录运行 `mvn package`。
2. 将 `target/` 中生成的 JAR 复制到服务器的 `plugins/` 目录。
3. 重启服务器，再按需修改插件生成的配置文件。

## 命令与配置

- 注册命令：`/xnbt`。权限和用法见 `src/main/resources/plugin.yml`。
- 主要配置段：`settings`, `help`, `messages`, `enchants`, `attributes`, `attribute-operations`, `slots`。详见 `src/main/resources/config.yml`。
