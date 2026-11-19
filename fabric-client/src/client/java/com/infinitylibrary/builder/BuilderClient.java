package com.infinitylibrary.builder;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.gizmos.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;


/** Optional client companion. All authoring operations are validated by the server plugin. */
public final class BuilderClient implements ClientModInitializer {
    public static JsonObject state=new JsonObject();public static String error="Waiting for an Infinity Library server…";public static boolean outlines=true,labels=true,throughWalls=false;private static long sequence;private static int ticks;private static boolean hello;
    public static final BuilderScreen.Options options=new BuilderScreen.Options();
    @Override public void onInitializeClient(){PayloadTypeRegistry.clientboundPlay().register(BuilderPayload.TYPE,BuilderPayload.CODEC);PayloadTypeRegistry.serverboundPlay().register(BuilderPayload.TYPE,BuilderPayload.CODEC);ClientPlayNetworking.registerGlobalReceiver(BuilderPayload.TYPE,(payload,context)->context.client().execute(()->receive(payload.json())));
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("infinitylibrary_builder","tools"));KeyMapping open=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinitylibrary_builder.editor",InputConstants.Type.KEYBOARD,InputConstants.KEY_B,category));KeyMapping toggle=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.infinitylibrary_builder.outlines",InputConstants.Type.KEYBOARD,InputConstants.KEY_V,category));
        ClientPlayConnectionEvents.JOIN.register((handler,sender,client)->{reset();});ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(client->{ticks++;if(client.player==null||client.level==null)return;if(!hello&&ticks%20==0&&ClientPlayNetworking.canSend(BuilderPayload.TYPE)){hello=true;hello();}while(open.consumeClick()){if(!state.has("token"))hello();client.setScreenAndShow(new BuilderScreen());}while(toggle.consumeClick())outlines=!outlines;draw(client);});
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("infinitylibrary_builder","selection"),(g,delta)->{var mc=Minecraft.getInstance();if(mc.player==null||mc.gui.screen()!=null||!state.has("token")||text("tool").equals("none"))return;g.fill(6,g.guiHeight()-46,Math.min(g.guiWidth()-6,420),g.guiHeight()-6,0xbb101b26);g.text(mc.font,"Infinity Builder • "+text("tool")+"   [B] Editor  [V] Outlines",12,g.guiHeight()-40,0xff72dbd0);g.text(mc.font,mc.font.plainSubstrByWidth(text("prompt"),Math.max(60,g.guiWidth()-28)),12,g.guiHeight()-24,0xffeeeeee);});
    }
    public static String text(String key){return state.has(key)?state.get(key).getAsString():"";}
    public static void hello(){if(Minecraft.getInstance().getConnection()==null)return;if(!ClientPlayNetworking.canSend(BuilderPayload.TYPE)){error="This server has no compatible Infinity Library builder channel.";return;}JsonObject obj=new JsonObject();obj.addProperty("protocol",1);obj.addProperty("action","hello");ClientPlayNetworking.send(new BuilderPayload(obj.toString()));}
    public static void send(String action,JsonObject args){if(!state.has("token")){hello();return;}if(!ClientPlayNetworking.canSend(BuilderPayload.TYPE)){reset();return;}args.addProperty("protocol",1);args.addProperty("action",action);args.addProperty("token",text("token"));args.addProperty("revision",state.get("revision").getAsLong());args.addProperty("seq",++sequence);ClientPlayNetworking.send(new BuilderPayload(args.toString()));}
    public static void send(String action){send(action,new JsonObject());}
    private static void receive(String value){try{JsonObject obj=JsonParser.parseString(value).getAsJsonObject();if(obj.get("protocol").getAsInt()!=1){reset();error="Builder protocol mismatch; update plugin and mod together.";return;}if(!obj.get("kind").getAsString().equals("state")){state=new JsonObject();hello=false;error=obj.has("message")?obj.get("message").getAsString():"Builder disconnected.";return;}if(Minecraft.getInstance().gui.screen() instanceof BuilderScreen current)current.remember();boolean newToken=!state.has("token")||!text("token").equals(obj.get("token").getAsString());if(newToken)sequence=0;state=obj;error=obj.has("message")?obj.get("message").getAsString():"";String selected=text("selected");if(!selected.isEmpty()&&!selected.equals(options.selected)){options.selected=selected;options.room=selected;if(obj.has("detail"))options.type=obj.getAsJsonObject("detail").get("type").getAsString();}var client=Minecraft.getInstance();if(obj.has("open")&&obj.get("open").getAsBoolean()&&!(client.gui.screen() instanceof BuilderScreen))client.setScreenAndShow(new BuilderScreen());else if(client.gui.screen() instanceof BuilderScreen editor)editor.stateChanged();}catch(RuntimeException ex){reset();error="Invalid builder state received.";}}
    private static void reset(){state=new JsonObject();sequence=0;hello=false;error="Waiting for an Infinity Library server…";options.selected="";}
    private static void draw(Minecraft client){if(!outlines||!state.has("boxes")||client.level==null||!state.has("dimension")||!client.level.dimension().identifier().toString().equals(text("dimension")))return;
        try(var ignored=client.collectPerTickGizmos()){int n=0;for(var entry:state.getAsJsonArray("boxes")){if(n++>=240)break;JsonObject b=entry.getAsJsonObject();var a=b.getAsJsonArray("min");var z=b.getAsJsonArray("max");double x=a.get(0).getAsDouble(),y=a.get(1).getAsDouble(),zz=a.get(2).getAsDouble();if(client.player.position().distanceToSqr(x,y,zz)>256*256)continue;int color=0xff000000|b.get("color").getAsInt();var style=GizmoStyle.strokeAndFill(color,2,0x16000000|(color&0xffffff));var box=Gizmos.cuboid(new AABB(x-.004,y-.004,zz-.004,z.get(0).getAsDouble()+1.004,z.get(1).getAsDouble()+1.004,z.get(2).getAsDouble()+1.004),style);if(throughWalls)box.setAlwaysOnTop();if(b.get("kind").getAsString().equals("corner")&&client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit){var target=hit.getBlockPos();if(client.player.position().distanceToSqr(target.getX(),target.getY(),target.getZ())<256){Gizmos.cuboid(new AABB(Math.min(x,target.getX()),Math.min(y,target.getY()),Math.min(zz,target.getZ()),Math.max(x,target.getX())+1,Math.max(y,target.getY())+1,Math.max(zz,target.getZ())+1),GizmoStyle.stroke(0xbbffffff,1));}}if(labels&&n<=48)Gizmos.billboardTextOverBlock(b.get("label").getAsString(),BlockPos.containing(x,y,zz),color,0x88000000,.025f);}}
    }
}
