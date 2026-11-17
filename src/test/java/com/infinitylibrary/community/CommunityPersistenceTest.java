package com.infinitylibrary.community;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommunityPersistenceTest {
    @TempDir Path dir;
    @Test void atomicYamlRoundTripsAndRefusesMalformedInputWithoutReplacingIt() throws Exception {Path file=dir.resolve("data.yml");var y=new YamlConfiguration();y.set("text","Dutch\n日本語");AtomicYaml.write(file,y);assertEquals("Dutch\n日本語",AtomicYaml.read(file).getString("text"));Files.writeString(file,"broken: [unterminated");assertThrows(IllegalStateException.class,()->AtomicYaml.read(file));assertEquals("broken: [unterminated",Files.readString(file));}
    @Test void failedAtomicWritePreservesExistingTarget() throws Exception {Path file=dir.resolve("data.yml");Files.writeString(file,"original: true\n");Files.createDirectory(dir.resolve("data.yml.tmp"));var y=new YamlConfiguration();y.set("new",true);assertThrows(IllegalStateException.class,()->AtomicYaml.write(file,y));assertEquals("original: true\n",Files.readString(file));}
    @Test void noticeExpiryAndContentBoundsAreEnforced(){UUID id=UUID.randomUUID();var n=new NoticeboardManager.Notice(id,id,"Author","Event","Details",100,200,false);assertTrue(n.active(199));assertFalse(n.active(200));assertThrows(IllegalArgumentException.class,()->new NoticeboardManager.Notice(id,id,"Author","","Details",100,200,false));assertThrows(IllegalArgumentException.class,()->new NoticeboardManager.Notice(id,id,"Author","Event","x".repeat(2001),100,200,false));}
    @Test void invalidReadingPageCannotBePersisted(){assertThrows(IllegalArgumentException.class,()->new ReadingManager.Progress(-1,2,false,"Title",0));assertThrows(IllegalArgumentException.class,()->new ReadingManager.Progress(2,2,true,"Title",0));assertEquals(1,new ReadingManager.Progress(1,2,true,"Title",0).page());}
}
