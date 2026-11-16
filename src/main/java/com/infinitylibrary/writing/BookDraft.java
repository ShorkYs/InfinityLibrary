package com.infinitylibrary.writing;

import java.util.*;

/** A virtual manuscript, independent of item inventories and client callback state. */
public final class BookDraft {
    public static final int MAX_PAGES = 100;
    public static final int MAX_PAGE_LENGTH = 1024;
    private String title = "";
    private String category = "general";
    private String tags = "";
    private boolean publicBook;
    private final List<String> pages = new ArrayList<>(List.of(""));
    private int current;
    public String title() { return title; }
    public String category() { return category; }
    public String tags() { return tags; }
    public boolean publicBook() { return publicBook; }
    public List<String> pages() { return List.copyOf(pages); }
    public int current() { return current; }
    public String page() { return pages.get(current); }
    public void details(String title, String category, String tags, boolean visibility) {
        check(title,32,"Title"); check(category,64,"Category"); check(tags,256,"Tags");
        this.title=title.trim(); this.category=category.isBlank()?"general":category.trim(); this.tags=tags.trim(); this.publicBook=visibility;
    }
    public void page(String text) { check(text,MAX_PAGE_LENGTH,"Page"); pages.set(current,text); }
    public void move(int delta) { current=Math.max(0,Math.min(pages.size()-1,current+delta)); }
    public void addPage() {
        if(pages.size()>=MAX_PAGES) throw new IllegalArgumentException("A book can have at most 100 pages.");
        pages.add(++current,"");
    }
    public void removePage() { if(pages.size()==1) pages.set(0,""); else { pages.remove(current); current=Math.min(current,pages.size()-1); } }
    public void replacePages(List<String> input) {
        if(input.size()>MAX_PAGES) throw new IllegalArgumentException("Too many pages.");
        for(String s:input) check(s,MAX_PAGE_LENGTH,"Page");
        pages.clear(); pages.addAll(input.isEmpty()?List.of(""):input); current=0;
    }
    public void validatePublish() {
        if(title.isBlank()) throw new IllegalArgumentException("Set a title before signing or publishing.");
        if(pages.stream().allMatch(String::isBlank)) throw new IllegalArgumentException("Write at least one page first.");
    }
    private void check(String value,int length,String label) {
        if(value==null || value.length()>length) throw new IllegalArgumentException(label+" must be at most "+length+" characters.");
    }
}
