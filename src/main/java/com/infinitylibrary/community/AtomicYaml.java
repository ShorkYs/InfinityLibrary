package com.infinitylibrary.community;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.io.IOException;

public final class AtomicYaml {
    private AtomicYaml(){}
    public static YamlConfiguration read(Path file){
        YamlConfiguration y=new YamlConfiguration();if(!Files.exists(file))return y;
        try{y.load(file.toFile());return y;}catch(Exception ex){throw new IllegalStateException("Cannot read "+file.getFileName()+"; restore valid YAML before continuing.",ex);}
    }
    public static void write(Path file,YamlConfiguration yaml){
        Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        try{Files.createDirectories(file.getParent());Files.writeString(tmp,yaml.saveToString());try{Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);}}
        catch(IOException ex){throw new IllegalStateException("Unable to save "+file.getFileName()+". Change was not accepted.",ex);}
    }
    public static String text(String value,int max,String label){if(value==null||value.length()>max||value.indexOf('\0')>=0)throw new IllegalArgumentException(label+" must have at most "+max+" characters.");return value;}
}
