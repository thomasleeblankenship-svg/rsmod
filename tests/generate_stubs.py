"""Build-only minimalist API stubs. NOT RuneLite and NOT packaged in the Android app."""
from pathlib import Path
import sys
out=Path(sys.argv[1]);out.mkdir(parents=True, exist_ok=True)
f={
# Runtime integration is deliberately outside the portable Phase 2 runner.
# Real runtime/API/example verification uses the actual Gradle project.
'net/runelite/client/eventbus/EventBus.java':'package net.runelite.client.eventbus; public class EventBus {}',
'net/runelite/client/plugins/microbot/MicrobotRuntime.java':'package net.runelite.client.plugins.microbot; import java.util.List;import net.runelite.api.Client;import net.runelite.client.callback.ClientThread;import net.runelite.client.eventbus.EventBus;import net.runelite.client.config.ConfigManager; public class MicrobotRuntime {public static void install(Client c,ClientThread t,EventBus b,ConfigManager m,List<?> services){}public static void uninstall(){}public static void noteHumanInput(){}}',
'net/runelite/client/plugins/microbot/api/MicrobotReadServices.java':'package net.runelite.client.plugins.microbot.api; public class MicrobotReadServices {}',
'net/runelite/client/plugins/microbot/api/MicrobotReadRuntimeService.java':'package net.runelite.client.plugins.microbot.api; import net.runelite.client.eventbus.EventBus;public class MicrobotReadRuntimeService {public MicrobotReadRuntimeService(MicrobotReadServices s,EventBus b){}}',
'net/runelite/client/plugins/microbot/MicrobotScriptAdapter.java':'package net.runelite.client.plugins.microbot;import java.util.function.*;import net.runelite.client.plugins.mobilemicrobot.*;public class MicrobotScriptAdapter<T> implements MobileScript {public MicrobotScriptAdapter(String n,Supplier<T> f,Predicate<T> l){}public void onStart(MobileScriptContext c){throw new UnsupportedOperationException("Use real Gradle integration tests for the upstream runtime");}public void onGameTick(MobileScriptContext c){throw new UnsupportedOperationException();}}',
'net/runelite/client/plugins/microbot/statemachineexample/StateMachineExampleScript.java':'package net.runelite.client.plugins.microbot.statemachineexample;public class StateMachineExampleScript {public boolean run(StateMachineExampleConfig c){throw new UnsupportedOperationException();}}',
'javax/inject/Inject.java':'package javax.inject; public @interface Inject {}',
'com/google/inject/Provides.java':'package com.google.inject; public @interface Provides {}',
'net/runelite/api/GameState.java':'package net.runelite.api; public enum GameState { LOGGED_IN,LOGIN_SCREEN,CONNECTION_LOST,LOGIN_SCREEN_AUTHENTICATOR,LOADING,HOPPING,LOGGING_IN }',
'net/runelite/api/InventoryID.java':'package net.runelite.api; public enum InventoryID { INVENTORY }',
'net/runelite/api/Item.java':'package net.runelite.api; public class Item { private final int id, qty; public Item(int id,int qty){this.id=id;this.qty=qty;} public int getId(){return id;} public int getQuantity(){return qty;} }',
'net/runelite/api/ItemContainer.java':'package net.runelite.api; public interface ItemContainer { Item[] getItems(); }',
'net/runelite/api/Client.java':'''package net.runelite.api;
public interface Client { GameState getGameState(); Player getLocalPlayer(); ItemContainer getItemContainer(InventoryID id); Scene getScene(); ObjectComposition getObjectDefinition(int id); int getCanvasWidth(); int getCanvasHeight(); }''',
'net/runelite/api/Player.java':'package net.runelite.api; import net.runelite.api.coords.WorldPoint; public interface Player { WorldPoint getWorldLocation(); int getAnimation(); int getPoseAnimation(); int getIdlePoseAnimation(); String getName(); }',
'net/runelite/api/coords/WorldPoint.java':'''package net.runelite.api.coords; public final class WorldPoint {private final int x,y,plane; public WorldPoint(int x,int y,int plane){this.x=x;this.y=y;this.plane=plane;} public int getX(){return x;}public int getY(){return y;}public int getPlane(){return plane;}public int distanceTo(WorldPoint p){return p.plane==plane?Math.max(Math.abs(p.x-x),Math.abs(p.y-y)):Integer.MAX_VALUE;} }''',
'net/runelite/api/coords/LocalPoint.java':'package net.runelite.api.coords; import net.runelite.api.Client;public final class LocalPoint { public static LocalPoint fromWorld(Client c,WorldPoint p){return p.getX()<0?null:new LocalPoint();} }',
'net/runelite/api/Point.java':'package net.runelite.api;public final class Point {private final int x,y;public Point(int x,int y){this.x=x;this.y=y;}public int getX(){return x;}public int getY(){return y;}}',
'net/runelite/api/Perspective.java':'package net.runelite.api;import net.runelite.api.coords.LocalPoint;public final class Perspective {public static Point localToMinimap(Client c,LocalPoint l){return new Point(10,15);} }',
'net/runelite/api/GameObject.java':'package net.runelite.api;import net.runelite.api.coords.WorldPoint;import java.awt.Shape;public interface GameObject { WorldPoint getWorldLocation(); int getId(); Shape getClickbox(); }',
'net/runelite/api/ObjectComposition.java':'package net.runelite.api;public interface ObjectComposition{String getName();}',
'net/runelite/api/Scene.java':'package net.runelite.api;public interface Scene {Tile[][][] getTiles();}',
'net/runelite/api/Tile.java':'package net.runelite.api;public interface Tile {GameObject[] getGameObjects();}',
'net/runelite/api/events/GameTick.java':'package net.runelite.api.events;public class GameTick {}',
'net/runelite/api/events/GameStateChanged.java':'package net.runelite.api.events;import net.runelite.api.GameState;public class GameStateChanged {private final GameState state;public GameStateChanged(GameState s){state=s;}public GameState getGameState(){return state;}}',
'net/runelite/client/headless/JavaBridge.java':'package net.runelite.client.headless;public final class JavaBridge {public static int calls;public static void onClick(int x,int y,int button){ScriptInputSession.manualInput(); calls++;} public static boolean onScriptClick(int x,int y,ScriptInputSession session){if(session==null||!session.isActive()) return false; calls++; return true;}}',
'net/runelite/client/callback/ClientThread.java':'package net.runelite.client.callback;public class ClientThread {public boolean queued; private final java.util.Queue<Runnable> tasks=new java.util.ArrayDeque<>(); public void invokeLater(Runnable run){if(queued)tasks.add(run);else run.run();} public void drain(){while(!tasks.isEmpty())tasks.remove().run();}}',
'net/runelite/client/config/Config.java':'package net.runelite.client.config;public interface Config {}',
'net/runelite/client/config/ConfigGroup.java':'package net.runelite.client.config;public @interface ConfigGroup {String value();}',
'net/runelite/client/config/ConfigItem.java':'package net.runelite.client.config;public @interface ConfigItem {String keyName();String name();String description();int position();}',
'net/runelite/client/config/ConfigManager.java':'package net.runelite.client.config;import java.util.*;public class ConfigManager {public final Map<String,Object> state=new HashMap<>();public <T> T getConfig(Class<T> t){return null;}public void setConfiguration(String group,String key,Object value){state.put(key,value);} }',
'net/runelite/client/eventbus/Subscribe.java':'package net.runelite.client.eventbus;public @interface Subscribe {}',
'net/runelite/client/events/ConfigChanged.java':'package net.runelite.client.events;public class ConfigChanged{private final String group; public ConfigChanged(String group){this.group=group;}public String getGroup(){return group;}public String getKey(){return "";}}',
'net/runelite/client/plugins/Plugin.java':'package net.runelite.client.plugins;public class Plugin {protected void startUp() throws Exception{}protected void shutDown() throws Exception{}}',
'net/runelite/client/plugins/PluginDescriptor.java':'package net.runelite.client.plugins;public @interface PluginDescriptor {String name();String description();String[] tags();boolean enabledByDefault();}',
'org/slf4j/Logger.java':'package org.slf4j;public interface Logger {default void error(String fmt,Object...args){} default void warn(String fmt,Object...args){} }',
'org/slf4j/LoggerFactory.java':'package org.slf4j;public class LoggerFactory {public static Logger getLogger(Class<?> c){return new Logger(){};} }',
'com/google/gson/JsonObject.java':'''package com.google.gson;import java.util.*;public class JsonObject{final Map<String,String> props=new LinkedHashMap<>();public void addProperty(String k,String v){props.put(k,\"\\\"\"+v+\"\\\"\");}public void addProperty(String k,Boolean v){props.put(k,v.toString());}public String toString(){List<String> a=new ArrayList<>();props.forEach((k,v)->a.add(\"\\\"\"+k+\"\\\":\"+v));return \"{\"+String.join(\",\",a)+\"}\";}}''',
'com/google/gson/JsonArray.java':'package com.google.gson;import java.util.*;public class JsonArray{final List<JsonObject> items=new ArrayList<>();public void add(JsonObject o){items.add(o);}public String toString(){return items.toString();}}',
}
for n,s in f.items():
    fp=out/n;fp.parent.mkdir(parents=True,exist_ok=True);fp.write_text(s+'\n')
print('Created',len(f),'compile-only stubs')
