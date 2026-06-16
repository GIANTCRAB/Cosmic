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
import config.YamlConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HardcoreProcessor {
    private static final Logger log = LoggerFactory.getLogger(HardcoreProcessor.class);

    public static boolean isHardcoreModeEnabled() {
        return YamlConfig.config.server.USE_HARDCORE_MODE;
    }

    public static boolean processPermanentDeathIfEnabled(Character chr) {
        if (!isHardcoreModeEnabled()) {
            return false;
        }

        executePermanentDeath(chr);
        return true;
    }

    private static void executePermanentDeath(Character chr) {
        chr.flushStorage();

        final var deleted = chr.deletePermanently();
        if (!deleted) {
            log.warn("Failed to delete hardcore-dead character '{}' (cid: {}) from DB", chr.getName(), chr.getId());
        } else {
            log.info("Character '{}' (cid: {}) has permanently died in hardcore mode", chr.getName(), chr.getId());
        }

        chr.markPendingHardcoreDeletion();

        final var client = chr.getClient();
        if (client != null) {
            client.forceDisconnect();
        }
    }
}
