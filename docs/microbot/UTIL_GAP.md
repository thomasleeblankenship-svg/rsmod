# `util/**` API gap: what is left and who takes it

Measured 2026-10-24 by Claude 1. Upstream Microbot (`a2114401`) `util/**` has 3,146 public
methods; 1,118 are present here with the same class, name and parameter count (35.5%).
That leaves **2028 missing** by the same measure (1054 reads, 974 actions,
split by name: `get`/`is`/`has`/`can`/`count`/`find`/... are reads).

**Only 195 of the 2028 are called by any Hub plugin** (94 reads, 101 actions);
72 are called by three or more. The rest are internals, desktop-only
helpers or code paths no Hub plugin reaches.

Caveats:
- "Present" needs the same path, class, name and parameter count. Reimplementations under another
  class (`shortestpath/WalkerPathReads` for `Rs2PathApi`, legacy `util/npc/Rs2NpcModel` for parts of
  `Rs2ActorModel`) count as missing.
- "Plugins" matches the class and method name in Hub sources, not the parameter count. An overload
  counts as used when any same-named method is called, so a high count on an overload can be
  callers of a variant that already exists. Check before porting.
- Policy still applies: no anti-ban behaviour, humanised input, auto-login, mules or hopping away
  from other players. Such members stay inert or out.

Largest missing classes (all methods, used or not): `Rs2Bank` 69, `Rs2Walker` 69, `Rs2HuntKit` 63, `Rs2GameObject` 60, `Rs2PathApi` 51, `Rs2Inventory` 39, `Rs2LeaguesTransport` 37, `Rs2Death` 37, `Rs2Player` 37, `DoorAttemptLedger` 36, `WebWalkLog` 31, `Rs2Npc` 26.

## Proposal (Claude 1 to Claude 2; see the request in `COORDINATION.md`)

1. Scope: port the 195 Hub-used methods first. Port the rest only when a plugin or the
   user needs it.
2. Owners: reads go to Claude 2, actions to Claude 1, as before. Where a name is ambiguous, the
   one who claims it first takes it.
3. Order (as agreed with Claude 2): by the Hub compile sweep's exact missing signatures (javac `cannot find symbol` / `no suitable method`), most plugins first; the tables below are by name only, so their top rows include overloads that already exist. Batches are in batches of about 10. Each batch is claimed
   in `COORDINATION.md` with the method names, so nothing is done twice.
4. After each batch, Claude 1 re-runs the Hub compile sweep and logs the new error counts.

## Reads used by Hub plugins (94)

| Plugins | Method | File |
| ---: | --- | --- |
| 69 | `Rs2Player.isMoving` (1 params) | `util/player/Rs2Player.java` |
| 68 | `Rs2Inventory.isFull` (1 params) | `util/inventory/Rs2Inventory.java` |
| 65 | `Rs2Equipment.isWearing` (0 params) | `util/equipment/Rs2Equipment.java` |
| 65 | `Rs2Equipment.isWearing` (3 params) | `util/equipment/Rs2Equipment.java` |
| 65 | `Rs2Equipment.isWearing` (4 params) | `util/equipment/Rs2Equipment.java` |
| 37 | `Rs2Inventory.get` (2 params) | `util/inventory/Rs2Inventory.java` |
| 37 | `Rs2Inventory.get` (3 params) | `util/inventory/Rs2Inventory.java` |
| 16 | `Rs2Bank.getNearestBank` (2 params) | `util/bank/Rs2Bank.java` |
| 15 | `Rs2Equipment.get` (0 params) | `util/equipment/Rs2Equipment.java` |
| 15 | `Rs2Equipment.get` (2 params) | `util/equipment/Rs2Equipment.java` |
| 11 | `Rs2Inventory.all` (1 params) | `util/inventory/Rs2Inventory.java` |
| 4 | `Rs2Walker.canReach` (2 params) | `util/walker/Rs2Walker.java` |
| 4 | `Rs2Walker.canReach` (3 params) | `util/walker/Rs2Walker.java` |
| 4 | `Rs2Walker.canReach` (4 params) | `util/walker/Rs2Walker.java` |
| 4 | `Rs2Walker.canReach` (5 params) | `util/walker/Rs2Walker.java` |
| 4 | `Rs2Walker.canReach` (6 params) | `util/walker/Rs2Walker.java` |
| 3 | `Rs2Equipment.all` (1 params) | `util/equipment/Rs2Equipment.java` |
| 3 | `Rs2Equipment.all` (2 params) | `util/equipment/Rs2Equipment.java` |
| 3 | `Rs2ExplorersRing.hasCharges` (0 params) | `util/item/Rs2ExplorersRing.java` |
| 3 | `Rs2ExplorersRing.hasRing` (0 params) | `util/item/Rs2ExplorersRing.java` |
| 3 | `Rs2Gembag.hasGemBag` (0 params) | `util/inventory/Rs2Gembag.java` |
| 3 | `Rs2Gembag.isUnknown` (0 params) | `util/inventory/Rs2Gembag.java` |
| 3 | `Rs2Prayer.toggleQuickPrayer` (0 params) | `util/prayer/Rs2Prayer.java` |
| 3 | `Rs2Tile.getNearestWalkableTile` (1 params) | `util/tile/Rs2Tile.java` |
| 3 | `Rs2UiHelper.getClickingPoint` (2 params) | `util/misc/Rs2UiHelper.java` |
| 3 | `Rs2UiHelper.getClickingPoint` (3 params) | `util/misc/Rs2UiHelper.java` |
| 2 | `Rs2Bank.getCurrentTab` (0 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Bank.getItemWidget` (1 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Combat.getAttackRange` (0 params) | `util/combat/Rs2Combat.java` |
| 2 | `Rs2Combat.getAttackRange` (2 params) | `util/combat/Rs2Combat.java` |
| 2 | `Rs2GameObject.canReach` (1 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.canReach` (3 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.canReach` (5 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getGameObject` (1 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getGameObject` (2 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getGameObject` (3 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getGameObject` (4 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getTileObject` (1 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getTileObject` (2 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getTileObject` (3 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GameObject.getTileObject` (4 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2Gembag.isGemBagOpen` (0 params) | `util/inventory/Rs2Gembag.java` |
| 2 | `Rs2GroundItem.getGroundItems` (0 params) | `util/grounditem/Rs2GroundItem.java` |
| 2 | `Rs2MiniMap.isPointInsideMinimap` (1 params) | `util/walker/Rs2MiniMap.java` |
| 2 | `Rs2Player.getRs2WorldPoint` (0 params) | `util/player/Rs2Player.java` |
| 2 | `Rs2RunePouch.contains` (1 params) | `util/inventory/Rs2RunePouch.java` |
| 2 | `Rs2RunePouch.contains` (2 params) | `util/inventory/Rs2RunePouch.java` |
| 2 | `Rs2RunePouch.contains` (3 params) | `util/inventory/Rs2RunePouch.java` |
| 2 | `Rs2Tile.getReachableTilesFromTile` (1 params) | `util/tile/Rs2Tile.java` |
| 2 | `Rs2Tile.getReachableTilesFromTile` (2 params) | `util/tile/Rs2Tile.java` |
| 2 | `Rs2Tile.getReachableTilesFromTile` (3 params) | `util/tile/Rs2Tile.java` |
| 2 | `Rs2Tile.getTile` (2 params) | `util/tile/Rs2Tile.java` |
| 2 | `Rs2UiHelper.getDefaultRectangle` (0 params) | `util/misc/Rs2UiHelper.java` |
| 2 | `Rs2Widget.findWidgetsWithAction` (3 params) | `util/widget/Rs2Widget.java` |
| 2 | `Rs2Widget.findWidgetsWithAction` (4 params) | `util/widget/Rs2Widget.java` |
| 1 | `Rs2Bank.get` (1 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.getBankLiveEpoch` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.getItemBounds` (1 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.getItemTabForBankItem` (1 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.getItems` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.getTabs` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.hasRunePouch` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.isBankPinWidgetVisible` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.isTabOpen` (1 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.toggleAllLocks` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.toggleItemLock` (2 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Camera.isTrackingNpc` (0 params) | `util/camera/Rs2Camera.java` |
| 1 | `Rs2Death.getDeathsOfficeItems` (0 params) | `util/death/Rs2Death.java` |
| 1 | `Rs2DepositBox.getDepositBoxBounds` (0 params) | `util/depositbox/Rs2DepositBox.java` |
| 1 | `Rs2GameObject.findObjectById` (2 params) | `util/gameobject/Rs2GameObject.java` |
| 1 | `Rs2GameObject.getObjectComposition` (1 params) | `util/gameobject/Rs2GameObject.java` |
| 1 | `Rs2GameObject.getWallObjects` (0 params) | `util/gameobject/Rs2GameObject.java` |
| 1 | `Rs2GameObject.getWallObjects` (1 params) | `util/gameobject/Rs2GameObject.java` |
| 1 | `Rs2GameObject.getWallObjects` (2 params) | `util/gameobject/Rs2GameObject.java` |
| 1 | `Rs2GameObject.getWallObjects` (3 params) | `util/gameobject/Rs2GameObject.java` |
| 1 | `Rs2Gembag.isAnyGemSlotFull` (0 params) | `util/inventory/Rs2Gembag.java` |
| 1 | `Rs2GroundItem.getAll` (1 params) | `util/grounditem/Rs2GroundItem.java` |
| 1 | `Rs2Inventory.getAshes` (0 params) | `util/inventory/Rs2Inventory.java` |
| 1 | `Rs2Inventory.getLast` (1 params) | `util/inventory/Rs2Inventory.java` |
| 1 | `Rs2ItemModel.getUnNotedId` (1 params) | `util/inventory/Rs2ItemModel.java` |
| 1 | `Rs2ItemModel.matches` (1 params) | `util/inventory/Rs2ItemModel.java` |
| 1 | `Rs2ItemModel.matches` (2 params) | `util/inventory/Rs2ItemModel.java` |
| 1 | `Rs2NpcModel.matches` (2 params) | `util/npc/Rs2NpcModel.java` |
| 1 | `Rs2PathApi.getActiveRoute` (0 params) | `util/walker/Rs2PathApi.java` |
| 1 | `Rs2PathApi.getPathfinderConfig` (0 params) | `util/walker/Rs2PathApi.java` |
| 1 | `Rs2Player.calculateHealthPercentage` (1 params) | `util/player/Rs2Player.java` |
| 1 | `Rs2Player.getPlayersInCombat` (0 params) | `util/player/Rs2Player.java` |
| 1 | `Rs2Player.hasPlayerEquippedItem` (2 params) | `util/player/Rs2Player.java` |
| 1 | `Rs2Reachable.getReachableTiles` (1 params) | `util/reachable/Rs2Reachable.java` |
| 1 | `Rs2Reflection.getGroundItemActions` (1 params) | `util/reflection/Rs2Reflection.java` |
| 1 | `Rs2Tile.getNearestWalkableTileWithLineOfSight` (1 params) | `util/tile/Rs2Tile.java` |
| 1 | `Rs2UiHelper.getObjectClickbox` (1 params) | `util/misc/Rs2UiHelper.java` |
| 1 | `Rs2UiHelper.isRectangleWithinRectangle` (2 params) | `util/misc/Rs2UiHelper.java` |
| 1 | `Rs2UiHelper.isRectangleWithinViewport` (1 params) | `util/misc/Rs2UiHelper.java` |

## Actions used by Hub plugins (101)

| Plugins | Method | File |
| ---: | --- | --- |
| 49 | `Rs2Widget.clickWidget` (4 params) | `util/widget/Rs2Widget.java` |
| 25 | `Rs2Player.eatAt` (2 params) | `util/player/Rs2Player.java` |
| 21 | `Rs2Inventory.drop` (2 params) | `util/inventory/Rs2Inventory.java` |
| 9 | `Rs2Equipment.unEquip` (2 params) | `util/equipment/Rs2Equipment.java` |
| 7 | `Rs2GroundItem.lootItemBasedOnValue` (1 params) | `util/grounditem/Rs2GroundItem.java` |
| 7 | `Rs2Inventory.moveItemToSlot` (2 params) | `util/inventory/Rs2Inventory.java` |
| 7 | `Rs2Npc.interact` (1 params) | `util/npc/Rs2Npc.java` |
| 7 | `Rs2Player.drinkCombatPotionAt` (1 params) | `util/player/Rs2Player.java` |
| 7 | `Rs2Player.drinkCombatPotionAt` (2 params) | `util/player/Rs2Player.java` |
| 7 | `Rs2Random.wait` (3 params) | `util/math/Rs2Random.java` |
| 6 | `Rs2Dialogue.keyPressForDialogueOption` (1 params) | `util/dialogues/Rs2Dialogue.java` |
| 6 | `Rs2Dialogue.keyPressForDialogueOption` (2 params) | `util/dialogues/Rs2Dialogue.java` |
| 6 | `Rs2Inventory.dropAllExcept` (2 params) | `util/inventory/Rs2Inventory.java` |
| 6 | `Rs2Keyboard.keyRelease` (1 params) | `util/keyboard/Rs2Keyboard.java` |
| 5 | `Rs2Bank.emptyGemBag` (0 params) | `util/bank/Rs2Bank.java` |
| 5 | `Rs2Keyboard.keyHold` (1 params) | `util/keyboard/Rs2Keyboard.java` |
| 4 | `Rs2Inventory.emptyPouches` (0 params) | `util/inventory/Rs2Inventory.java` |
| 4 | `Rs2Inventory.fillPouches` (0 params) | `util/inventory/Rs2Inventory.java` |
| 4 | `Rs2Magic.repairPouchesWithLunar` (0 params) | `util/magic/Rs2Magic.java` |
| 4 | `Rs2Player.drinkPrayerPotion` (0 params) | `util/player/Rs2Player.java` |
| 4 | `Rs2Player.hopIfPlayerDetected` (3 params) | `util/player/Rs2Player.java` |
| 3 | `Rs2Bank.preHover` (0 params) | `util/bank/Rs2Bank.java` |
| 3 | `Rs2Bank.withdrawRunePouch` (0 params) | `util/bank/Rs2Bank.java` |
| 3 | `Rs2Cannon.refill` (0 params) | `util/gameobject/Rs2Cannon.java` |
| 3 | `Rs2Cannon.refill` (1 params) | `util/gameobject/Rs2Cannon.java` |
| 3 | `Rs2Cannon.repair` (0 params) | `util/gameobject/Rs2Cannon.java` |
| 3 | `Rs2DepositBox.depositAll` (0 params) | `util/depositbox/Rs2DepositBox.java` |
| 3 | `Rs2Dialogue.clickCombinationOption` (1 params) | `util/dialogues/Rs2Dialogue.java` |
| 3 | `Rs2Dialogue.clickCombinationOption` (2 params) | `util/dialogues/Rs2Dialogue.java` |
| 3 | `Rs2Dialogue.sleepUntilHasCombinationDialogue` (0 params) | `util/dialogues/Rs2Dialogue.java` |
| 3 | `Rs2ExplorersRing.closeInterface` (0 params) | `util/item/Rs2ExplorersRing.java` |
| 3 | `Rs2ExplorersRing.highAlch` (1 params) | `util/item/Rs2ExplorersRing.java` |
| 3 | `Rs2Gembag.checkGemBag` (0 params) | `util/inventory/Rs2Gembag.java` |
| 3 | `Rs2GroundItem.interact` (1 params) | `util/grounditem/Rs2GroundItem.java` |
| 3 | `Rs2GroundItem.interact` (2 params) | `util/grounditem/Rs2GroundItem.java` |
| 3 | `Rs2GroundItem.loot` (1 params) | `util/grounditem/Rs2GroundItem.java` |
| 3 | `Rs2GroundItem.lootCoins` (1 params) | `util/grounditem/Rs2GroundItem.java` |
| 3 | `Rs2Inventory.checkPouches` (0 params) | `util/inventory/Rs2Inventory.java` |
| 3 | `Rs2Inventory.open` (0 params) | `util/inventory/Rs2Inventory.java` |
| 3 | `Rs2LootEngine.with` (1 params) | `util/grounditem/Rs2LootEngine.java` |
| 3 | `Rs2Npc.hoverOverActor` (1 params) | `util/npc/Rs2Npc.java` |
| 3 | `Rs2Player.drinkAntiFirePotion` (0 params) | `util/player/Rs2Player.java` |
| 3 | `Rs2Player.drinkGoadingPotion` (0 params) | `util/player/Rs2Player.java` |
| 3 | `Rs2Widget.clickWidgetFast` (1 params) | `util/widget/Rs2Widget.java` |
| 3 | `Rs2Widget.clickWidgetFast` (2 params) | `util/widget/Rs2Widget.java` |
| 3 | `Rs2Widget.clickWidgetFast` (3 params) | `util/widget/Rs2Widget.java` |
| 2 | `Rs2Bank.depositLootingBag` (0 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Bank.emptyHerbSack` (0 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Bank.handleBankPin` (0 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Bank.handleBankPin` (1 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Bank.openTab` (1 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Bank.scrollBankToSlot` (1 params) | `util/bank/Rs2Bank.java` |
| 2 | `Rs2Combat.setAttackStyle` (1 params) | `util/combat/Rs2Combat.java` |
| 2 | `Rs2DepositBox.depositAllExcept` (1 params) | `util/depositbox/Rs2DepositBox.java` |
| 2 | `Rs2DepositBox.depositAllExcept` (2 params) | `util/depositbox/Rs2DepositBox.java` |
| 2 | `Rs2DepositBox.openDepositBox` (0 params) | `util/depositbox/Rs2DepositBox.java` |
| 2 | `Rs2Dialogue.sleepUntilHasContinue` (0 params) | `util/dialogues/Rs2Dialogue.java` |
| 2 | `Rs2GameObject.hoverOverObject` (1 params) | `util/gameobject/Rs2GameObject.java` |
| 2 | `Rs2GroundItem.coreLoot` (1 params) | `util/grounditem/Rs2GroundItem.java` |
| 2 | `Rs2GroundItem.lootItemsBasedOnLocation` (2 params) | `util/grounditem/Rs2GroundItem.java` |
| 2 | `Rs2Inventory.deselect` (0 params) | `util/inventory/Rs2Inventory.java` |
| 2 | `Rs2Inventory.hover` (1 params) | `util/inventory/Rs2Inventory.java` |
| 2 | `Rs2MiniMap.worldToMinimap` (1 params) | `util/walker/Rs2MiniMap.java` |
| 2 | `Rs2Player.logoutIfPlayerDetected` (1 params) | `util/player/Rs2Player.java` |
| 2 | `Rs2Random.fancyNormalSample` (2 params) | `util/math/Rs2Random.java` |
| 2 | `Rs2RunePouch.fullUpdate` (0 params) | `util/inventory/Rs2RunePouch.java` |
| 1 | `Rs2AntibanSettings.loadFromProfile` (0 params) | `util/antiban/Rs2AntibanSettings.java` |
| 1 | `Rs2Bank.emptySeedBox` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.lockAllBySlot` (1 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.openMainTab` (0 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.withdrawAllButOne` (1 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.withdrawAllButOne` (2 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Bank.withdrawXAndEquip` (2 params) | `util/bank/Rs2Bank.java` |
| 1 | `Rs2Camera.stopTrackingNpc` (0 params) | `util/camera/Rs2Camera.java` |
| 1 | `Rs2Camera.trackNpc` (1 params) | `util/camera/Rs2Camera.java` |
| 1 | `Rs2Dialogue.handleQuestOptionDialogueSelection` (0 params) | `util/dialogues/Rs2Dialogue.java` |
| 1 | `Rs2Dialogue.sleepUntilNotInDialogue` (0 params) | `util/dialogues/Rs2Dialogue.java` |
| 1 | `Rs2Dialogue.sleepUntilSelectAnOption` (0 params) | `util/dialogues/Rs2Dialogue.java` |
| 1 | `Rs2Discord.convertColorToInt` (1 params) | `util/discord/Rs2Discord.java` |
| 1 | `Rs2Discord.sendWebhookMessage` (1 params) | `util/discord/Rs2Discord.java` |
| 1 | `Rs2Discord.sendWebhookMessage` (2 params) | `util/discord/Rs2Discord.java` |
| 1 | `Rs2Discord.sendWebhookMessage` (3 params) | `util/discord/Rs2Discord.java` |
| 1 | `Rs2GroundItem.lootAllItemBasedOnValue` (2 params) | `util/grounditem/Rs2GroundItem.java` |
| 1 | `Rs2GroundItem.pickup` (1 params) | `util/grounditem/Rs2GroundItem.java` |
| 1 | `Rs2Inventory.cleanHerbs` (1 params) | `util/inventory/Rs2Inventory.java` |
| 1 | `Rs2Inventory.dropAmount` (4 params) | `util/inventory/Rs2Inventory.java` |
| 1 | `Rs2Inventory.fullSlotCount` (0 params) | `util/inventory/Rs2Inventory.java` |
| 1 | `Rs2Inventory.slot` (1 params) | `util/inventory/Rs2Inventory.java` |
| 1 | `Rs2Inventory.useRestoreEnergyItem` (0 params) | `util/inventory/Rs2Inventory.java` |
| 1 | `Rs2Keyboard.releaseHeldKeys` (0 params) | `util/keyboard/Rs2Keyboard.java` |
| 1 | `Rs2PathApi.exit` (0 params) | `util/walker/Rs2PathApi.java` |
| 1 | `Rs2Player.attack` (1 params) | `util/player/Rs2Player.java` |
| 1 | `Rs2Player.walkUnder` (1 params) | `util/player/Rs2Player.java` |
| 1 | `Rs2Random.gaussRand` (2 params) | `util/math/Rs2Random.java` |
| 1 | `Rs2Random.logNormalBounded` (2 params) | `util/math/Rs2Random.java` |
| 1 | `Rs2Random.normalRange` (3 params) | `util/math/Rs2Random.java` |
| 1 | `Rs2Random.skewedRand` (4 params) | `util/math/Rs2Random.java` |
| 1 | `Rs2Random.truncatedGauss` (3 params) | `util/math/Rs2Random.java` |
| 1 | `Rs2Tab.switchToEmotesTab` (0 params) | `util/tabs/Rs2Tab.java` |
| 1 | `Rs2Tab.switchToMusicTab` (0 params) | `util/tabs/Rs2Tab.java` |
| 1 | `Rs2Thrall.cast` (1 params) | `util/magic/thralls/Rs2Thrall.java` |
