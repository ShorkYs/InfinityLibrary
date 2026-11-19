package com.infinitylibrary.builder;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import java.nio.charset.StandardCharsets;
class BuilderProtocolTest {
 byte[] b(String s){return s.getBytes(StandardCharsets.UTF_8);}
 @Test void acceptsVersionedUtf8(){assertEquals("hello",BuilderProtocol.parse(b("{\"protocol\":1,\"action\":\"hello\",\"label\":\"Bücher\"}")).get("action").getAsString());}
 @Test void rejectsWrongVersionAndShapes(){for(String s:new String[]{"[]","{\"protocol\":2}","{\"protocol\":1", "{\"protocol\":1,\"x\":\"unterminated}"})assertThrows(RuntimeException.class,()->BuilderProtocol.parse(b(s)));}
 @Test void rejectsOversizeAndDeepPackets(){assertThrows(RuntimeException.class,()->BuilderProtocol.parse(new byte[30001]));assertThrows(RuntimeException.class,()->BuilderProtocol.parse(b("{\"protocol\":1,\"x\":"+"[".repeat(13)+"0"+"]".repeat(13)+"}")));}
 @Test void identifiersCannotInjectCommandsOrPaths(){var o=BuilderProtocol.parse(b("{\"protocol\":1,\"room\":\"../bad; op\"}"));assertThrows(IllegalArgumentException.class,()->BuilderProtocol.id(o,"room"));}
}