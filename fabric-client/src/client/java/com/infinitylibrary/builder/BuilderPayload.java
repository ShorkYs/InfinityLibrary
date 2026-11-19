package com.infinitylibrary.builder;
import java.nio.charset.StandardCharsets;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
public record BuilderPayload(String json) implements CustomPacketPayload {
    public static final Type<BuilderPayload> TYPE=new Type<>(Identifier.fromNamespaceAndPath("infinitylibrary","builder_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BuilderPayload> CODEC=StreamCodec.of((buf,payload)->{byte[] b=payload.json().getBytes(StandardCharsets.UTF_8);if(b.length>30000)throw new IllegalArgumentException("Builder payload too large");buf.writeBytes(b);},buf->{int size=buf.readableBytes();if(size>30000)throw new IllegalArgumentException("Builder payload too large");byte[] b=new byte[size];buf.readBytes(b);return new BuilderPayload(new String(b,StandardCharsets.UTF_8));});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
