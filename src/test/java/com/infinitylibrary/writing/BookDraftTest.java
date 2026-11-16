package com.infinitylibrary.writing;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BookDraftTest {
    @Test void pagesCanBeWrittenInsertedNavigatedAndDeleted() {
        BookDraft d=new BookDraft(); d.page("Page one\nSecond line"); d.addPage(); d.page("Page two");
        d.move(-1); assertEquals("Page one\nSecond line",d.page());
        d.addPage(); d.page("Inserted"); assertEquals(List.of("Page one\nSecond line","Inserted","Page two"),d.pages());
        d.removePage(); assertEquals(List.of("Page one\nSecond line","Page two"),d.pages());
    }
    @Test void deletingOnlyPageLeavesAnEditableBlankPage() {
        BookDraft d=new BookDraft(); d.page("text"); d.removePage(); assertEquals(List.of(""),d.pages()); assertEquals(0,d.current());
    }
    @Test void enforcesVanillaLimitsAndKeepsTextOnInvalidImport() {
        BookDraft d=new BookDraft(); d.page("saved");
        assertThrows(IllegalArgumentException.class,() -> d.page("x".repeat(1025)));
        assertThrows(IllegalArgumentException.class,() -> d.replacePages(List.of("x".repeat(1025))));
        assertEquals("saved",d.page());
        for(int i=1;i<100;i++) d.addPage();
        assertThrows(IllegalArgumentException.class,d::addPage); assertEquals(100,d.pages().size());
    }
    @Test void publicationRequiresTitleAndContent() {
        BookDraft d=new BookDraft(); assertThrows(IllegalArgumentException.class,d::validatePublish);
        d.details("A book","fiction","adventure",true); assertThrows(IllegalArgumentException.class,d::validatePublish);
        d.page("Once upon a time"); assertDoesNotThrow(d::validatePublish);
        assertTrue(d.publicBook());
    }
    @Test void invalidMetadataDoesNotPartiallyChangeSavedDetails() {
        BookDraft d=new BookDraft(); d.details("Original","history","tag",false);
        assertThrows(IllegalArgumentException.class,() -> d.details("Changed","c".repeat(65),"",true));
        assertEquals("Original",d.title()); assertEquals("history",d.category()); assertFalse(d.publicBook());
    }
}
