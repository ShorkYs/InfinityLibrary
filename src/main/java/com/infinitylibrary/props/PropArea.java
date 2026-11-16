package com.infinitylibrary.props;

import com.infinitylibrary.model.Vector3i;
import org.bukkit.configuration.ConfigurationSection;

public record PropArea(String id, Vector3i min, Vector3i size, String category, double gapPercent, boolean rotate,boolean stack,FillMode fillMode) {
    public enum FillMode { EXACT, PACKED }
    public PropArea(String id,Vector3i min,Vector3i size,String category,double gapPercent,boolean rotate,boolean stack) {this(id,min,size,category,gapPercent,rotate,stack,FillMode.EXACT);}
    public PropArea(String id,Vector3i min,Vector3i size,String category,double gapPercent,boolean rotate) { this(id,min,size,category,gapPercent,rotate,true); }
    public PropArea {
        if(fillMode==null)throw new IllegalArgumentException("Decoration fill mode is required");
        if(id==null || !id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Area IDs need 1–64 lowercase letters, digits, underscores or hyphens.");
        if(category==null || !(category.equals("*")||category.matches("[a-z0-9_-]{1,64}"))) throw new IllegalArgumentException("Invalid prop category.");
        if(min==null || size==null || size.x()<1||size.y()<1||size.z()<1 || volume(size)>8192) throw new IllegalArgumentException("Prop area must contain 1–8,192 blocks.");
        if(!Double.isFinite(gapPercent)||gapPercent<0||gapPercent>100) throw new IllegalArgumentException("Gap chance must be 0–100.");
    }
    public static long volume(Vector3i size) { return Math.multiplyExact(Math.multiplyExact((long)size.x(),size.y()),size.z()); }
    public boolean contains(Vector3i p) {
        return p.x()>=min.x()&&p.x()<(long)min.x()+size.x()&&p.y()>=min.y()&&p.y()<(long)min.y()+size.y()&&p.z()>=min.z()&&p.z()<(long)min.z()+size.z();
    }
    public boolean overlaps(PropArea b) {
        return min.x()<(long)b.min.x()+b.size.x()&&(long)min.x()+size.x()>b.min.x()
            &&min.y()<(long)b.min.y()+b.size.y()&&(long)min.y()+size.y()>b.min.y()
            &&min.z()<(long)b.min.z()+b.size.z()&&(long)min.z()+size.z()>b.min.z();
    }
    public void write(ConfigurationSection s) {
        min.write(s.createSection("min")); size.write(s.createSection("size")); s.set("category",category);s.set("gap-percent",gapPercent);s.set("rotate",rotate);s.set("stack",stack);s.set("fill-mode",fillMode.name());
    }
    public static PropArea read(String id,ConfigurationSection s) {
        return new PropArea(id,Vector3i.read(s.getConfigurationSection("min")),Vector3i.read(s.getConfigurationSection("size")),s.getString("category","library"),s.getDouble("gap-percent",15),s.getBoolean("rotate",true),s.getBoolean("stack",true),FillMode.valueOf(s.getString("fill-mode","EXACT").toUpperCase(java.util.Locale.ROOT)));
    }
}
