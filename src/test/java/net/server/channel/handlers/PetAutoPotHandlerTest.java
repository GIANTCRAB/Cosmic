package net.server.channel.handlers;

import client.Character;
import client.Client;
import client.processor.action.PetAutopotProcessor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import server.ItemInformationProvider;
import server.StatEffect;
import testutil.Packets;
import testutil.ItemInformationProviderMocks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PetAutoPotHandlerTest {
    @ParameterizedTest
    @ValueSource(ints = {20_000, 50_000})
    void learnsThresholdFromCombatMaximumRatherThanTrueMaximum(int trueMaximum) {
        Client client = mock(Client.class);
        Character chr = mock(Character.class);
        ItemInformationProvider items = ItemInformationProviderMocks.mockProvider();
        StatEffect potion = mock(StatEffect.class);
        when(client.getPlayer()).thenReturn(chr);
        when(chr.getHp()).thenReturn(15_000);
        when(chr.getMp()).thenReturn(15_000);
        when(chr.getCurrentMaxHp()).thenReturn(30_000);
        when(chr.getCurrentMaxMp()).thenReturn(30_000);
        when(chr.getMaxHp()).thenReturn(trueMaximum);
        when(chr.getMaxMp()).thenReturn(trueMaximum);
        when(potion.getHp()).thenReturn((short) 100);
        when(potion.getMp()).thenReturn((short) 100);
        when(items.getItemEffect(2000000)).thenReturn(potion);

        try (var itemProvider = mockStatic(ItemInformationProvider.class);
             var autopot = mockStatic(PetAutopotProcessor.class)) {
            itemProvider.when(ItemInformationProvider::getInstance).thenReturn(items);

            new PetAutoPotHandler().handlePacket(Packets.buildInPacket(p -> {
                p.writeByte(0);
                p.writeLong(0);
                p.writeInt(0);
                p.writeShort(1);
                p.writeInt(2000000);
            }), client);

            verify(chr).setAutopotHpAlert(0.55f);
            verify(chr).setAutopotMpAlert(0.55f);
            autopot.verify(() -> PetAutopotProcessor.runAutopotAction(client, (short) 1, 2000000));
        }
    }
}
