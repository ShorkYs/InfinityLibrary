package com.infinitylibrary.writing;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class BookTypographyTest {
 @Test void rendersOnlySupportedMarkup(){assertEquals("§lTitle§r\n§lBold§r §oquiet§r\n────────────",BookTypography.render("# Title\n**Bold** _quiet_\n---"));assertEquals("<click:run_command:'/op me'>plain</click>",BookTypography.render("<click:run_command:'/op me'>plain</click>"));}
 @Test void multilineToolsPreserveParagraphs(){assertEquals("**First**\n\n**Last**\n",BookTypography.apply("First\n\nLast\n","bold"));assertEquals("my_draft_note",BookTypography.render("my_draft_note"));}
 @Test void sourceIsPreservedAndPageLimitIsEnforced(){String source="_a_".repeat(300);assertThrows(IllegalArgumentException.class,()->BookTypography.render(source));assertEquals("A paragraph\n---",BookTypography.apply("A paragraph","divider"));}
}
