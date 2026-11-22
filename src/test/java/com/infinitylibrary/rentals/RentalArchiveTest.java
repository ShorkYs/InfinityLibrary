package com.infinitylibrary.rentals;
import org.junit.jupiter.api.Test;import java.util.*;import static org.junit.jupiter.api.Assertions.*;
class RentalArchiveTest {
 @Test void copiedBookContentIsImmutable(){List<String> pages=new ArrayList<>(List.of("Private words"));var book=new RentalVaultManager.Book(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Title","Author",pages,true);pages.clear();assertEquals(List.of("Private words"),book.pages());assertThrows(UnsupportedOperationException.class,()->book.pages().clear());}
 @Test void oversizedBooksAreRejectedWithoutTruncation(){assertThrows(IllegalArgumentException.class,()->new RentalVaultManager.Book(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Title","Author",List.of("x".repeat(1025)),false));assertThrows(IllegalArgumentException.class,()->new RentalVaultManager.Book(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),"Title","Author",List.of(),false));}
}
