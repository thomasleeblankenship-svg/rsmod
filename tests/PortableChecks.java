package net.runelite.client.plugins.mobilemicrobot;

import java.awt.Rectangle;
import java.lang.reflect.Field;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.headless.JavaBridge;
import net.runelite.client.headless.ScriptInputSession;

/** Deterministic smoke tests against compile-only mocks, not a device test. */
public final class PortableChecks
{
    private static int pass;
    private static void check(boolean success, String label)
    {
        if (!success) throw new AssertionError(label);
        System.out.println("PASS " + label);
        pass++;
    }

    private static final class Config implements MobileMicrobotConfig
    {
        boolean run;
        ScriptKind kind = ScriptKind.DIAGNOSTICS;
        @Override public ScriptKind selectedScript(){return kind;}
        @Override public boolean runScript(){return run;}
    }
    private static final class Manager extends ConfigManager
    {
        Config config;
        Manager(Config c){config=c;}
        @Override public void setConfiguration(String group,String key,Object value)
        {
            super.setConfiguration(group,key,value);
            if ("runScript".equals(key)) config.run = (Boolean) value;
            if ("selectedScript".equals(key)) config.kind = (ScriptKind) value;
        }
    }
    private static final class Fake implements Client
    {
        GameState state = GameState.LOGGED_IN;
        Item[] items = new Item[28];
        boolean moving;
        Scene scene;
        final WorldPoint pos = new WorldPoint(3200,3200,0);
        @Override public GameState getGameState(){return state;}
        @Override public Player getLocalPlayer(){return state == GameState.LOGGED_IN ? new Player(){
            public WorldPoint getWorldLocation(){return pos;}
            public int getAnimation(){return -1;}
            public int getPoseAnimation(){return moving ? 819 : 808;}
            public int getIdlePoseAnimation(){return 808;}
            public String getName(){return "MockPlayer";}
        }:null;}
        @Override public ItemContainer getItemContainer(InventoryID id){return () -> items;}
        @Override public Scene getScene(){return scene;}
        @Override public ObjectComposition getObjectDefinition(int id){return ()->"Oak tree";}
        @Override public int getCanvasWidth(){return 800;}
        @Override public int getCanvasHeight(){return 600;}
    }
    private static final class Obj implements GameObject
    {
        @Override public WorldPoint getWorldLocation(){return new WorldPoint(3201,3201,0);}
        @Override public int getId(){return 100;}
        @Override public java.awt.Shape getClickbox(){return new Rectangle(100,100,30,30);}
    }
    private static void field(Object obj,String name,Object value) throws Exception
    {
        Field f=obj.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.set(obj,value);
    }
    public static void main(String[] args) throws Exception
    {
        Fake game=new Fake();
        Config config=new Config();
        Manager manager=new Manager(config);
        MobileScriptContext ctx=new MobileScriptContext(game,config,new ScriptInputSession());
        check(ctx.player().isIdle(),"idle pose is idle");
        game.moving=true;
        check(ctx.player().isMoving() && !ctx.player().isIdle(),"movement pose blocks idle");
        game.moving=false;
        check(ctx.inventory().occupiedSlots()==0 && ctx.inventory().freeSlots()==28,"inventory empty");
        for(int i=0;i<28;i++) game.items[i]=new Item(i+1,1);
        check(ctx.inventory().isFull() && ctx.inventory().freeSlots()==0,"inventory full");
        game.items[0]=new Item(120,17);
        check(ctx.inventory().contains(120) && ctx.inventory().count(120)==17,"inventory stack quantities");
        game.items[27]=new Item(-1,0);
        check(!ctx.inventory().isFull() && ctx.inventory().freeSlots()==1,"empty-slot sentinel minus one");
        game.scene=()->new Tile[][][]{ { { () -> new GameObject[]{new Obj()} } } };
        check(ctx.objects().findNearest("Oak tree",5)!=null,"nearest scene object");
        check(ctx.objects().findNearest("Yew tree",5)==null,"object name filtering");
        JavaBridge.calls=0;
        check(ctx.objects().interact(new Obj()),"first input reserved");
        check(!ctx.objects().interact(new Obj()) && JavaBridge.calls==1,"one input per tick");
        MobileScriptContext second=new MobileScriptContext(game,config,new ScriptInputSession());
        check(second.walker().walkToVisible(new WorldPoint(3204,3203,0)) && JavaBridge.calls==2,"near minimap walk");
        check(!second.walker().walkToVisible(new WorldPoint(3300,3300,0)),"distant path explicitly unsupported");
        check(!second.walker().walkToVisible(new WorldPoint(3204,3203,0)),"shared input budget");

        MobileMicrobotPlugin plugin=new MobileMicrobotPlugin();
        field(plugin,"client",game);
        ClientThread thread=new ClientThread();
        field(plugin,"clientThread",thread);
        field(plugin,"configManager",manager);
        field(plugin,"config",config);
        plugin.startUp();
        check(!config.run && MobileScriptBridge.statusJson().contains("ready"),"startup does not auto-run");
        MobileScriptBridge.start("DIAGNOSTICS");
        check(config.run,"start facade config scheduling");
        plugin.onGameTick(new GameTick());
        check(MobileScriptBridge.statusJson().contains("Diagnostics") == false && MobileScriptBridge.statusJson().contains("running\":true"),"diagnostics begins ticking");
        check(MobileScriptBridge.scriptsJson().contains("SCENE_SCAN"),"registry visible");
        plugin.onGameStateChanged(new GameStateChanged(GameState.CONNECTION_LOST));
        check(!config.run && !MobileScriptBridge.statusJson().contains("running\":true"),"disconnect fail-closed");
        game.state=GameState.LOGGED_IN;
        plugin.onGameTick(new GameTick());
        check(!config.run,"no surprise restart after reconnection");
        MobileScriptBridge.start("SCENE_SCAN");
        plugin.onGameTick(new GameTick());
        check(MobileScriptBridge.statusJson().contains("Scene objects"),"scene scanner runs");
        MobileScriptBridge.stop();
        check(!config.run,"stop facade");
        MobileScriptBridge.start("DIAGNOSTICS");
        plugin.onGameTick(new GameTick());
        plugin.onGameStateChanged(new GameStateChanged(GameState.HOPPING));
        check(!config.run,"world hop fails closed");
        thread.queued=true;
        MobileScriptBridge.start("DIAGNOSTICS");
        MobileScriptBridge.stop();
        thread.drain();
        check(!config.run,"stop invalidates queued start");
        MobileScriptBridge.start("DIAGNOSTICS");
        plugin.shutDown();
        thread.drain();
        check(!config.run,"disable invalidates queued start");
        check(MobileScriptBridge.statusJson().contains("ready\":false"),"plugin bridge detaches");
        System.out.println("ALL " + pass + " TESTS PASSED");
    }
}
