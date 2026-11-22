package com.infinitylibrary.storage;
import com.infinitylibrary.community.AtomicYaml;

import com.infinitylibrary.InfinityLibraryPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class BookStorageManager implements org.bukkit.event.Listener {
    private final InfinityLibraryPlugin plugin;
    private final File file;
    private final File dailyFile;
    private final NamespacedKey ownerUuidKey;
    private final NamespacedKey ownerNameKey;
    private final NamespacedKey publicKey;
    private final NamespacedKey categoryKey;
    private final NamespacedKey tagsKey;
    private final NamespacedKey ratingKey;
    private final NamespacedKey commentsKey;
    private final Map<UUID, StoredBook> books = new ConcurrentHashMap<>();
    private final Set<UUID> awaitingSearch = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PendingBookMetadata> pendingMetadata = new ConcurrentHashMap<>();
    private final Map<UUID, PendingLecternBookEdit> pendingLecternEdits = new ConcurrentHashMap<>();
    private final Map<UUID, PendingReturnBookEdit> pendingReturnEdits = new ConcurrentHashMap<>();
    private final Map<String, String> shelfCategories = new ConcurrentHashMap<>();
    private YamlConfiguration daily;
    private final Set<String> registeredShelves=new LinkedHashSet<>();
    private final Queue<String> scanChunks=new ArrayDeque<>();private final Set<String> queuedChunks=new HashSet<>();
    private int shelfTask=-1,shelfTicks,shelfCursor;
    private final Queue<String> stockingShelves=new ArrayDeque<>();
    private final Set<String> queuedStockingShelves=new HashSet<>();


    public BookStorageManager(InfinityLibraryPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "books.yml");
        this.dailyFile = new File(plugin.getDataFolder(), "daily-books.yml");
        this.ownerUuidKey = new NamespacedKey(plugin, "book_owner_uuid");
        this.ownerNameKey = new NamespacedKey(plugin, "book_owner_name");
        this.publicKey = new NamespacedKey(plugin, "book_public");
        this.categoryKey = new NamespacedKey(plugin, "book_category");
        this.tagsKey = new NamespacedKey(plugin, "book_tags");
        this.ratingKey = new NamespacedKey(plugin, "book_rating");
        this.commentsKey = new NamespacedKey(plugin, "book_comments");
    }

    public void load() {
        YamlConfiguration nextDaily = AtomicYaml.read(dailyFile.toPath());
        YamlConfiguration y = AtomicYaml.read(file.toPath());
        Map<UUID,StoredBook> nextBooks=new HashMap<>();
        Map<String,String> nextCategories=new HashMap<>();
        ConfigurationSection root = y.getConfigurationSection("books");
        if(root!=null)for(String key:root.getKeys(false)) {
            try { StoredBook book=StoredBook.read(UUID.fromString(key),root.getConfigurationSection(key));nextBooks.put(book.id(),book); }
            catch(RuntimeException ex){throw new IllegalStateException("Invalid stored book "+key+"; restore books.yml from backup",ex);}
        }
        ConfigurationSection shelves=y.getConfigurationSection("shelf-categories");
        if(shelves!=null)for(String key:shelves.getKeys(false))nextCategories.put(key,shelves.getString(key,""));
        books.clear();books.putAll(nextBooks);shelfCategories.clear();shelfCategories.putAll(nextCategories);
        registeredShelves.clear();registeredShelves.addAll(y.getStringList("registered-shelves"));daily=nextDaily;
        stockingShelves.clear();queuedStockingShelves.clear();if(shelfTask!=-1)requestShelfScan();
    }

    private int saveTask = -1;
    public void saveAsync() {
        if (!Bukkit.isPrimaryThread()) { Bukkit.getScheduler().runTask(plugin, this::saveAsync); return; }
        if (saveTask != -1) return;
        saveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> { saveTask = -1; saveNow(); }, 40L).getTaskId();
    }
    public synchronized void saveNow() {
        try { persistBookCollection(); if(daily!=null) AtomicYaml.write(dailyFile.toPath(),daily); }
        catch(RuntimeException e) { plugin.getLogger().severe("Unable to save book data: "+e.getMessage()); }
    }

    /** Acknowledge publication only after the searchable collection is safely on disk. */
    public void publishManuscript(Player player,ItemStack stack) {
        Map<UUID,StoredBook> previous=new HashMap<>(books);
        try {
            recordBook(player,stack,null);
            persistBookCollection();requestShelfScan();
        } catch(RuntimeException ex) { books.clear(); books.putAll(previous); throw ex; }
    }
    private void persistBookCollection() {
        YamlConfiguration y=new YamlConfiguration();
        y.set("registered-shelves",new ArrayList<>(registeredShelves));
        ConfigurationSection root=y.createSection("books");
        for(StoredBook b:books.values()) b.write(root.createSection(b.id().toString()));
        ConfigurationSection shelfRoot=y.createSection("shelf-categories");
        for(var e:shelfCategories.entrySet()) shelfRoot.set(e.getKey(),e.getValue());
        java.nio.file.Path target=file.toPath(),temp=target.resolveSibling("books.yml.tmp");
        try {
            java.nio.file.Files.writeString(temp,y.saveToString());
            try { java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch(java.nio.file.AtomicMoveNotSupportedException e) { java.nio.file.Files.move(temp,target,java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException e) { throw new IllegalStateException("Book could not be saved. Your book & quill was not consumed.",e); }
    }

    public boolean giveDailyWritableBook(Player player) {
        String today = LocalDate.now().toString();
        String path = "players." + player.getUniqueId() + ".last-claimed";
        if (!player.hasPermission("infinitylibrary.book.bypassdaily") && today.equals(daily.getString(path))) return false;
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Private Library Draft");
        tagLibraryBook(meta, player, false);
        book.setItemMeta(meta);
        if (!player.getInventory().addItem(book).isEmpty()) { player.sendMessage(ChatColor.RED + "Make room in your inventory first."); return false; }
        daily.set(path, today);
        saveAsync();
        return true;
    }

    public void recordBook(Player contributor, ItemStack stack, Location shelfLocation) {
        if (stack == null || stack.getType() != Material.WRITTEN_BOOK || !(stack.getItemMeta() instanceof BookMeta meta)) return;
        BookOwnership ownership = ownership(meta, contributor);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String fp = fingerprint(meta);
        Optional<StoredBook> existing = books.values().stream().filter(b -> b.ownerUuid().equals(ownership.ownerUuid()) && fp.equals(fingerprint((BookMeta) Objects.requireNonNull(b.toItemStack().getItemMeta())))).findFirst();
        UUID id = existing.map(StoredBook::id).orElseGet(UUID::randomUUID);
        pdc.set(new NamespacedKey(plugin,"library_book_id"),PersistentDataType.STRING,id.toString());
        pdc.set(ownerUuidKey, PersistentDataType.STRING, ownership.ownerUuid());
        pdc.set(ownerNameKey, PersistentDataType.STRING, ownership.ownerName());
        pdc.set(publicKey, PersistentDataType.BYTE, (byte)(ownership.isPublic() ? 1 : 0));
        stack.setItemMeta(meta);
        String insertedAt = existing.map(StoredBook::insertedAt).orElseGet(() -> Instant.now().toString());
        books.put(id, new StoredBook(id, contributor.getUniqueId(), contributor.getName(), ownership.ownerUuid(), ownership.ownerName(), ownership.isPublic(), safe(meta.getTitle()), safe(meta.getAuthor()), List.copyOf(meta.getPages()), stack.serialize(), insertedAt, shelfLocation==null?existing.map(StoredBook::shelfLocation).orElse(""):serializeLocation(shelfLocation), safe(pdc.get(categoryKey, PersistentDataType.STRING)), safe(pdc.get(tagsKey, PersistentDataType.STRING)), safe(pdc.get(ratingKey, PersistentDataType.STRING)), safe(pdc.get(commentsKey, PersistentDataType.STRING))));
        saveAsync();requestShelfStocking();
    }


    public boolean isPublicBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte publicFlag = item.getItemMeta().getPersistentDataContainer().get(publicKey, PersistentDataType.BYTE);
        return publicFlag != null && publicFlag == (byte) 1;
    }

    public String bookMetadataValue(PersistentDataContainer pdc, String key) {
        NamespacedKey namespacedKey = switch (key) {
            case "book_category" -> categoryKey;
            case "book_tags" -> tagsKey;
            case "book_rating" -> ratingKey;
            case "book_comments" -> commentsKey;
            default -> null;
        };
        return namespacedKey == null ? "" : pdc.get(namespacedKey, PersistentDataType.STRING);
    }

    public void endSession(Player player) {
        UUID id = player.getUniqueId();
        awaitingSearch.remove(id); pendingMetadata.remove(id);
        PendingLecternBookEdit edit = pendingLecternEdits.remove(id);
        if (edit != null) returnItem(player, edit.stack());
        PendingReturnBookEdit returned = pendingReturnEdits.remove(id);
        if (returned != null) returnItem(player, returned.stack());
    }
    public boolean hasChatPrompt(Player p) { UUID id = p.getUniqueId(); return awaitingSearch.contains(id) || pendingMetadata.containsKey(id) || pendingLecternEdits.containsKey(id) || pendingReturnEdits.containsKey(id); }
    public boolean hasPendingLecternEdit(Player player) { return pendingLecternEdits.containsKey(player.getUniqueId()); }

    public void beginLecternBookEdit(Player player, ItemStack book, String field) {
        if (book == null || book.getType() != Material.WRITTEN_BOOK) throw new IllegalArgumentException("Place a written book in the bottom middle slot first.");
        ensureOwner(book.getItemMeta(), player);
        pendingLecternEdits.put(player.getUniqueId(), new PendingLecternBookEdit(book.clone(), field));
        player.closeInventory();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Type the new " + field + " in chat, or type cancel.");
    }

    public void toggleLecternBookVisibility(Player player, ItemStack book) {
        if (book == null || book.getType() != Material.WRITTEN_BOOK || !book.hasItemMeta()) throw new IllegalArgumentException("Place a written book in the bottom middle slot first.");
        ItemMeta meta = book.getItemMeta();
        ensureOwner(meta, player);
        boolean isPublic = !isPublicBook(book);
        meta.getPersistentDataContainer().set(publicKey, PersistentDataType.BYTE, (byte) (isPublic ? 1 : 0));
        book.setItemMeta(meta);
    }

    public boolean saveLecternBook(Player player, ItemStack book) {
        if (book == null || !book.hasItemMeta()) throw new IllegalArgumentException("Submit a written book first.");
        ensureOwner(book.getItemMeta(), player);
        return storeSubmittedBook(player, book);
    }

    private boolean storeSubmittedBook(Player player, ItemStack book) {
        if (book == null || book.getType() != Material.WRITTEN_BOOK || !book.hasItemMeta()) throw new IllegalArgumentException("Place a written book in the bottom middle slot first.");
        ItemStack saved = book.clone();
        saved.setAmount(1);
        ItemMeta meta = saved.getItemMeta();
        if (!meta.getPersistentDataContainer().has(ownerUuidKey, PersistentDataType.STRING)) tagLibraryBook(meta, player, false);
        saved.setItemMeta(meta);
        applyAverageRatingLore(saved);
        publishManuscript(player,saved);
        Location shelfLocation = plugin.isLibraryWorld(player.getWorld().getName()) ? placeInNearbyBookshelf(player, saved) : null;
        return shelfLocation != null;
    }


    private Location placeInNearbyBookshelf(Player player, ItemStack book) {
        String id=book.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"library_book_id"),PersistentDataType.STRING);
        int radius=Math.max(0,Math.min(32,plugin.getConfig().getInt("lectern-gui.save-shelf-radius",16)));
        Location center=player.getLocation();World world=player.getWorld();
        for(int y=-radius;y<=radius;y++)for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++){
            int wx=center.getBlockX()+x,wz=center.getBlockZ()+z;if(!world.isChunkLoaded(wx>>4,wz>>4))continue;
            Block block=world.getBlockAt(wx,center.getBlockY()+y,wz);if(block.getType()!=Material.CHISELED_BOOKSHELF)continue;
            populateBookshelf(block);Inventory inv=bookshelfInventory(block);
            if(inv!=null&&Arrays.stream(inv.getContents()).anyMatch(item->Objects.equals(id,bookId(item))))return block.getLocation();
        }
        return null;
    }


    public boolean hasPendingReturnEdit(Player player) { return pendingReturnEdits.containsKey(player.getUniqueId()); }

    public void beginReturnBookEdit(Player player, ItemStack book, String field) {
        if (book == null || book.getType() != Material.WRITTEN_BOOK) throw new IllegalArgumentException("Place a written book in the middle slot first.");
        pendingReturnEdits.put(player.getUniqueId(), new PendingReturnBookEdit(book.clone(), field));
        player.closeInventory();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Type the new " + field + " in chat, or type cancel.");
    }

    public boolean handleReturnBookEditChat(Player player, String input) {
        PendingReturnBookEdit pending = pendingReturnEdits.remove(player.getUniqueId());
        if (pending == null) return false;
        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> { returnItem(player, pending.stack()); player.sendMessage(ChatColor.GRAY + "Book return edit cancelled."); });
            return true;
        }
        ItemStack edited = pending.stack().clone();
        applyReturnField(player, edited, pending.field(), input);
        plugin.getGuiManager().openBookshelfReturn(player, edited);
        return true;
    }

    public boolean returnBookToLibrary(Player player, ItemStack book) {
        if (!canRead(player, book)) throw new IllegalArgumentException("Only the owner can return a private book.");
        return storeSubmittedBook(player, book);
    }

    public String averageRatingText(ItemStack book) {
        if (book == null || !book.hasItemMeta()) return "unrated";
        String ratings = book.getItemMeta().getPersistentDataContainer().get(ratingKey, PersistentDataType.STRING);
        List<Double> values = parseRatings(ratings);
        if (values.isEmpty()) return "unrated";
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return String.format(Locale.ROOT, "%.1f/5", average);
    }

    public String bookSummary(ItemStack book) {
        if (book == null || book.getType() != Material.WRITTEN_BOOK || !(book.getItemMeta() instanceof BookMeta meta)) return "";
        return ChatColor.LIGHT_PURPLE + safe(meta.getTitle()) + ChatColor.GRAY + " by " + ChatColor.WHITE + safe(meta.getAuthor()) + ChatColor.GOLD + " ★ " + averageRatingText(book);
    }

    private void applyReturnField(Player player, ItemStack book, String field, String value) {
        if (!(book.getItemMeta() instanceof BookMeta meta)) return;
        if (!canRead(player, book)) throw new IllegalArgumentException("This book is private.");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (field.equals("rating")) {
            double rating;
            try { rating = Double.parseDouble(value.trim()); } catch (NumberFormatException ex) { rating = 0.0; }
            rating = Math.max(1.0, Math.min(5.0, rating));
            String existing = safe(pdc.get(ratingKey, PersistentDataType.STRING));
            pdc.set(ratingKey, PersistentDataType.STRING, existing.isBlank() ? String.valueOf(rating) : existing + "," + rating);
        } else if (field.equals("comment")) {
            String existing = safe(pdc.get(commentsKey, PersistentDataType.STRING));
            String entry = player.getName() + ": " + value;
            pdc.set(commentsKey, PersistentDataType.STRING, existing.isBlank() ? entry : existing + "\n" + entry);
        }
        book.setItemMeta(meta);
    }

    private List<Double> parseRatings(String ratings) {
        if (ratings == null || ratings.isBlank()) return List.of();
        List<Double> values = new ArrayList<>();
        for (String part : ratings.split(",")) {
            try {
                double value = Double.parseDouble(part.trim());
                if (value >= 1.0 && value <= 5.0) values.add(value);
            } catch (NumberFormatException ignored) { }
        }
        return values;
    }

    public boolean handleLecternBookEditChat(Player player, String input) {
        PendingLecternBookEdit pending = pendingLecternEdits.remove(player.getUniqueId());
        if (pending == null) return false;
        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                returnItem(player, pending.stack());
                player.sendMessage(ChatColor.GRAY + "Book metadata edit cancelled.");
            });
            return true;
        }
        ItemStack edited = pending.stack().clone();
        applyLecternField(player, edited, pending.field(), input);
        plugin.getGuiManager().openLecternBookEditor(player, edited);
        return true;
    }

    public void returnItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    private void applyLecternField(Player player, ItemStack book, String field, String value) {
        if (!(book.getItemMeta() instanceof BookMeta meta)) return;
        ensureOwner(meta, player);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        switch (field) {
            case "title" -> meta.setTitle(value);
            case "author" -> meta.setAuthor(value);
            case "category" -> pdc.set(categoryKey, PersistentDataType.STRING, value);
            case "tags" -> pdc.set(tagsKey, PersistentDataType.STRING, value);
            case "rating" -> pdc.set(ratingKey, PersistentDataType.STRING, value);
            case "comments" -> pdc.set(commentsKey, PersistentDataType.STRING, value);
            default -> { return; }
        }
        book.setItemMeta(meta);
        applyAverageRatingLore(book);
    }

    public void beginMetadataPrompt(Player player, ItemStack stack, Location shelfLocation) {
        pendingMetadata.put(player.getUniqueId(), new PendingBookMetadata(stack.clone(), shelfLocation));
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Enter book metadata as: <category>|<rating>|<comments> (or cancel)");
    }

    public void setHeldBookMetadata(Player player, String category, String tags, String rating, String comments) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || (item.getType() != Material.WRITTEN_BOOK && item.getType() != Material.WRITABLE_BOOK) || !item.hasItemMeta()) throw new IllegalArgumentException("Hold a library book first.");
        ItemMeta meta = item.getItemMeta();
        ensureOwner(meta, player);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (category != null) pdc.set(categoryKey, PersistentDataType.STRING, category);
        if (tags != null) pdc.set(tagsKey, PersistentDataType.STRING, tags);
        if (rating != null) pdc.set(ratingKey, PersistentDataType.STRING, rating);
        if (comments != null) pdc.set(commentsKey, PersistentDataType.STRING, comments);
        item.setItemMeta(meta);
    }

    public int totalBooks() { return books.size(); }
    public long totalContributors() { return books.values().stream().map(StoredBook::contributor).distinct().count(); }
    public long playerCount(UUID uuid) { return books.values().stream().filter(b -> b.contributor().equals(uuid)).count(); }
    public Collection<StoredBook> allBooks() { return Collections.unmodifiableCollection(books.values()); }

    public Optional<ItemStack> randomBook() {
        if (books.isEmpty()) return Optional.empty();
        List<StoredBook> list = new ArrayList<>(books.values());
        return Optional.ofNullable(list.get(ThreadLocalRandom.current().nextInt(list.size())).toItemStack());
    }

    private String bookId(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin,"library_book_id"),PersistentDataType.STRING);}
    private boolean eligibleShelf(Block block){return block.getType()==Material.CHISELED_BOOKSHELF&&plugin.isLibraryWorld(block.getWorld().getName())&&(plugin.getRentalVaultManager()==null||!plugin.getRentalVaultManager().rental(block.getLocation()));}
    private String normalizedCategory(String category){return category==null||category.isBlank()?"general":category.strip().toLowerCase(Locale.ROOT);}
    private boolean categoryMatches(String category,StoredBook book){return category.isBlank()||category.equals("*")||normalizedCategory(category).equals(normalizedCategory(book.category()));}
    /** Unloaded destinations remain reserved until their chunk can be inspected without force loading. */
    private boolean hasValidAssignment(StoredBook book){
        String key=book.shelfLocation();if(key==null||key.isBlank())return false;
        Location location=deserializeLocation(key);
        if(location==null){String world=key.split(";",2)[0];return plugin.isLibraryWorld(world);}
        if(!plugin.isLibraryWorld(location.getWorld().getName()))return false;
        if(!location.getWorld().isChunkLoaded(location.getBlockX()>>4,location.getBlockZ()>>4))return true;
        Block block=location.getBlock();if(!eligibleShelf(block))return false;
        Inventory inv=bookshelfInventory(block);if(inv==null)return false;
        for(int slot=0;slot<inv.getSize();slot++){
            ItemStack item=inv.getItem(slot);if(book.id().toString().equals(bookId(item)))return true;
            if(bookId(item)==null&&legacyMatches(item,book)){
                var meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"library_book_id"),PersistentDataType.STRING,book.id().toString());item.setItemMeta(meta);inv.setItem(slot,item);return true;
            }
        }
        return false;
    }
    private boolean legacyMatches(ItemStack item,StoredBook book){
        if(item==null||item.getType()!=Material.WRITTEN_BOOK||!(item.getItemMeta() instanceof BookMeta meta))return false;
        return book.ownerUuid().equals(meta.getPersistentDataContainer().get(ownerUuidKey,PersistentDataType.STRING))&&fingerprint(meta).equals(safe(book.title())+"|"+safe(book.author())+"|"+String.join("\n",book.pages()));
    }
    public void populateBookshelf(Block block) {
        if(!eligibleShelf(block))return;
        String location=serializeLocation(block.getLocation());boolean dirty=registeredShelves.add(location);
        Inventory inv=bookshelfInventory(block);if(inv==null)return;
        Set<UUID> present=new HashSet<>();
        for(int slot=0;slot<inv.getSize();slot++){
            ItemStack item=inv.getItem(slot);String value=bookId(item);
            // Older direct submissions were copied before their catalog ID was attached.
            if(value==null&&item!=null&&item.getType()==Material.WRITTEN_BOOK&&item.getItemMeta() instanceof BookMeta meta){
                String owner=meta.getPersistentDataContainer().get(ownerUuidKey,PersistentDataType.STRING);
                StoredBook legacy=books.values().stream().filter(b->b.ownerUuid().equals(owner)&&fingerprint(meta).equals(safe(b.title())+"|"+safe(b.author())+"|"+String.join("\n",b.pages()))).sorted(Comparator.comparing(b->b.id().toString())).findFirst().orElse(null);
                if(legacy!=null){value=legacy.id().toString();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"library_book_id"),PersistentDataType.STRING,value);item.setItemMeta(meta);inv.setItem(slot,item);}
            }
            if(value!=null)try{UUID id=UUID.fromString(value);present.add(id);StoredBook stored=books.get(id);if(stored!=null&&!location.equals(stored.shelfLocation())&&!hasValidAssignment(stored)){books.put(id,stored.withLocation(location));dirty=true;}}catch(IllegalArgumentException ignored){}
        }
        String category=shelfCategory(block.getLocation());List<StoredBook> pending=new ArrayList<>();
        for(StoredBook book:books.values())if(!present.contains(book.id())){
            boolean assigned=hasValidAssignment(book);if(assigned)continue;
            if(book.shelfLocation()!=null&&!book.shelfLocation().isBlank()){book=book.withLocation("");books.put(book.id(),book);dirty=true;}
            if(categoryMatches(category,book))pending.add(book);
        }
        pending.sort(Comparator.comparing(StoredBook::insertedAt).thenComparing(b->b.id().toString()));
        int next=0;for(int slot=0;slot<Math.min(6,inv.getSize())&&next<pending.size();slot++){
            ItemStack current=inv.getItem(slot);if(current!=null&&!current.getType().isAir())continue;
            StoredBook book=pending.get(next++);ItemStack item=book.toItemStack();item.setAmount(1);var meta=item.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"library_book_id"),PersistentDataType.STRING,book.id().toString());item.setItemMeta(meta);inv.setItem(slot,item);books.put(book.id(),book.withLocation(location));dirty=true;
        }
        if(block.getBlockData() instanceof org.bukkit.block.data.type.ChiseledBookshelf data){boolean changed=false;for(int slot=0;slot<6;slot++){boolean occupied=inv.getItem(slot)!=null&&!inv.getItem(slot).getType().isAir();if(data.isSlotOccupied(slot)!=occupied){data.setSlotOccupied(slot,occupied);changed=true;}}if(changed)block.setBlockData(data,false);}
        if(dirty)saveAsync();
    }
    private void requestShelfStocking(){for(String shelf:registeredShelves)if(queuedStockingShelves.add(shelf))stockingShelves.add(shelf);}
    public int registeredShelfCount(){return registeredShelves.size();}
    public void requestShelfScan(){requestShelfStocking();for(World world:Bukkit.getWorlds())if(plugin.isLibraryWorld(world.getName()))for(org.bukkit.Chunk chunk:world.getLoadedChunks())queueChunk(chunk);}
    private void queueChunk(org.bukkit.Chunk chunk){if(!plugin.isLibraryWorld(chunk.getWorld().getName()))return;String key=chunk.getWorld().getName()+";"+chunk.getX()+";"+chunk.getZ();if(queuedChunks.add(key))scanChunks.add(key);}
    public void startShelfService(){if(shelfTask!=-1)return;plugin.getServer().getPluginManager().registerEvents(this,plugin);requestShelfScan();shelfTask=Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin,()->{
        int shelvesPerTick=Math.max(1,Math.min(64,plugin.getConfig().getInt("bookshelves.shelves-per-pass",8)));
        for(int i=0;i<shelvesPerTick&&!stockingShelves.isEmpty();i++){String key=stockingShelves.remove();queuedStockingShelves.remove(key);Location l=deserializeLocation(key);if(l!=null&&l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))populateBookshelf(l.getBlock());}
        int budget=Math.max(1,Math.min(4,plugin.getConfig().getInt("bookshelves.chunks-per-tick",1)));
        for(int i=0;i<budget&&!scanChunks.isEmpty();i++){String key=scanChunks.remove();queuedChunks.remove(key);String[] parts=key.split(";");World w=Bukkit.getWorld(parts[0]);int x=Integer.parseInt(parts[1]),z=Integer.parseInt(parts[2]);if(w!=null&&w.isChunkLoaded(x,z))for(BlockState tile:w.getChunkAt(x,z).getTileEntities())if(tile.getType()==Material.CHISELED_BOOKSHELF)populateBookshelf(tile.getBlock());}
        if(++shelfTicks%20==0){List<String> shelves=new ArrayList<>(registeredShelves);int count=Math.min(shelves.size(),Math.max(1,Math.min(64,plugin.getConfig().getInt("bookshelves.shelves-per-pass",8))));for(int i=0;i<count;i++){String key=shelves.get(Math.floorMod(shelfCursor++,shelves.size()));Location l=deserializeLocation(key);if(l==null||!l.getWorld().isChunkLoaded(l.getBlockX()>>4,l.getBlockZ()>>4))continue;Block b=l.getBlock();if(b.getType()!=Material.CHISELED_BOOKSHELF){registeredShelves.remove(key);for(var book:new ArrayList<>(books.values()))if(key.equals(book.shelfLocation()))books.put(book.id(),book.withLocation(""));saveAsync();}else populateBookshelf(b);}}
    },1,1);}
    public void stopShelfService(){if(shelfTask!=-1)Bukkit.getScheduler().cancelTask(shelfTask);shelfTask=-1;scanChunks.clear();queuedChunks.clear();stockingShelves.clear();queuedStockingShelves.clear();}
    @org.bukkit.event.EventHandler public void chunk(org.bukkit.event.world.ChunkLoadEvent e){queueChunk(e.getChunk());}
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true) public void placed(org.bukkit.event.block.BlockPlaceEvent e){if(e.getBlockPlaced().getType()==Material.CHISELED_BOOKSHELF&&plugin.isLibraryWorld(e.getBlockPlaced().getWorld().getName()))Bukkit.getScheduler().runTask(plugin,()->populateBookshelf(e.getBlockPlaced()));}

    public String bookshelfSlotSummary(Block block) {
        if (block.getType() != Material.CHISELED_BOOKSHELF) return "";
        Inventory inventory = bookshelfInventory(block);
        if (inventory == null) return "";
        List<String> slots = new ArrayList<>();
        for (int slot = 0; slot < 6; slot++) {
            ItemStack item = slot < inventory.getSize() ? inventory.getItem(slot) : null;
            slots.add(ChatColor.DARK_PURPLE + String.valueOf(slot + 1) + ChatColor.GRAY + ": " + shortBookSummary(item));
        }
        return String.join(ChatColor.DARK_GRAY + " | ", slots);
    }

    private String shortBookSummary(ItemStack book) {
        if (book == null || book.getType().isAir()) return ChatColor.DARK_GRAY + "Empty";
        if (book.getType() != Material.WRITTEN_BOOK || !(book.getItemMeta() instanceof BookMeta meta)) return ChatColor.RED + "Not a book";
        return ChatColor.LIGHT_PURPLE + truncate(safe(meta.getTitle()), 14) + ChatColor.GRAY + "/" + ChatColor.WHITE + truncate(safe(meta.getAuthor()), 10) + ChatColor.GOLD + " ★" + averageRatingText(book);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) return "?";
        return value.length() <= maxLength ? value : value.substring(0, Math.max(1, maxLength - 1)) + "…";
    }

    public Optional<ItemStack> peekBookshelfBook(Block block) {
        if (block.getType() != Material.CHISELED_BOOKSHELF) return Optional.empty();
        Inventory inventory = bookshelfInventory(block);
        if (inventory == null) return Optional.empty();
        for (int slot = 0; slot < Math.min(inventory.getSize(), 6); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == Material.WRITTEN_BOOK) return Optional.of(item);
        }
        return Optional.empty();
    }

    private void applyAverageRatingLore(ItemStack book) {
        if (book == null || !book.hasItemMeta()) return;
        ItemMeta meta = book.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> ChatColor.stripColor(line).startsWith("Average rating:"));
        lore.add(ChatColor.GOLD + "Average rating: " + averageRatingText(book));
        meta.setLore(lore);
        book.setItemMeta(meta);
    }

    public void beginSearchPrompt(Player player) {
        awaitingSearch.add(player.getUniqueId());
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Type a book title search in chat. Type cancel to stop.");
    }

    public boolean handleSearchChat(Player player, String query) {
        if (!awaitingSearch.remove(player.getUniqueId())) return false;
        if (query.equalsIgnoreCase("cancel")) { player.sendMessage(ChatColor.GRAY + "Book search cancelled."); return true; }
        Bukkit.getScheduler().runTask(plugin, () -> search(player, query));
        return true;
    }

    public void search(Player player, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) throw new IllegalArgumentException("Enter a search query.");
        List<StoredBook> results = books.values().stream()
            .filter(b -> b.isPublic() || b.ownerUuid().equals(player.getUniqueId().toString()))
            .filter(b -> (b.title()+" "+b.author()+" "+b.category()+" "+b.tags()).toLowerCase(Locale.ROOT).contains(needle))
            .sorted(Comparator.comparing(StoredBook::title).thenComparing(b -> b.id().toString())).toList();
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Found " + results.size() + " book(s). Showing up to 10:");
        results.stream().limit(10).forEach(b -> player.sendMessage(net.kyori.adventure.text.Component.text(b.title()+" by "+b.author()+" ["+b.category()+"] — click to read").clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/il read "+b.id()))));
        results.stream().map(b -> deserializeLocation(b.shelfLocation())).filter(Objects::nonNull)
            .filter(l -> player.getWorld().equals(l.getWorld())).findFirst().ifPresent(l -> {try{plugin.getNavigationManager().start(player,"search result bookshelf",l);}catch(IllegalArgumentException ex){player.sendMessage(ex.getMessage());}});
    }

    public boolean handleMetadataChat(Player player, String input) {
        if (handleReturnBookEditChat(player, input)) return true;
        if (handleLecternBookEditChat(player, input)) return true;
        PendingBookMetadata pending = pendingMetadata.remove(player.getUniqueId());
        if (pending == null) return false;
        if (input.equalsIgnoreCase("cancel")) { player.sendMessage(ChatColor.GRAY + "Book metadata input cancelled."); return true; }
        String[] parts = input.split("\\|", 3);
        String category = parts.length > 0 ? parts[0] : "general";
        String rating = parts.length > 1 ? parts[1] : "unrated";
        String comments = parts.length > 2 ? parts[2] : "";
        if (pending.stack().getItemMeta() instanceof BookMeta meta) {
            meta.getPersistentDataContainer().set(categoryKey, PersistentDataType.STRING, category);
            meta.getPersistentDataContainer().set(ratingKey, PersistentDataType.STRING, rating);
            meta.getPersistentDataContainer().set(commentsKey, PersistentDataType.STRING, comments);
            pending.stack().setItemMeta(meta);
        }
        recordBook(player, pending.stack(), pending.shelfLocation());
        player.sendMessage(ChatColor.GREEN + "Book saved with metadata.");
        return true;
    }

    public void setShelfCategory(Location shelf, String category) { shelfCategories.put(serializeLocation(shelf), category); saveAsync(); }
    public String shelfCategory(Location shelf) { return shelfCategories.getOrDefault(serializeLocation(shelf), ""); }
    private String fingerprint(BookMeta meta) { return safe(meta.getTitle()) + "|" + safe(meta.getAuthor()) + "|" + String.join("\n", meta.getPages()); }
    private record PendingBookMetadata(ItemStack stack, Location shelfLocation) {}
    private record PendingLecternBookEdit(ItemStack stack, String field) {}
    private record PendingReturnBookEdit(ItemStack stack, String field) {}

    public boolean canRead(Player player, ItemStack item) {
        if (item == null || item.getType() != Material.WRITTEN_BOOK || !item.hasItemMeta()) return true;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Byte publicFlag = pdc.get(publicKey, PersistentDataType.BYTE);
        String owner = pdc.get(ownerUuidKey, PersistentDataType.STRING);
        if (owner == null || publicFlag == null || publicFlag == (byte) 1) return true;
        return owner.equals(player.getUniqueId().toString());
    }

    public void setHeldBookPublic(Player player, boolean isPublic) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || (item.getType() != Material.WRITTEN_BOOK && item.getType() != Material.WRITABLE_BOOK) || !item.hasItemMeta()) throw new IllegalArgumentException("Hold a library book first.");
        ItemMeta meta = item.getItemMeta();
        ensureOwner(meta, player);
        meta.getPersistentDataContainer().set(publicKey, PersistentDataType.BYTE, (byte) (isPublic ? 1 : 0));
        item.setItemMeta(meta);
    }

    public void convertHeldWrittenBookToEditable(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() != Material.WRITTEN_BOOK || !(held.getItemMeta() instanceof BookMeta oldMeta)) throw new IllegalArgumentException("Hold a written library book first.");
        BookOwnership ownership = ownership(oldMeta, player);
        if (!ownership.ownerUuid().equals(player.getUniqueId().toString()) && !ownership.ownerName().equalsIgnoreCase(player.getName())) throw new IllegalArgumentException("Only the linked owner can edit this book.");
        ItemStack editable = new ItemStack(Material.WRITABLE_BOOK, held.getAmount());
        BookMeta newMeta = (BookMeta) editable.getItemMeta();
        newMeta.setPages(oldMeta.getPages());
        tagLibraryBook(newMeta, player, ownership.isPublic());
        editable.setItemMeta(newMeta);
        player.getInventory().setItemInMainHand(editable);
    }

    private void markBookLocation(ItemStack item, Location location) {
        for (StoredBook book : books.values()) {
            if (book.toItemStack().isSimilar(item)) {
                books.put(book.id(), book.withLocation(serializeLocation(location)));
                saveAsync();
                return;
            }
        }
    }

    private void tagLibraryBook(ItemMeta meta, Player player, boolean isPublic) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(ownerUuidKey, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(ownerNameKey, PersistentDataType.STRING, player.getName());
        pdc.set(publicKey, PersistentDataType.BYTE, (byte) (isPublic ? 1 : 0));
    }

    private void ensureOwner(ItemMeta meta, Player player) {
        String owner = meta.getPersistentDataContainer().get(ownerUuidKey, PersistentDataType.STRING);
        if (owner != null && !owner.equals(player.getUniqueId().toString())) throw new IllegalArgumentException("Only the book owner can change its metadata or visibility.");
        if (!meta.getPersistentDataContainer().has(ownerUuidKey, PersistentDataType.STRING)) tagLibraryBook(meta, player, false);
    }

    private BookOwnership ownership(ItemMeta meta, Player fallback) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String ownerUuid = pdc.get(ownerUuidKey, PersistentDataType.STRING);
        String ownerName = pdc.get(ownerNameKey, PersistentDataType.STRING);
        Byte publicFlag = pdc.get(publicKey, PersistentDataType.BYTE);
        return new BookOwnership(ownerUuid == null ? fallback.getUniqueId().toString() : ownerUuid, ownerName == null ? fallback.getName() : ownerName, publicFlag != null && publicFlag == (byte) 1);
    }

    private Inventory bookshelfInventory(Block block) {
        try {
            BlockState state = block.getState();
            if (state instanceof InventoryHolder holder) return holder.getInventory();
            Method method = state.getClass().getMethod("getInventory");
            Object result = method.invoke(state);
            return result instanceof Inventory inv ? inv : null;
        } catch (ReflectiveOperationException ignored) { return null; }
    }

    private String serializeLocation(Location location) { return location == null || location.getWorld() == null ? "" : location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ(); }
    public Location bookLocation(StoredBook book){return deserializeLocation(book.shelfLocation());}
    private Location deserializeLocation(String value) {
        try { String[] p = value.split(";"); World w = Bukkit.getWorld(p[0]); return w == null ? null : new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])); }
        catch (Exception ex) { return null; }
    }
    private String safe(String value) { return value == null ? "" : value; }
    private record BookOwnership(String ownerUuid, String ownerName, boolean isPublic) {}

    public record StoredBook(UUID id, UUID contributor, String contributorName, String ownerUuid, String ownerName, boolean isPublic, String title, String author, List<String> pages, Map<String, Object> item, String insertedAt, String shelfLocation, String category, String tags, String rating, String comments) {
        @SuppressWarnings("unchecked") public ItemStack toItemStack() { return ItemStack.deserialize(item); }
        public StoredBook withLocation(String location) { return new StoredBook(id, contributor, contributorName, ownerUuid, ownerName, isPublic, title, author, pages, item, insertedAt, location, category, tags, rating, comments); }
        public String displayContributor() {
            OfflinePlayer p = Bukkit.getOfflinePlayer(contributor);
            return p.getName() != null ? p.getName() : contributorName;
        }
        public void write(ConfigurationSection s) {
            s.set("contributor", contributor.toString()); s.set("contributor-name", contributorName); s.set("owner-uuid", ownerUuid); s.set("owner-name", ownerName); s.set("public", isPublic); s.set("title", title); s.set("author", author); s.set("pages", pages); s.set("item", item); s.set("inserted-at", insertedAt); s.set("shelf-location", shelfLocation); s.set("category", category); s.set("tags", tags); s.set("rating", rating); s.set("comments", comments);
        }
        public static StoredBook read(UUID id, ConfigurationSection s) {
            String contributor = s.getString("contributor");
            return new StoredBook(id, UUID.fromString(contributor), s.getString("contributor-name", "Unknown"), s.getString("owner-uuid", contributor), s.getString("owner-name", s.getString("contributor-name", "Unknown")), s.getBoolean("public", false), s.getString("title", ""), s.getString("author", ""), s.getStringList("pages"), s.getConfigurationSection("item").getValues(false), s.getString("inserted-at", ""), s.getString("shelf-location", ""), s.getString("category", ""), s.getString("tags", ""), s.getString("rating", ""), s.getString("comments", ""));
        }
    }
}
