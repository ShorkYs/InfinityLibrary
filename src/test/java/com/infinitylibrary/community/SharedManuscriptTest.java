package com.infinitylibrary.community;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedManuscriptTest {
    final UUID owner=UUID.randomUUID(),editor=UUID.randomUUID(),stranger=UUID.randomUUID();
    SharedManuscript shared(){return SharedManuscript.create(owner,"Owner","Story").invite(owner,editor,"Editor");}
    @Test void simultaneousEditsCannotSilentlyOverwriteEachOther(){var before=shared();var after=before.page(editor,before.revision(),0,"First edit");assertThrows(IllegalArgumentException.class,()->after.page(owner,before.revision(),0,"Stale edit"));assertEquals("First edit",after.pages().getFirst());assertEquals("",before.pages().getFirst());}
    @Test void revocationBlocksAnAlreadyOpenedEditorsSave(){var before=shared();var after=before.revoke(owner,editor);assertFalse(after.canEdit(editor));assertThrows(IllegalArgumentException.class,()->after.page(editor,before.revision(),0,"No access"));}
    @Test void onlyOwnerCanInviteRevokeOrPublish(){var d=shared();assertThrows(IllegalArgumentException.class,()->d.invite(editor,stranger,"Other"));assertThrows(IllegalArgumentException.class,()->d.revoke(editor,owner));assertThrows(IllegalArgumentException.class,()->d.requireOwner(editor));assertThrows(IllegalArgumentException.class,()->d.revoke(owner,owner));}
    @Test void editorCannotChangeVisibility(){var d=shared();assertThrows(IllegalArgumentException.class,()->d.details(editor,d.revision(),"Other","general","",true));assertTrue(d.details(owner,d.revision(),d.title(),d.category(),d.tags(),true).publicBook());}
    @Test void creditsRetainEditorsWhoContributedBeforeRevocation(){var d=shared();d=d.page(editor,d.revision(),0,"Chapter");d=d.revoke(owner,editor);assertEquals("Editor",d.contributors().get(editor));assertFalse(d.editors().containsKey(editor));}
    @Test void pagesAndMembershipAreImmutableToCallers(){var d=shared();assertThrows(UnsupportedOperationException.class,()->d.pages().set(0,"Bypass"));assertThrows(UnsupportedOperationException.class,()->d.editors().put(stranger,"Bypass"));}
    @Test void pageInsertionAndDeletionKeepAtLeastOnePageAndRespectLimit(){var d=shared();var inserted=d.insert(editor,d.revision(),0);assertEquals(2,inserted.pages().size());var deleted=inserted.removePage(owner,inserted.revision(),1);assertEquals(1,deleted.pages().size());assertEquals(1,deleted.removePage(owner,deleted.revision(),0).pages().size());for(int i=1;i<100;i++)d=d.insert(owner,d.revision(),0);var full=d;assertThrows(IllegalArgumentException.class,()->full.insert(editor,full.revision(),0));}
    @Test void malformedTextAndExcessiveMembershipAreRejected(){var initial=shared();assertThrows(IllegalArgumentException.class,()->initial.page(editor,initial.revision(),0,"a".repeat(1025)));var d=initial;assertThrows(IllegalArgumentException.class,()->SharedManuscript.create(owner,"Owner","a".repeat(33)));for(int i=0;i<14;i++)d=d.invite(owner,UUID.randomUUID(),"Member"+i);var full=d;assertThrows(IllegalArgumentException.class,()->full.invite(owner,stranger,"Too many"));}
    @Test void nativeWholeBookSaveChecksRevisionAndPageLimits(){var d=shared();var next=d.pages(editor,d.revision(),List.of("First","Second"));assertEquals(2,next.pages().size());assertThrows(IllegalArgumentException.class,()->next.pages(editor,d.revision(),List.of("Stale")));assertThrows(IllegalArgumentException.class,()->d.pages(editor,d.revision(),List.of("x".repeat(1025))));}
}
