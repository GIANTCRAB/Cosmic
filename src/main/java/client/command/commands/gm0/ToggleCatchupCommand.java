/*
 * Copyright (C) 2026 Huiren Woo
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package client.command.commands.gm0;

import client.Character;
import client.Client;
import client.MetaProgressionToggle;
import client.command.Command;

public class ToggleCatchupCommand extends Command {
    {
        setDescription("Toggle the account catchup (meta progression) stat buffs on/off.");
    }

    @Override
    public void execute(Client c, String[] params) {
        if (c.tryacquireClient()) {
            try {
                Character player = c.getPlayer();
                boolean enabled = MetaProgressionToggle.toggle(player.getId());
                player.equipChanged();
                player.showHint("Meta Progression catchup buffs " + (enabled ? "enabled." : "disabled."), 300);
            } finally {
                c.releaseClient();
            }
        }
    }
}
