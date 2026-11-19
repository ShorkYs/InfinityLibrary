package com.infinitylibrary.builder;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
public final class BuilderProtocol {
    public static final int VERSION=1,MAX_BYTES=30000;
    public static JsonObject parse(byte[] bytes){if(bytes.length==0||bytes.length>MAX_BYTES)throw new IllegalArgumentException("Invalid builder packet size.");boolean quote=false,escape=false;int depth=0;for(byte b:bytes){char c=(char)(b&255);if(quote){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='"')quote=false;}else if(c=='"')quote=true;else if(c=='{'||c=='['){if(++depth>12)throw new IllegalArgumentException("Builder packet nesting limit.");}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("Invalid builder packet.");}}if(quote||depth!=0)throw new IllegalArgumentException("Incomplete builder packet.");var value=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8));if(!value.isJsonObject())throw new IllegalArgumentException("Builder object required.");JsonObject obj=value.getAsJsonObject();if(!obj.has("protocol")||obj.get("protocol").getAsInt()!=VERSION)throw new IllegalArgumentException("Incompatible builder protocol.");return obj;}
    public static String id(JsonObject o,String name){String s=o.has(name)?o.get(name).getAsString():"";if(!s.matches("[a-z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid "+name+".");return s;}
    private BuilderProtocol(){}
}
