/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package client.processor;

import client.Character;
import client.Client;
import client.inventory.InventoryType;
import client.inventory.manipulator.InventoryManipulator;
import constants.inventory.ItemConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripting.AbstractPlayerInteraction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InventoryExchangeProcessor {
    private static final Logger log = LoggerFactory.getLogger(InventoryExchangeProcessor.class);

    public static boolean exchange(AbstractPlayerInteraction api,
                                   int[] removeIds, int[] removeQtys,
                                   int[] addIds, int[] addQtys) {
        Client c = api.getClient();
        Character chr = api.getPlayer();

        Map<Integer, Integer> removes = aggregate(removeIds, removeQtys);
        Map<Integer, Integer> adds = aggregate(addIds, addQtys);

        for (Map.Entry<Integer, Integer> e : removes.entrySet()) {
            if (!api.haveItem(e.getKey(), e.getValue())) {
                return false;
            }
        }

        if (!api.canHoldAllAfterRemoving(new ArrayList<>(adds.keySet()), new ArrayList<>(adds.values()),
                new ArrayList<>(removes.keySet()), new ArrayList<>(removes.values()))) {
            return false;
        }

        chr.lockTransfer();
        try {
            return commit(c, removes, adds);
        } finally {
            chr.unlockTransfer();
        }
    }

    private static boolean commit(Client c, Map<Integer, Integer> removes, Map<Integer, Integer> adds) {
        List<JournalEntry> journal = new ArrayList<>();
        try {
            for (Map.Entry<Integer, Integer> e : removes.entrySet()) {
                InventoryType type = ItemConstants.getInventoryType(e.getKey());
                InventoryManipulator.removeById(c, type, e.getKey(), e.getValue(), false, false);
                journal.add(new JournalEntry(e.getKey(), e.getValue(), true));
            }
            for (Map.Entry<Integer, Integer> e : adds.entrySet()) {
                if (!InventoryManipulator.addById(c, e.getKey(), e.getValue().shortValue())) {
                    rollback(c, journal);
                    return false;
                }
                journal.add(new JournalEntry(e.getKey(), e.getValue(), false));
            }
            return true;
        } catch (RuntimeException ex) {
            rollback(c, journal);
            log.warn("Rolled back failed inventory exchange (removes={}, adds={})", removes, adds, ex);
            return false;
        }
    }

    private static void rollback(Client c, List<JournalEntry> journal) {
        for (int i = journal.size() - 1; i >= 0; i--) {
            JournalEntry entry = journal.get(i);
            try {
                if (entry.removed) {
                    InventoryManipulator.addById(c, entry.itemId, (short) entry.qty);
                } else {
                    InventoryType type = ItemConstants.getInventoryType(entry.itemId);
                    InventoryManipulator.removeById(c, type, entry.itemId, entry.qty, false, false);
                }
            } catch (RuntimeException ex) {
                log.warn("Rollback step failed for item {} qty {}", entry.itemId, entry.qty, ex);
            }
        }
    }

    private static Map<Integer, Integer> aggregate(int[] ids, int[] qtys) {
        LinkedHashMap<Integer, Integer> result = new LinkedHashMap<>();
        if (ids == null) {
            return result;
        }
        for (int i = 0; i < ids.length; i++) {
            int qty = (qtys == null || i >= qtys.length) ? 0 : qtys[i];
            if (qty <= 0) {
                continue;
            }
            result.merge(ids[i], qty, Integer::sum);
        }
        return result;
    }

    private static class JournalEntry {
        final int itemId;
        final int qty;
        final boolean removed;

        JournalEntry(int itemId, int qty, boolean removed) {
            this.itemId = itemId;
            this.qty = qty;
            this.removed = removed;
        }
    }
}
