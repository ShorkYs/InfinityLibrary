package com.infinitylibrary.writing;
/** Small explicit markup vocabulary; never interprets commands, URLs or MiniMessage tags. */
public final class BookTypography {
 public static String render(String source){String out=source.replaceAll("(?m)^---$","────────────").replaceAll("(?m)^# (.+)$","§l$1§r").replaceAll("\\*\\*([^*\\n]+)\\*\\*","§l$1§r").replaceAll("_([^_\\n]+)_","§o$1§r");if(out.length()>BookDraft.MAX_PAGE_LENGTH)throw new IllegalArgumentException("Formatted page exceeds the page limit; shorten it before publishing");return out;}
 public static String apply(String page,String operation){return switch(operation){case "bold"->"**"+page+"**";case "italic"->"_"+page+"_";case "heading"->"# "+page;case "divider"->page+(page.isEmpty()?"":"\n")+"---";default->throw new IllegalArgumentException("Unknown formatting tool");};}
 private BookTypography(){}
}
