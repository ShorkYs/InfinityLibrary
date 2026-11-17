package com.infinitylibrary.community;

import com.infinitylibrary.writing.BookDraft;
import java.util.*;

/** Immutable content + optimistic revision. No shared mutable page cursor. */
public record SharedManuscript(UUID id,UUID owner,String ownerName,Map<UUID,String> editors,Map<UUID,String> contributors,long revision,String title,String category,String tags,boolean publicBook,List<String> pages){
    public SharedManuscript{editors=Map.copyOf(editors);contributors=Map.copyOf(contributors);pages=List.copyOf(pages);BookDraft d=new BookDraft();d.details(title,category,tags,publicBook);d.replacePages(pages);if(pages.isEmpty()||editors.size()>16)throw new IllegalArgumentException("Invalid shared manuscript.");}
    public static SharedManuscript create(UUID owner,String name,String title){return new SharedManuscript(UUID.randomUUID(),owner,name,Map.of(owner,name),Map.of(owner,name),0,title,"general","",false,List.of(""));}
    public boolean canEdit(UUID player){return owner.equals(player)||editors.containsKey(player);}
    public void requireOwner(UUID actor){if(!owner.equals(actor))throw new IllegalArgumentException("Only the manuscript owner can manage editors or publish.");}
    public void requireEdit(UUID actor,long expected){if(!canEdit(actor))throw new IllegalArgumentException("You are no longer an editor of this manuscript.");if(expected!=revision)throw new IllegalArgumentException("Another author changed this manuscript. Reopen it before saving; your text was not overwritten.");}
    private SharedManuscript content(UUID actor,String t,String c,String tags,boolean visibility,List<String> pages){Map<UUID,String> names=new HashMap<>(contributors);names.put(actor,editors.getOrDefault(actor,ownerName));return new SharedManuscript(id,owner,ownerName,editors,names,revision+1,t,c,tags,visibility,pages);}
    public SharedManuscript page(UUID actor,long expected,int index,String text){requireEdit(actor,expected);AtomicYaml.text(text,BookDraft.MAX_PAGE_LENGTH,"Page");List<String> updated=new ArrayList<>(pages);updated.set(index,text);return content(actor,title,category,tags,publicBook,updated);}
    public SharedManuscript details(UUID actor,long expected,String title,String category,String tags,boolean visibility){requireEdit(actor,expected);if(visibility!=publicBook)requireOwner(actor);return content(actor,title,category,tags,visibility,pages);}
    public SharedManuscript insert(UUID actor,long expected,int after){requireEdit(actor,expected);if(pages.size()>=100)throw new IllegalArgumentException("Maximum 100 pages.");List<String> p=new ArrayList<>(pages);p.add(after+1,"");return content(actor,title,category,tags,publicBook,p);}
    public SharedManuscript removePage(UUID actor,long expected,int index){requireEdit(actor,expected);List<String> p=new ArrayList<>(pages);if(p.size()==1)p.set(0,"");else p.remove(index);return content(actor,title,category,tags,publicBook,p);}
    public SharedManuscript invite(UUID actor,UUID editor,String name){requireOwner(actor);Map<UUID,String> next=new HashMap<>(editors);next.put(editor,name);return new SharedManuscript(id,owner,ownerName,next,contributors,revision+1,title,category,tags,publicBook,pages);}
    public SharedManuscript revoke(UUID actor,UUID editor){requireOwner(actor);if(editor.equals(owner))throw new IllegalArgumentException("The owner cannot be removed.");Map<UUID,String> next=new HashMap<>(editors);if(next.remove(editor)==null)throw new IllegalArgumentException("That player is not an editor.");return new SharedManuscript(id,owner,ownerName,next,contributors,revision+1,title,category,tags,publicBook,pages);}
    public BookDraft draft(){BookDraft d=new BookDraft();d.details(title,category,tags,publicBook);d.replacePages(pages);return d;}
}
