package m.co.rh.id.a_personal_stuff.base.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import m.co.rh.id.a_personal_stuff.base.entity.Item;
import m.co.rh.id.a_personal_stuff.base.entity.ItemImage;
import m.co.rh.id.a_personal_stuff.base.entity.ItemTag;
import m.co.rh.id.a_personal_stuff.base.model.ItemState;

public class ItemDaoTest {

    // assembleItemStates tests

    @Test
    public void groupsImagesAndTagsOntoTheirItemsPreservingInputOrder() {
        List<Item> items = new ArrayList<>(Arrays.asList(
                item(1L, "Beans"), item(2L, "Rice"), item(3L, "Noodles")));
        // deliberately unsorted image input to prove grouping is by itemId,
        // not by list position
        List<ItemImage> images = new ArrayList<>(Arrays.asList(
                image(11L, 3L, "noodles.jpg"),
                image(10L, 1L, "beans_a.jpg"),
                image(12L, 1L, "beans_b.jpg")));
        List<ItemTag> tags = new ArrayList<>(Collections.singletonList(
                tag(21L, 2L, "pantry")));

        List<ItemState> itemStates = ItemDao.assembleItemStates(items, images, tags);

        assertEquals(3, itemStates.size());
        // items-list order is preserved
        assertEquals("Beans", itemStates.get(0).getItemName());
        assertEquals("Rice", itemStates.get(1).getItemName());
        assertEquals("Noodles", itemStates.get(2).getItemName());

        List<ItemImage> beansImages = itemStates.get(0).getItemImages();
        assertEquals(2, beansImages.size());
        assertEquals("beans_a.jpg", beansImages.get(0).fileName);
        assertEquals("beans_b.jpg", beansImages.get(1).fileName);
        assertTrue(itemStates.get(0).getItemTags().isEmpty());

        List<ItemImage> riceImages = itemStates.get(1).getItemImages();
        assertTrue(riceImages.isEmpty());
        assertEquals(1, itemStates.get(1).getItemTags().size());
        assertTrue(itemStates.get(1).getItemTags().contains(tag(21L, 2L, "pantry")));

        List<ItemImage> noodlesImages = itemStates.get(2).getItemImages();
        assertEquals(1, noodlesImages.size());
        assertEquals("noodles.jpg", noodlesImages.get(0).fileName);
        assertTrue(itemStates.get(2).getItemTags().isEmpty());
    }

    @Test
    public void itemsWithoutImagesOrTagsKeepEmptyChildLists() {
        List<Item> items = new ArrayList<>(Collections.singletonList(item(1L, "Beans")));

        List<ItemState> itemStates = ItemDao.assembleItemStates(items,
                new ArrayList<>(), new ArrayList<>());

        assertEquals(1, itemStates.size());
        assertEquals("Beans", itemStates.get(0).getItemName());
        assertTrue(itemStates.get(0).getItemImages().isEmpty());
        assertTrue(itemStates.get(0).getItemTags().isEmpty());
    }

    @Test
    public void emptyItemsYieldEmptyList() {
        List<ItemState> itemStates = ItemDao.assembleItemStates(
                new ArrayList<>(),
                new ArrayList<>(Collections.singletonList(image(10L, 1L, "orphan.jpg"))),
                new ArrayList<>());

        assertTrue(itemStates.isEmpty());
    }

    private static Item item(long id, String name) {
        Item item = new Item();
        item.id = id;
        item.name = name;
        item.amount = 1;
        item.createdDateTime = new Date(0L);
        item.updatedDateTime = new Date(0L);
        return item;
    }

    private static ItemImage image(long id, long itemId, String fileName) {
        ItemImage itemImage = new ItemImage();
        itemImage.id = id;
        itemImage.itemId = itemId;
        itemImage.fileName = fileName;
        itemImage.createdDateTime = new Date(0L);
        return itemImage;
    }

    private static ItemTag tag(long id, long itemId, String tag) {
        ItemTag itemTag = new ItemTag();
        itemTag.id = id;
        itemTag.itemId = itemId;
        itemTag.tag = tag;
        itemTag.createdDateTime = new Date(0L);
        return itemTag;
    }

    // chunkIds tests

    @Test
    public void chunkIdsWithEmptyListYieldsNoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(0));
        assertEquals(0, chunks.size());
    }

    @Test
    public void chunkIdsWithSingleIdYieldsOneChunk() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(1));
        assertChunks(chunks, 1, 1);
    }

    @Test
    public void chunkIdsJustUnderLimitYieldsOneChunk() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(499));
        assertChunks(chunks, 1, 499);
    }

    @Test
    public void chunkIdsAtExactLimitYieldsOneChunk() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(500));
        assertChunks(chunks, 1, 500);
    }

    @Test
    public void chunkIdsJustOverLimitYieldsTwoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(501));
        assertChunks(chunks, 2, 501);
    }

    @Test
    public void chunkIdsJustUnderTwoLimitsYieldsTwoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(999));
        assertChunks(chunks, 2, 999);
    }

    @Test
    public void chunkIdsAtTwoExactLimitsYieldsTwoChunks() {
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(1000));
        assertChunks(chunks, 2, 1000);
    }

    @Test
    public void chunkIdsAtMaxPageLimitYieldsFourChunks() {
        // 1600 is the largest page size reachable via PagedItemCmd#loadNextPage
        List<List<Long>> chunks = ItemDao.chunkIds(buildIds(1600));
        assertChunks(chunks, 4, 1600);
    }

    /**
     * Verifies the generic chunking contract for an input of ids 1..totalIds:
     * the explicitly expected number of chunks, every chunk except the last
     * holding exactly the 500-id bind cap, the last chunk holding 1..500 ids,
     * and the concatenation reproducing the original list in order without
     * losses or duplicates.
     */
    private static void assertChunks(List<List<Long>> chunks, int expectedChunkCount, int totalIds) {
        assertEquals(expectedChunkCount, chunks.size());
        if (expectedChunkCount == 0) {
            return;
        }
        List<Long> concatenated = new ArrayList<>();
        for (int c = 0; c < chunks.size(); c++) {
            List<Long> chunk = chunks.get(c);
            if (c < chunks.size() - 1) {
                assertEquals("every chunk except the last must hold the full 500-id cap",
                        500, chunk.size());
            } else {
                assertTrue("last chunk must hold 1..500 ids, was " + chunk.size(),
                        chunk.size() >= 1 && chunk.size() <= 500);
            }
            concatenated.addAll(chunk);
        }
        assertEquals(totalIds, concatenated.size());
        for (int i = 0; i < totalIds; i++) {
            assertEquals("ids must survive in order, without losses or duplicates",
                    Long.valueOf(i + 1), concatenated.get(i));
        }
    }

    private static List<Long> buildIds(int count) {
        List<Long> ids = new ArrayList<>(count);
        for (long id = 1; id <= count; id++) {
            ids.add(id);
        }
        return ids;
    }
}
